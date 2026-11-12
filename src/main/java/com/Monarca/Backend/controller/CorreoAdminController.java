package com.Monarca.Backend.controller;

import com.Monarca.Backend.service.NotificacionesPedidoService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.util.Map;

/**
 * Herramienta de diagnóstico: no expone credenciales, remitentes ni
 * destinatarios. Solo se permite probar con el correo del administrador autenticado.
 */
@RestController
@RequestMapping("/api/admin/correo")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class CorreoAdminController {
    private final NotificacionesPedidoService notificaciones;

    public CorreoAdminController(NotificacionesPedidoService notificaciones) {
        this.notificaciones = notificaciones;
    }

    @GetMapping("/estado")
    public NotificacionesPedidoService.EstadoCorreo estado() {
        return notificaciones.estado();
    }

    @PostMapping("/prueba")
    public Map<String,String> probar(Principal principal) {
        if (principal == null || principal.getName() == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión no válida.");
        notificaciones.enviarPruebaAdministrador(principal.getName());
        return Map.of("mensaje", "Resend aceptó la solicitud de prueba; verifica la bandeja de entrada y el panel de Resend.");
    }
}
