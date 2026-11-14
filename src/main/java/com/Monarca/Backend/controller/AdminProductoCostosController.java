package com.Monarca.Backend.controller;

import com.Monarca.Backend.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/admin/productos/costos")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminProductoCostosController {
    private final ProductoRepository productos;
    public record CostoProducto(Long idProducto, BigDecimal costoCompra) {}

    @GetMapping
    @Transactional(readOnly = true)
    public List<CostoProducto> listarCostos() {
        return productos.findAll().stream()
            .map(p -> new CostoProducto(p.getIdProducto(), p.getCostoCompra()))
            .toList();
    }
}
