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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** Modelos separados de las valoraciones auténticas; toda acción requiere ADMIN. */
@RestController
@RequestMapping("/api/admin/ml/tallas")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminMlEntrenamientoController {
    private final JdbcTemplate db;
    private final AdminMlFuentesController fuentes;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    @Value("${monarca.ml.url:}") private String mlUrl;
    @Value("${monarca.ml.internal-token:}") private String mlToken;

    public AdminMlEntrenamientoController(JdbcTemplate db, AdminMlFuentesController fuentes, ObjectMapper mapper) {
        this.db = db; this.fuentes = fuentes; this.mapper = mapper;
    }

    private void requireService() {
        if (mlUrl == null || mlUrl.isBlank() || mlToken == null || mlToken.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "El servicio ML no está configurado");
    }

    @PostMapping("/entrenar")
    public Map<String,Object> entrenar() {
        requireService();
        // Capturamos el dataset desde el mismo endpoint ADMIN: nunca hay mezcla de fuentes.
        String fuente = db.queryForObject(
            "SELECT fuente FROM monarca.ml_fuentes_datos WHERE modulo='TALLAS'", String.class);
        byte[] csv = fuentes.datasetTallas().getBody();
        if (csv == null || csv.length > 300000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dataset vacío o demasiado grande");
        String dataset = new String(csv, StandardCharsets.UTF_8);
        try {
            var body = mapper.writeValueAsString(Map.of("fuente", fuente, "dataset_csv", dataset));
            var request = HttpRequest.newBuilder(URI.create(mlUrl.replaceAll("/+$", "") + "/internal/v1/tallas/entrenar"))
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "application/json")
                .header("X-ML-Token", mlToken)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 422)
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Dataset insuficiente o inválido para entrenar las tres clases");
            if (response.statusCode() != 200)
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "El servicio ML rechazó el entrenamiento");
            JsonNode result = mapper.readTree(response.body());
            if (!fuente.equals(result.path("fuente").asText())
                || !Set.of("knn", "arbol").contains(result.path("algoritmo").asText())
                || result.path("modelo_base64").asText().length() > 2000000)
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Resultado de entrenamiento inválido");
            Long id = db.queryForObject("""
                INSERT INTO monarca.ml_modelos_talla (fuente, algoritmo, modelo_base64, metricas)
                VALUES (?, ?, ?, CAST(? AS jsonb)) RETURNING id_modelo
                """, Long.class, fuente, result.path("algoritmo").asText(),
                result.path("modelo_base64").asText(), result.path("metricas").toString());
            return Map.of("idModelo", id, "fuente", fuente,
                "algoritmo", result.path("algoritmo").asText(),
                "metricas", mapper.convertValue(result.path("metricas"), Map.class),
                "activo", false);
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo completar el entrenamiento. Verifica el servicio ML.");
        }
    }

    @GetMapping("/modelos")
    @Transactional(readOnly=true)
    public List<Map<String,Object>> modelos() {
        return db.query("""
            SELECT id_modelo,fuente,algoritmo,metricas::text AS metricas,activo,creado_en
              FROM monarca.ml_modelos_talla ORDER BY creado_en DESC LIMIT 25
            """, (rs, n) -> {
            Map<String,Object> model = new LinkedHashMap<>();
            model.put("idModelo", rs.getLong("id_modelo"));
            model.put("fuente", rs.getString("fuente"));
            model.put("algoritmo", rs.getString("algoritmo"));
            model.put("activo", rs.getBoolean("activo"));
            model.put("creadoEn", rs.getTimestamp("creado_en").toInstant().toString());
            try { model.put("metricas", mapper.readValue(rs.getString("metricas"), Map.class)); }
            catch (Exception e) { model.put("metricas", Map.of()); }
            return model;
        });
    }

    @PostMapping("/modelos/{id}/activar")
    @Transactional
    public Map<String,Object> activar(@PathVariable long id) {
        List<Map<String,Object>> found = db.query("""
            SELECT fuente, algoritmo FROM monarca.ml_modelos_talla WHERE id_modelo=?
            """, (rs,n)->Map.<String,Object>of("fuente",rs.getString(1),"algoritmo",rs.getString(2)), id);
        if (found.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Modelo no encontrado");
        db.update("UPDATE monarca.ml_modelos_talla SET activo=false WHERE activo=true");
        db.update("UPDATE monarca.ml_modelos_talla SET activo=true WHERE id_modelo=?", id);
        return Map.of("idModelo",id,"activo",true,
            "fuente",found.get(0).get("fuente"),"algoritmo",found.get(0).get("algoritmo"),
            "mensaje","Modelo experimental activado; la calidad con clientes reales no está demostrada");
    }

    @PostMapping("/desactivar")
    public Map<String,Object> desactivar() {
        db.update("UPDATE monarca.ml_modelos_talla SET activo=false WHERE activo=true");
        return Map.of("activo",false,"mensaje","Se restaura la recomendación mediante reglas");
    }
}
