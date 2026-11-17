package com.Monarca.Backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Simulador académico de demanda: no guarda una nueva ejecución ni altera
 * inventario, pedidos, precios o modelos activos.
 */
@RestController
@RequestMapping("/api/admin/ml/demanda")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminMlDemandaSimuladorController {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    @Value("${monarca.ml.url:}")
    private String mlUrl;

    @Value("${monarca.ml.internal-token:}")
    private String token;

    public AdminMlDemandaSimuladorController(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    public record Escenario(Integer idProducto, Double precioSoles,
                            Double descuentoPorcentaje, Integer visitasSemana) {}

    @PostMapping("/simular")
    public Map<String, Object> simular(@RequestBody Escenario datos) {
        if (datos == null || datos.idProducto() == null || datos.idProducto() < 1
                || datos.precioSoles() == null || !Double.isFinite(datos.precioSoles())
                || datos.precioSoles() < 0.01 || datos.precioSoles() > 10000
                || datos.descuentoPorcentaje() == null || !Double.isFinite(datos.descuentoPorcentaje())
                || datos.descuentoPorcentaje() < 0 || datos.descuentoPorcentaje() > 100
                || datos.visitasSemana() == null || datos.visitasSemana() < 0
                || datos.visitasSemana() > 100000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Elige un producto y valores válidos de precio, descuento y visitas.");
        }

        String fuente = db.queryForObject(
                "SELECT fuente FROM monarca.ml_fuentes_datos WHERE modulo='DEMANDA'", String.class);
        if (!"PRUEBA".equals(fuente)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "La simulación de demanda solo está habilitada con fuente PRUEBA.");
        }

        if (mlUrl == null || mlUrl.isBlank() || token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Monarca-ML no está configurado.");
        }

        List<JsonNode> registros = db.query("""
                SELECT datos::text FROM monarca.ml_demo_registros
                 WHERE modulo='DEMANDA' ORDER BY numero
                """, (rs, i) -> {
            try {
                return json.readTree(rs.getString(1));
            } catch (Exception ex) {
                throw new IllegalStateException("Datos de prueba inválidos", ex);
            }
        });
        if (registros.size() < 30) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Se necesitan al menos 30 registros sintéticos para evaluar el modelo.");
        }

        Map<String, Object> parametros = new LinkedHashMap<>();
        parametros.put("fuente", "PRUEBA");
        parametros.put("registros", registros);
        parametros.put("escenario_demanda", Map.of(
                "idProducto", datos.idProducto(),
                "precioSoles", datos.precioSoles(),
                "descuentoPorcentaje", datos.descuentoPorcentaje(),
                "visitasSemana", datos.visitasSemana()));

        try {
            HttpRequest peticion = HttpRequest.newBuilder(
                    URI.create(mlUrl.replaceAll("/+$", "") + "/internal/v1/modulos/DEMANDA/ejecutar"))
                    .timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/json")
                    .header("X-ML-Token", token)
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(parametros)))
                    .build();
            HttpResponse<String> respuesta = http.send(
                    peticion, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() == 422) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Los valores no son compatibles con el dataset de prueba.");
            }
            if (respuesta.statusCode() != 200) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "El servicio de ML no pudo generar el escenario.");
            }
            JsonNode cuerpo = json.readTree(respuesta.body());
            if (!"DEMANDA".equals(cuerpo.path("modulo").asText())
                    || !"PRUEBA".equals(cuerpo.path("fuente").asText())
                    || cuerpo.path("escenario_personalizado").isNull()
                    || cuerpo.path("escenario_personalizado").isMissingNode()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "El servicio ML devolvió un resultado no válido.");
            }
            return Map.of("resultado", cuerpo.get("escenario_personalizado"));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo completar la simulación. Comprueba Monarca-ML.", ex);
        }
    }
}
