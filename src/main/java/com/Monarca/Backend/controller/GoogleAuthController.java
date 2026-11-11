package com.Monarca.Backend.controller;

import com.Monarca.Backend.model.Usuario;
import com.Monarca.Backend.security.CustomUserDetailsService;
import com.Monarca.Backend.security.JwtUtil;
import com.Monarca.Backend.service.GoogleLoginService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/google")
public class GoogleAuthController {
    private final GoogleLoginService google;
    private final CustomUserDetailsService usuarios;
    private final JwtUtil jwt;

    public GoogleAuthController(GoogleLoginService google, CustomUserDetailsService usuarios, JwtUtil jwt) {
        this.google = google;
        this.usuarios = usuarios;
        this.jwt = jwt;
    }

    public record Credencial(String credential) {}

    @GetMapping("/configuracion")
    public Map<String, String> configuracion() {
        return Map.of("clientId", google.getClientId());
    }

    @PostMapping
    public Map<String, String> login(@RequestBody Credencial datos) {
        Usuario u = google.iniciarSesion(datos == null ? null : datos.credential());
        UserDetails detalle = usuarios.loadUserByUsername(u.getCorreo());
        return Map.of("token", jwt.generateToken(detalle), "email", detalle.getUsername(),
                "rol", detalle.getAuthorities().iterator().next().getAuthority());
    }

    @PostMapping("/vincular")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> vincular(Principal principal, @RequestBody Credencial datos) {
        google.vincular(principal.getName(), datos == null ? null : datos.credential());
        return ResponseEntity.noContent().build();
    }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> errorGoogle(
            org.springframework.web.server.ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of(
                "error", e.getReason() == null ? "No se pudo iniciar sesión con Google." : e.getReason()));
    }
}
