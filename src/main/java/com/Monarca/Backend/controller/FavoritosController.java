package com.Monarca.Backend.controller;

import com.Monarca.Backend.service.PerfilService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api/perfil/favoritos")
public class FavoritosController {
    private final JdbcTemplate db;
    private final PerfilService perfiles;
    public FavoritosController(JdbcTemplate db,PerfilService perfiles){this.db=db;this.perfiles=perfiles;}
    private Long usuario(Principal p){return perfiles.usuario(p.getName()).getIdUsuario();}

    @GetMapping
    public ResponseEntity<Map<String,Object>> listar(Principal p){
        Long id=usuario(p);
        var favoritos=db.queryForList("SELECT f.id_producto FROM monarca.favoritos f JOIN monarca.productos p ON p.id_producto=f.id_producto WHERE f.id_usuario=? AND p.activo ORDER BY f.creado DESC",Long.class,id);
        var alertas=db.queryForList("""
            SELECT id,id_producto AS "idProducto",id_variante AS "idVariante",nombre,talla,color,
            precio_anterior AS "precioAnterior",precio_nuevo AS "precioNuevo",creado,leida
            FROM monarca.favoritos_alertas WHERE id_usuario=? ORDER BY creado DESC,id DESC LIMIT 100
            """,id);
        Integer nuevas=db.queryForObject("SELECT count(*)::int FROM monarca.favoritos_alertas WHERE id_usuario=? AND NOT leida",Integer.class,id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("favoritos",favoritos,"alertas",alertas,"noLeidas",nuevas));
    }
    @PutMapping("/{producto}")
    public ResponseEntity<Void> guardar(Principal p,@PathVariable Long producto){
        Long id=usuario(p);
        int n=db.update("""
            INSERT INTO monarca.favoritos(id_usuario,id_producto)
            SELECT ?,id_producto FROM monarca.productos WHERE id_producto=? AND activo
            ON CONFLICT DO NOTHING
            """,id,producto);
        if(n==0&&!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM monarca.favoritos WHERE id_usuario=? AND id_producto=?)",Boolean.class,id,producto)))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Producto no disponible");
        return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/{producto}")
    public ResponseEntity<Void> quitar(Principal p,@PathVariable Long producto){
        db.update("DELETE FROM monarca.favoritos WHERE id_usuario=? AND id_producto=?",usuario(p),producto);
        return ResponseEntity.noContent().build();
    }
    @PutMapping("/alertas/leidas")
    public ResponseEntity<Void> leer(Principal p){
        db.update("UPDATE monarca.favoritos_alertas SET leida=true WHERE id_usuario=? AND NOT leida",usuario(p));
        return ResponseEntity.noContent().build();
    }
}
