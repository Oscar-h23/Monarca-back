package com.Monarca.Backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/** Ejecuciones académicas aisladas: nunca altera pedidos ni modelos de talla. */
@RestController
@RequestMapping("/api/admin/ml/modulos")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminMlModulosController {
    private static final Set<String> MODULOS=Set.of("CLUSTERING","DEMANDA","OUTFITS");
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    @Value("${monarca.ml.url:}") private String mlUrl;
    @Value("${monarca.ml.internal-token:}") private String token;

    public record Parametros(Double presupuestoSoles) {}
    public AdminMlModulosController(JdbcTemplate db, ObjectMapper json) {
        this.db=db;this.json=json;
    }

    @GetMapping("/resultados")
    @Transactional(readOnly=true)
    public List<Map<String,Object>> resultados() {
        return db.query("""
            SELECT DISTINCT ON (modulo) id_ejecucion,modulo,fuente,registros,
                   resultado::text,fecha_ejecucion
              FROM monarca.ml_ejecuciones_academicas
             ORDER BY modulo,fecha_ejecucion DESC,id_ejecucion DESC
            """,(rs,i)->{
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("idEjecucion",rs.getLong("id_ejecucion"));
            row.put("modulo",rs.getString("modulo"));
            row.put("fuente",rs.getString("fuente"));
            row.put("registros",rs.getInt("registros"));
            row.put("fecha",rs.getTimestamp("fecha_ejecucion").toInstant().toString());
            try {row.put("resultado",json.readValue(rs.getString("resultado"),Map.class));}
            catch(Exception ex) {row.put("resultado",Map.of());}
            return row;
        });
    }

    @PostMapping("/{modulo}/ejecutar")
    public Map<String,Object> ejecutar(@PathVariable String modulo,
                                       @RequestBody(required=false) Parametros parametros) {
        String m=modulo.toUpperCase(Locale.ROOT);
        if(!MODULOS.contains(m))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Módulo no compatible.");
        String fuente=db.queryForObject(
          "SELECT fuente FROM monarca.ml_fuentes_datos WHERE modulo=?",String.class,m);
        if(fuente==null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Configura la fuente.");
        if(mlUrl==null||mlUrl.isBlank()||token==null||token.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Servicio ML no configurado.");
        List<JsonNode> registros;
        try {
            if("PRUEBA".equals(fuente)) {
                registros=db.query("""
                    SELECT datos::text FROM monarca.ml_demo_registros
                     WHERE modulo=? ORDER BY numero
                    """,(rs,i)->json.readTree(rs.getString(1)),m);
            } else if("OUTFITS".equals(m)) {
                // Real: productos y precios del catálogo; valoración estética neutral (0).
                registros=db.query("""
                    SELECT jsonb_build_object(
                        'id_variante_superior',v1.id_variante,'superior',p1.nombre,
                        'precio_superior_soles',v1.precio,
                        'id_variante_inferior',v2.id_variante,'inferior',p2.nombre,
                        'precio_inferior_soles',v2.precio,
                        'puntuacion_estilo_simulada_0_100',0,
                        'presupuesto_soles',200)::text
                    FROM monarca.variantes_producto v1
                    JOIN monarca.productos p1 ON p1.id_producto=v1.id_producto
                    JOIN monarca.categorias c1 ON c1.id_categoria=p1.id_categoria
                    CROSS JOIN monarca.variantes_producto v2
                    JOIN monarca.productos p2 ON p2.id_producto=v2.id_producto
                    JOIN monarca.categorias c2 ON c2.id_categoria=p2.id_categoria
                    WHERE p1.activo=true AND p2.activo=true
                      AND v1.activo=true AND v2.activo=true
                      AND v1.stock>0 AND v2.stock>0
                      AND c1.nombre='Tops' AND c2.nombre='Bottoms'
                    ORDER BY v1.id_variante,v2.id_variante LIMIT 300
                    """,(rs,i)->json.readTree(rs.getString(1)));
            } else {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                  "No hay datos reales suficientes con las variables requeridas para este módulo. Usa Prueba.");
            }
            if(registros.isEmpty())
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Sin registros disponibles.");
            Map<String,Object> body=new LinkedHashMap<>();
            body.put("fuente",fuente);
            body.put("registros",registros);
            if(parametros!=null&&parametros.presupuestoSoles()!=null)
                body.put("presupuesto_soles",parametros.presupuestoSoles());
            HttpRequest request=HttpRequest.newBuilder(
                    URI.create(mlUrl.replaceAll("/+$","")+"/internal/v1/modulos/"+m+"/ejecutar"))
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type","application/json")
                .header("X-ML-Token",token)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==422)
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                  "No se pudo evaluar: conjunto insuficiente o incompatible con el algoritmo.");
            if(response.statusCode()!=200)
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Servicio ML rechazó la ejecución.");
            JsonNode out=json.readTree(response.body());
            if(!m.equals(out.path("modulo").asText())||!fuente.equals(out.path("fuente").asText()))
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Respuesta ML inválida.");
            Long id=db.queryForObject("""
                INSERT INTO monarca.ml_ejecuciones_academicas(modulo,fuente,registros,resultado)
                VALUES(?,?,?,CAST(? AS jsonb)) RETURNING id_ejecucion
                """,Long.class,m,fuente,registros.size(),out.toString());
            return Map.of("idEjecucion",id,"modulo",m,"fuente",fuente,
                          "registros",registros.size(),"resultado",json.convertValue(out,Map.class));
        } catch(ResponseStatusException e) {throw e;}
        catch(Exception ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo ejecutar el módulo; verifica la conexión con Monarca-ML.");
        }
    }
}
