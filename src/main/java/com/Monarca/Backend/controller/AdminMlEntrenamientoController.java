package com.Monarca.Backend.controller;

import com.Monarca.Backend.service.MlTallasEntrenador;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/** Modelos separados de las valoraciones auténticas; toda acción requiere ADMIN. */
@RestController
@RequestMapping("/api/admin/ml/tallas")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminMlEntrenamientoController {
    private final JdbcTemplate db;
    private final MlTallasEntrenador entrenador;
    private final ObjectMapper mapper;
    public AdminMlEntrenamientoController(JdbcTemplate db, MlTallasEntrenador entrenador, ObjectMapper mapper) {
        this.db=db;this.entrenador=entrenador;this.mapper=mapper;
    }

    @PostMapping("/entrenar")
    public Map<String,Object> entrenar(){
        String fuente=db.queryForObject("SELECT fuente FROM monarca.ml_fuentes_datos WHERE modulo='TALLAS'",String.class);
        return entrenador.entrenar(fuente,entrenador.dataset(fuente));
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
