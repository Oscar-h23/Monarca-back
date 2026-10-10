package com.Monarca.Backend.controller;

import com.Monarca.Backend.dto.ProductoDto;
import com.Monarca.Backend.dto.ProductoResponseDto;
import com.Monarca.Backend.service.ProductoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})public class ProductoController {

    @Autowired
    private ProductoService productoService;

    // Listar todos (Público)
    @GetMapping
public ResponseEntity<?> listarTodos() {

    return ResponseEntity.ok(
            productoService.listarCatalogo()
    );
}

    // Guardar nuevo producto (Solo Admin)
    @PostMapping
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> guardar(@RequestBody ProductoDto productoDto) {
        productoService.guardar(productoDto);
        return ResponseEntity.status(HttpStatus.CREATED).body("Producto creado con éxito");
    }

    // Buscar por ID (Público)
    @GetMapping("/{id}")
    public ResponseEntity<ProductoResponseDto> buscarPorId(@PathVariable Long id) {
        return productoService.buscarCatalogoPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Eliminar (Solo Admin)
    @DeleteMapping("/{id}")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        productoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
    // Añade este método en tu controlador
@PutMapping("/{id}")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody ProductoDto productoDto) {
    productoService.actualizar(id, productoDto);
    return ResponseEntity.ok().body("Producto actualizado con éxito");
}
}