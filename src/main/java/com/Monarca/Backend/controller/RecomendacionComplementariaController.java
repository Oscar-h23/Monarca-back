package com.Monarca.Backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/** Recomienda complementos por co-compra; jamás inserta compras falsas en pedidos. */
@RestController
@RequestMapping("/api/productos")
public class RecomendacionComplementariaController {
    private final JdbcTemplate db;
    private final ObjectMapper mapper;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    @Value("${monarca.ml.url:}") private String mlUrl;
    @Value("${monarca.ml.internal-token:}") private String mlToken;

    public RecomendacionComplementariaController(JdbcTemplate db, ObjectMapper mapper) {
        this.db=db;this.mapper=mapper;
    }

    @GetMapping("/{producto}/complementos")
    public ResponseEntity<Map<String,Object>> obtener(@PathVariable long producto) {
        if(producto<1)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Producto inválido");
        List<Long> existe=db.query("""
          SELECT id_producto FROM monarca.productos
           WHERE id_producto=? AND activo=true
          """,(rs,i)->rs.getLong(1),producto);
        if(existe.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Producto no disponible");
        String fuente=db.queryForObject(
            "SELECT fuente FROM monarca.ml_fuentes_datos WHERE modulo='COCOMPRA'",String.class);
        List<List<Long>> cestas;
        if("PRUEBA".equals(fuente)) {
            cestas=db.query("""
                SELECT array_agg(id_producto ORDER BY id_producto) AS productos
                FROM monarca.ml_cocompra_cestas_demo
                GROUP BY id_cesta ORDER BY id_cesta
                """,(rs,i)->{
                Long[] ids=(Long[])rs.getArray(1).getArray();
                return Arrays.asList(ids);
            });
        } else {
            // Únicamente pedidos FINALIZADOS. No exponer IDs ni compradores.
            cestas=db.query("""
                SELECT array_agg(DISTINCT v.id_producto) AS productos
                FROM monarca.detalles_pedido d
                JOIN monarca.pedidos p ON p.id_pedido=d.id_pedido
                JOIN monarca.variantes_producto v ON v.id_variante=d.id_variante
                WHERE p.estado='FINALIZADO'
                GROUP BY p.id_pedido ORDER BY p.id_pedido
                LIMIT 5000
                """,(rs,i)->{
                Long[] ids=(Long[])rs.getArray(1).getArray();
                return Arrays.asList(ids);
            });
        }
        if(cestas.isEmpty()) return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
            "fuente",fuente,"cestas",0,"recomendaciones",List.of(),
            "mensaje","Aún no hay compras suficientes para recomendar complementos"));
        if(mlUrl==null||mlUrl.isBlank()||mlToken==null||mlToken.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Servicio ML no configurado");
        try {
            String body=mapper.writeValueAsString(Map.of(
                "fuente",fuente,"producto",producto,"cestas",cestas));
            HttpRequest request=HttpRequest.newBuilder(
                URI.create(mlUrl.replaceAll("/+$","")+"/internal/v1/cocompra/recomendar"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type","application/json")
                .header("X-ML-Token",mlToken)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200)
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"No fue posible calcular los complementos");
            JsonNode result=mapper.readTree(response.body());
            if(!fuente.equals(result.path("fuente").asText()))
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Fuente ML inesperada");
            List<Map<String,Object>> disponibles=new ArrayList<>();
            for(JsonNode item:result.path("recomendaciones")) {
                long id=item.path("idProducto").asLong(-1);
                if(id<=0 || id==producto)continue;
                List<Map<String,Object>> productoDisponible=db.query("""
                  SELECT p.id_producto,p.nombre,MIN(v.precio) AS precio
                    FROM monarca.productos p
                    JOIN monarca.variantes_producto v ON v.id_producto=p.id_producto
                   WHERE p.id_producto=? AND p.activo=true AND v.activo=true AND v.stock>0
                   GROUP BY p.id_producto,p.nombre
                  """,(rs,i)->Map.<String,Object>of(
                    "idProducto",rs.getLong("id_producto"),
                    "nombre",rs.getString("nombre"),
                    "precioDesde",rs.getBigDecimal("precio")),id);
                if(productoDisponible.isEmpty())continue;
                Map<String,Object> salida=new LinkedHashMap<>(productoDisponible.get(0));
                salida.put("confianza",item.path("confianza").asDouble());
                salida.put("soporte",item.path("soporte").asDouble());
                salida.put("lift",item.path("lift").asDouble());
                disponibles.add(salida);
            }
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
                "fuente",fuente,"cestas",result.path("cestas").asInt(),
                "recomendaciones",disponibles,
                "mensaje",result.path("mensaje").asText()));
        } catch(ResponseStatusException ex) {throw ex;}
        catch(Exception ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "La recomendación de complementos no está disponible");
        }
    }
}
