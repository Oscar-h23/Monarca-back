package com.Monarca.Backend.controller;

import com.Monarca.Backend.service.MlEntrenamientoAutomaticoService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/ml/automatico")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminMlAutomaticoController {
    private final MlEntrenamientoAutomaticoService servicio;
    public AdminMlAutomaticoController(MlEntrenamientoAutomaticoService servicio){this.servicio=servicio;}
    @GetMapping
    public Map<String,Object> consultar(){return Map.of("config",servicio.config(),"historial",servicio.historial());}
    @PutMapping
    public MlEntrenamientoAutomaticoService.Config guardar(@RequestBody MlEntrenamientoAutomaticoService.Ajustes ajustes){return servicio.guardar(ajustes);}
}
