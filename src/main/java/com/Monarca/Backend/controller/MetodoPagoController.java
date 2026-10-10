package com.Monarca.Backend.controller;

import com.Monarca.Backend.repository.MetodoPagoRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/metodos-pago")
public class MetodoPagoController {
    private final MetodoPagoRepository repository;
    public MetodoPagoController(MetodoPagoRepository repository) { this.repository = repository; }

    @GetMapping
    public List<Map<String, Object>> listar() {
        return repository.findAll().stream().filter(m -> Boolean.TRUE.equals(m.getActivo()))
                .map(m -> Map.<String, Object>of("idMetodoPago", m.getIdMetodoPago(),
                        "codigo", m.getCodigo(), "nombre", m.getNombre())).toList();
    }
}
