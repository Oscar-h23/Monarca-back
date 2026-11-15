package com.Monarca.Backend.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Una valoración por línea de compra finalizada. Obtiene identidad y medidas
 * exclusivamente del backend; la API no acepta datos corporales del navegador.
 */
@RestController
@RequestMapping("/api/pedidos/valoraciones-talla")
public class ValoracionTallaController {
    private final NamedParameterJdbcTemplate db;

    public ValoracionTallaController(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    public record Solicitud(Long idDetalle, String ajuste, Boolean consentimientoMl) {}
    public record Respuesta(Long idDetalle, String ajuste) {}

    private String identidad(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión.");
        }
        return auth.getName();
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Respuesta> listar(Authentication auth) {
        return db.query("""
            SELECT vt.id_detalle, vt.ajuste
              FROM monarca.valoraciones_talla vt
              JOIN monarca.usuarios u ON u.id_usuario = vt.id_usuario
             WHERE lower(u.correo)=lower(:correo) AND u.activo=true
             ORDER BY vt.fecha_valoracion DESC
            """, Map.of("correo", identidad(auth)),
            (rs, n) -> new Respuesta(rs.getLong("id_detalle"), rs.getString("ajuste")));
    }

    public record Requisito(Long idDetalle, List<String> faltantes) {}

    @GetMapping("/requisitos")
    @Transactional(readOnly = true)
    public List<Requisito> requisitos(Authentication auth) {
        String correo = identidad(auth);
        return db.query("""
            SELECT d.id_detalle, c.nombre categoria,
                   u.busto_cm, u.cintura_cm, u.cadera_cm
              FROM monarca.detalles_pedido d
              JOIN monarca.pedidos p ON p.id_pedido = d.id_pedido
              JOIN monarca.usuarios u ON u.id_usuario = p.id_usuario
              JOIN monarca.variantes_producto v ON v.id_variante = d.id_variante
              JOIN monarca.productos pr ON pr.id_producto = v.id_producto
              JOIN monarca.categorias c ON c.id_categoria = pr.id_categoria
             WHERE lower(u.correo) = lower(:correo) AND u.activo = true
               AND p.estado IN ('FINALIZADO','ENTREGADO','COMPLETADO')
            """, Map.of("correo",correo), (rs, i) -> {
                String categoria = rs.getString("categoria");
                List<String> faltantes = new java.util.ArrayList<>();
                if (!List.of("Tops","Bottoms","Vestidos").contains(categoria)) {
                    faltantes.add("Categoría sin recomendación de tallas");
                } else {
                    if ((categoria.equals("Tops") || categoria.equals("Vestidos")) && rs.getBigDecimal("busto_cm") == null) faltantes.add("busto");
                    if (rs.getBigDecimal("cintura_cm") == null) faltantes.add("cintura");
                    if ((categoria.equals("Bottoms") || categoria.equals("Vestidos")) && rs.getBigDecimal("cadera_cm") == null) faltantes.add("cadera");
                }
                return new Requisito(rs.getLong("id_detalle"),faltantes);
            });
    }

    @PostMapping
    @Transactional
    public Respuesta valorar(Authentication auth, @RequestBody Solicitud solicitud) {
        String correo = identidad(auth);
        if (solicitud == null || solicitud.idDetalle() == null || solicitud.idDetalle() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona una prenda válida.");
        }
        if (!Boolean.TRUE.equals(solicitud.consentimientoMl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Necesitamos tu consentimiento para usar esta valoración en las recomendaciones.");
        }
        String ajuste = solicitud.ajuste() == null ? "" : solicitud.ajuste().trim().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("PEQUENA","BIEN","GRANDE").contains(ajuste)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona cómo te quedó la prenda.");
        }
        Map<String,Object> fila;
        try {
            fila = db.queryForMap("""
                SELECT d.id_detalle, p.estado, u.id_usuario, v.id_variante,
                       c.nombre AS categoria, d.talla AS talla_comprada,
                       u.busto_cm, u.cintura_cm, u.cadera_cm,
                       v.pecho_cm AS prenda_pecho_cm, v.cintura_cm AS prenda_cintura_cm,
                       v.cadera_cm AS prenda_cadera_cm
                  FROM monarca.detalles_pedido d
                  JOIN monarca.pedidos p ON p.id_pedido = d.id_pedido
                  JOIN monarca.usuarios u ON u.id_usuario = p.id_usuario
                  JOIN monarca.variantes_producto v ON v.id_variante = d.id_variante
                  JOIN monarca.productos pr ON pr.id_producto = v.id_producto
                  JOIN monarca.categorias c ON c.id_categoria = pr.id_categoria
                 WHERE d.id_detalle = :detalle AND lower(u.correo) = lower(:correo)
                   AND u.activo = true
                """, Map.of("detalle",solicitud.idDetalle(),"correo",correo));
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Prenda no encontrada en tus compras.");
        }
        if (!List.of("FINALIZADO","ENTREGADO","COMPLETADO").contains(fila.get("estado"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Puedes valorar la talla cuando el pedido haya sido entregado.");
        }
        if (!List.of("Tops","Bottoms","Vestidos").contains(fila.get("categoria"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La prenda no tiene recomendaciones de tallas.");
        }
        String categoria = (String)fila.get("categoria");
        List<String> medidasFaltantes = new java.util.ArrayList<>();
        if ((categoria.equals("Tops") || categoria.equals("Vestidos")) && fila.get("busto_cm") == null) medidasFaltantes.add("busto");
        if (fila.get("cintura_cm") == null) medidasFaltantes.add("cintura");
        if ((categoria.equals("Bottoms") || categoria.equals("Vestidos")) && fila.get("cadera_cm") == null) medidasFaltantes.add("cadera");
        if (!medidasFaltantes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Completa en tu perfil las medidas de " + String.join(", ", medidasFaltantes) + " antes de valorar.");
        }
        if (((categoria.equals("Tops") || categoria.equals("Vestidos")) && fila.get("prenda_pecho_cm") == null)
            || fila.get("prenda_cintura_cm") == null
            || ((categoria.equals("Bottoms") || categoria.equals("Vestidos")) && fila.get("prenda_cadera_cm") == null)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Esta prenda no tiene sus medidas completas. Solicita al administrador que las registre.");
        }
        MapSqlParameterSource datos = new MapSqlParameterSource();
        for (String campo : List.of("id_detalle","id_usuario","id_variante","categoria",
                "talla_comprada","busto_cm","cintura_cm","cadera_cm",
                "prenda_pecho_cm","prenda_cintura_cm","prenda_cadera_cm")) {
            datos.addValue(campo, fila.get(campo));
        }
        datos.addValue("ajuste",ajuste);
        try {
            db.update("""
                INSERT INTO monarca.valoraciones_talla
                    (id_detalle,id_usuario,id_variante,categoria,talla_comprada,ajuste,
                     busto_cm,cintura_cm,cadera_cm,
                     prenda_pecho_cm,prenda_cintura_cm,prenda_cadera_cm,
                     origen_medidas,consentimiento_ml)
                VALUES (:id_detalle,:id_usuario,:id_variante,:categoria,:talla_comprada,:ajuste,
                        :busto_cm,:cintura_cm,:cadera_cm,
                        :prenda_pecho_cm,:prenda_cintura_cm,:prenda_cadera_cm,
                        'AL_VALORAR',true)
                """, datos);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta prenda ya tiene una valoración registrada.");
        }
        return new Respuesta(solicitud.idDetalle(), ajuste);
    }
}
