package com.Monarca.Backend.controller;

import com.Monarca.Backend.model.Rol;
import com.Monarca.Backend.model.Usuario;
import com.Monarca.Backend.repository.RolRepository;
import com.Monarca.Backend.repository.UsuarioRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/usuarios")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminUsuariosController {
    private final UsuarioRepository usuarios;
    private final RolRepository roles;

    public record AsignarAdministradorRequest(
        @NotBlank @Email @Size(max=150) String correo
    ) {}
    public record AdministradorView(Long idUsuario, String correo, String nombres, String apellidos) {}

    @GetMapping("/administradores")
    @Transactional(readOnly = true)
    public List<AdministradorView> administradores() {
        return usuarios.findByRol_NombreIgnoreCaseAndActivoTrue("ADMIN").stream()
            .map(u -> new AdministradorView(u.getIdUsuario(), u.getCorreo(), u.getNombres(), u.getApellidos()))
            .toList();
    }

    @PutMapping("/administradores")
    @Transactional
    public ResponseEntity<?> asignarAdministrador(@Valid @RequestBody AsignarAdministradorRequest request) {
        String correo = request.correo().trim().toLowerCase(java.util.Locale.ROOT);
        Usuario usuario = usuarios.findByCorreoIgnoreCase(correo).orElse(null);
        if (usuario == null) {
            return ResponseEntity.status(404).body(Map.of("error",
                "No existe una cuenta registrada con ese correo. Pídele que se registre primero."));
        }
        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            return ResponseEntity.badRequest().body(Map.of("error", "La cuenta está desactivada."));
        }
        if ("ADMIN".equalsIgnoreCase(usuario.getRol().getNombre())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Esta cuenta ya es administradora."));
        }
        Rol admin = roles.findByNombre("ADMIN").orElse(null);
        if (admin == null || !Boolean.TRUE.equals(admin.getActivo())) {
            return ResponseEntity.internalServerError().body(Map.of("error", "No está configurado el rol ADMIN."));
        }
        usuario.setRol(admin);
        usuarios.save(usuario);
        return ResponseEntity.ok(Map.of("mensaje", "Administrador asignado correctamente. La cuenta debe volver a iniciar sesión."));
    }
}
