package com.Monarca.Backend.controller;

import com.Monarca.Backend.service.ReporteVentasService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/admin/reportes")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class ReporteVentasController {
    private final ReporteVentasService reportes;
    public ReporteVentasController(ReporteVentasService reportes) {this.reportes=reportes;}

    @GetMapping("/ventas")
    public ReporteVentasService.Resumen resumen(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate hasta) {
        LocalDate hoy=LocalDate.now(ZoneId.of("America/Lima"));
        return reportes.consultar(desde == null ? hoy.minusDays(29) : desde,
                hasta == null ? hoy : hasta);
    }
}
