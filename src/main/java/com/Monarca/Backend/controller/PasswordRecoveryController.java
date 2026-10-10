package com.Monarca.Backend.controller;

import com.Monarca.Backend.service.PasswordRecoveryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class PasswordRecoveryController {
    private final PasswordRecoveryService recovery;
    public PasswordRecoveryController(PasswordRecoveryService recovery) { this.recovery = recovery; }
    public record Solicitud(String email) {}
    public record Restablecer(String token, String password) {}

    @PostMapping("/olvide-password")
    public ResponseEntity<?> solicitar(@RequestBody Solicitud solicitud, HttpServletRequest request) {
        try {
            recovery.solicitar(solicitud.email(), request.getRemoteAddr());
            return ResponseEntity.ok(Map.of("mensaje", "Si existe una cuenta activa con ese correo, recibirás un enlace. Revisa también spam. Puedes volver a solicitarlo en un minuto."));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", ex.getReason()));
        }
    }
    @PostMapping("/restablecer-password")
    public ResponseEntity<?> restablecer(@RequestBody Restablecer solicitud) {
        try {
            recovery.restablecer(solicitud.token(), solicitud.password());
            return ResponseEntity.ok(Map.of("mensaje", "Contraseña actualizada. Vuelve a iniciar sesión."));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }
}
