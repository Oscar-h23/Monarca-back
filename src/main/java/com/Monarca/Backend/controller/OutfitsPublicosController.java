package com.Monarca.Backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generador de conjuntos para compradores: solo variantes REALES, activas y con stock.
 * No usa las 100 combinaciones simuladas del laboratorio administrativo.
 * El algoritmo del microservicio ML optimiza el presupuesto (no atributos estéticos).
 */
@RestController
@RequestMapping("/api/outfits")
public class OutfitsPublicosController {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    @Value("${monarca.ml.url:}") private String mlUrl;
    @Value("${monarca.ml.internal-token:}") private String mlToken;

    public OutfitsPublicosController(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    @GetMapping("/recomendar")
    public ResponseEntity<Map<String, Object>> recomendar(@RequestParam BigDecimal presupuesto) {
        if (presupuesto == null || presupuesto.compareTo(new BigDecimal("20")) < 0
                || presupuesto.compareTo(new BigDecimal("2000")) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El presupuesto debe estar entre S/ 20 y S/ 2000.");
        if (mlUrl == null || mlUrl.isBlank() || mlToken == null || mlToken.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El generador de looks no está disponible.");

        // Únicamente combinaciones top+bottom de productos publicados.
        // Evita usar la puntuación ficticia del set de datos de prueba.
        List<Map<String, Object>> opciones = db.query("""
            SELECT v1.id_variante AS id_superior,p1.nombre AS superior,
                   v1.precio AS precio_superior,
                   v2.id_variante AS id_inferior,p2.nombre AS inferior,
                   v2.precio AS precio_inferior
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
             ORDER BY v1.id_variante,v2.id_variante
             LIMIT 500
            """,(rs,i) -> {
                Map<String,Object> row = new LinkedHashMap<>();
                row.put("id_variante_superior",rs.getLong("id_superior"));
                row.put("superior",rs.getString("superior"));
                row.put("precio_superior_soles",rs.getBigDecimal("precio_superior"));
                row.put("id_variante_inferior",rs.getLong("id_inferior"));
                row.put("inferior",rs.getString("inferior"));
                row.put("precio_inferior_soles",rs.getBigDecimal("precio_inferior"));
                // Obligatoria por el esquema de entrada, ignorada en fuente REALES.
                row.put("puntuacion_estilo_simulada_0_100",0);
                row.put("presupuesto_soles",presupuesto);
                return row;
            });

        if (opciones.isEmpty())
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("fuente","CATALOGO","factibles",0,
                        "mensaje","Todavía no hay combinaciones con stock."));

        try {
            Map<String,Object> entrada = Map.of(
                    "fuente","REALES",
                    "registros",opciones,
                    "presupuesto_soles",presupuesto);
            HttpRequest request = HttpRequest.newBuilder(
                URI.create(mlUrl.replaceAll("/+$","")
                    +"/internal/v1/modulos/OUTFITS/ejecutar"))
                .timeout(Duration.ofSeconds(12))
                .header("Content-Type","application/json")
                .header("X-ML-Token",mlToken)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(entrada)))
                .build();
            HttpResponse<String> respuesta =
                http.send(request,HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode()!=200)
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "El motor de combinaciones no pudo generar el look.");
            JsonNode salida=json.readTree(respuesta.body());
            if (!"OUTFITS".equals(salida.path("modulo").asText())
                    || !"REALES".equals(salida.path("fuente").asText()))
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "El motor devolvió un resultado inesperado.");
            if (salida.path("factibles").asInt()==0)
                return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(Map.of("fuente","CATALOGO","factibles",0,
                        "presupuestoSoles",presupuesto,
                        "mensaje","No encontramos combinaciones dentro de ese presupuesto."));
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of(
                    "fuente","CATALOGO",
                    "presupuestoSoles",presupuesto,
                    "factibles",salida.path("factibles").asInt(),
                    "aEstrella",json.convertValue(salida.path("a_estrella"),Map.class),
                    "hillClimbing",json.convertValue(salida.path("hill_climbing"),Map.class),
                    "mensaje","Combinaciones del catálogo disponibles. La búsqueda optimiza el presupuesto, no evalúa estilo."));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudieron consultar los outfits. Intenta nuevamente.");
        }
    }
}
