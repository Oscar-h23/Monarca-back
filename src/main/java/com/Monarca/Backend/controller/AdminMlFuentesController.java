package com.Monarca.Backend.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Selección persistente de fuente para datasets de entrenamiento.
 * Nunca modifica pedidos/valoraciones reales y no cambia inferencias públicas.
 * Sólo el administrador puede consultar o modificar la fuente.
 */
@RestController
@RequestMapping("/api/admin/ml")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminMlFuentesController {
    private final JdbcTemplate db;
    private static final Set<String> MODULOS = Set.of("TALLAS","OUTFITS","PATRONES","CLUSTERING","DEMANDA");
    private static final Set<String> FUENTES = Set.of("REALES","PRUEBA");

    public AdminMlFuentesController(JdbcTemplate db) {this.db=db;}

    public record FuenteInfo(String modulo, String fuente, long registrosDisponibles,
                             boolean datasetImplementado, String descripcion) {}
    public record Seleccion(String fuente) {}

    @GetMapping("/fuentes")
    @Transactional(readOnly = true)
    public List<FuenteInfo> fuentes() {
        return db.query("SELECT modulo,fuente FROM monarca.ml_fuentes_datos ORDER BY modulo",
                (rs,n)->informacion(rs.getString("modulo"),rs.getString("fuente")));
    }

    private FuenteInfo informacion(String modulo,String fuente) {
        if ("CLUSTERING".equals(modulo) || "DEMANDA".equals(modulo) || "OUTFITS".equals(modulo)) {
            if ("PRUEBA".equals(fuente)) {
                Long n=db.queryForObject("SELECT count(*) FROM monarca.ml_demo_registros WHERE modulo=?",Long.class,modulo);
                return new FuenteInfo(modulo,fuente,n==null?0:n,true,
                    "Dataset sintético de demostración; resultados no verificables con compradores reales.");
            }
            if ("OUTFITS".equals(modulo)) {
                Long n=db.queryForObject("""
                  SELECT count(*) FROM monarca.variantes_producto v1
                  JOIN monarca.productos p1 ON p1.id_producto=v1.id_producto
                  JOIN monarca.categorias c1 ON c1.id_categoria=p1.id_categoria
                  CROSS JOIN monarca.variantes_producto v2
                  JOIN monarca.productos p2 ON p2.id_producto=v2.id_producto
                  JOIN monarca.categorias c2 ON c2.id_categoria=p2.id_categoria
                  WHERE p1.activo AND p2.activo AND v1.activo AND v2.activo
                    AND v1.stock>0 AND v2.stock>0
                    AND c1.nombre='Tops' AND c2.nombre='Bottoms'
                """,Long.class);
                return new FuenteInfo(modulo,fuente,n==null?0:Math.min(n,300),true,
                    "Combinaciones reales de productos disponibles; sin puntuaciones estéticas reales.");
            }
            return new FuenteInfo(modulo,fuente,0,false,
                "Faltan datos históricos observados de visitas o comportamiento; selecciona Prueba.");
        }
        if (!"TALLAS".equals(modulo)) {
            return new FuenteInfo(modulo,fuente,0,false,
                "Módulo heredado pendiente de integración.");
        }
        long registros = "PRUEBA".equals(fuente)
          ? db.queryForObject("SELECT count(*) FROM monarca.ml_tallas_prueba",Long.class)
          : db.queryForObject("""
                SELECT count(*) FROM monarca.valoraciones_talla
                 WHERE consentimiento_ml IS TRUE
                   AND CASE WHEN categoria='Tops'
                     THEN busto_cm IS NOT NULL AND cintura_cm IS NOT NULL
                       AND prenda_pecho_cm IS NOT NULL AND prenda_cintura_cm IS NOT NULL
                   WHEN categoria='Bottoms'
                     THEN cintura_cm IS NOT NULL AND cadera_cm IS NOT NULL
                       AND prenda_cintura_cm IS NOT NULL AND prenda_cadera_cm IS NOT NULL
                   WHEN categoria='Vestidos'
                     THEN busto_cm IS NOT NULL AND cintura_cm IS NOT NULL AND cadera_cm IS NOT NULL
                       AND prenda_pecho_cm IS NOT NULL AND prenda_cintura_cm IS NOT NULL
                       AND prenda_cadera_cm IS NOT NULL
                   ELSE FALSE END
                """,Long.class);
        return new FuenteInfo(modulo,fuente,registros,true,
          "PRUEBA".equals(fuente) ?
            "Ejemplos sintéticos para la demostración; no miden precisión real." :
            "Valoraciones auténticas consentidas y con medidas necesarias.");
    }

    @PutMapping("/fuentes/{modulo}")
    @Transactional
    public FuenteInfo seleccionar(@PathVariable String modulo, @RequestBody Seleccion request) {
        String m=modulo.trim().toUpperCase(Locale.ROOT);
        String f=request==null || request.fuente()==null ? "" :
                request.fuente().trim().toUpperCase(Locale.ROOT);
        if(!MODULOS.contains(m)||!FUENTES.contains(f))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Módulo o fuente no admitidos.");
        int n=db.update("UPDATE monarca.ml_fuentes_datos SET fuente=?, actualizado_en=now() WHERE modulo=?",f,m);
        if(n!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Módulo no configurado.");
        return informacion(m,f);
    }

    /**
     * El archivo siempre usa la fuente seleccionada en la base. Es apto para
     * src.preparar_tallas del proyecto Monarca-ML. Sólo se consulta por admin.
     */
    @GetMapping(value="/tallas/dataset.csv",produces="text/csv")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> datasetTallas() {
        String fuente=db.queryForObject(
            "SELECT fuente FROM monarca.ml_fuentes_datos WHERE modulo='TALLAS'",String.class);
        String sql = "PRUEBA".equals(fuente)
          ? """
            SELECT categoria,talla_comprada,ajuste,busto_cm,cintura_cm,cadera_cm,
                   prenda_pecho_cm,prenda_cintura_cm,prenda_cadera_cm
              FROM monarca.ml_tallas_prueba ORDER BY id_ejemplo
            """
          : """
            SELECT categoria,talla_comprada,ajuste,busto_cm,cintura_cm,cadera_cm,
                   prenda_pecho_cm,prenda_cintura_cm,prenda_cadera_cm
              FROM monarca.valoraciones_talla
             WHERE consentimiento_ml IS TRUE
             ORDER BY fecha_valoracion,id_valoracion
            """;
        String[] columnas={"categoria","talla_comprada","ajuste","busto_cm","cintura_cm",
                           "cadera_cm","prenda_pecho_cm","prenda_cintura_cm","prenda_cadera_cm"};
        StringBuilder contenido=new StringBuilder("categoria,talla_comprada,ajuste,busto_cm,cintura_cm,cadera_cm,prenda_pecho_cm,prenda_cintura_cm,prenda_cadera_cm\n");
        db.query(sql,rs->{
            for(int i=0;i<columnas.length;i++) {
                if(i>0)contenido.append(',');
                String valor=rs.getString(columnas[i]);
                if(valor!=null)contenido.append('"').append(valor.replace("\"","\"\"")).append('"');
            }
            contenido.append('\n');
        });
        byte[] bytes=contenido.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=monarca_tallas_"+fuente.toLowerCase(Locale.ROOT)+".csv")
                .header(HttpHeaders.CACHE_CONTROL,"no-store")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(bytes);
    }
}
