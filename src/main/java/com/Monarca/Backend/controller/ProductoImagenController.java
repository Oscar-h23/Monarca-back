package com.Monarca.Backend.controller;

import com.Monarca.Backend.service.ProductoImagenStorageService;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

/** Carga imágenes de catálogo al bucket público; solo administradores autenticados. */
@RestController
@RequestMapping("/api/productos/imagenes")
public class ProductoImagenController {
    private final ProductoImagenStorageService storage;

    public ProductoImagenController(ProductoImagenStorageService storage) {
        this.storage=storage;
    }

    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String,String>> cargar(@RequestParam("archivo") MultipartFile archivo) {
        String url=storage.subir(archivo);
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(Map.of("url",url));
    }

    @ExceptionHandler({IllegalArgumentException.class, ResponseStatusException.class})
    public ResponseEntity<Map<String,String>> error(Exception e) {
        HttpStatusCode status=e instanceof ResponseStatusException r ? r.getStatusCode() : HttpStatus.BAD_REQUEST;
        String mensaje=e instanceof ResponseStatusException r ? r.getReason() : e.getMessage();
        return ResponseEntity.status(status).body(Map.of("error",mensaje==null?"No se pudo subir la imagen.":mensaje));
    }
}
