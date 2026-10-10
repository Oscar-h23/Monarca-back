package com.Monarca.Backend.controller;

import com.Monarca.Backend.dto.LoginDto;
import com.Monarca.Backend.dto.RegistroDto;
import com.Monarca.Backend.model.Rol;
import com.Monarca.Backend.model.Usuario;
import com.Monarca.Backend.repository.RolRepository;
import com.Monarca.Backend.repository.UsuarioRepository;
import com.Monarca.Backend.security.JwtUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private JwtUtil jwtUtil;


    // =========================================================
    // REGISTRO DE CLIENTE
    // =========================================================

    @PostMapping("/registro")
    public ResponseEntity<?> registrarCliente(@RequestBody RegistroDto dto) {

        if (dto == null || dto.getNombre() == null || dto.getNombre().isBlank() || dto.getNombre().trim().length() > 100
                || dto.getApellido() == null || dto.getApellido().isBlank() || dto.getApellido().trim().length() > 100
                || !com.Monarca.Backend.service.PasswordRecoveryService.emailValido(dto.getEmail())
                || !com.Monarca.Backend.service.PasswordRecoveryService.passwordValido(dto.getPassword())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Completa los nombres, apellidos y correo; la contraseña debe tener entre 8 caracteres y 72 bytes."));
        }
        // 1. Normalizar el correo
        String correo = dto.getEmail().trim().toLowerCase();

        // 2. Verificar si el correo ya está registrado
        if (usuarioRepository.findByCorreoIgnoreCase(correo).isPresent()) {

            return ResponseEntity
                    .badRequest()
                    .body("El correo ya está registrado");
        }

        // 3. Buscar el rol CLIENTE en la nueva BD
        Rol rolCliente = rolRepository
                .findByNombre("CLIENTE")
                .orElseThrow(() ->
                        new RuntimeException(
                                "No existe el rol CLIENTE en la base de datos"
                        )
                );

        // 4. Crear usuario
        Usuario nuevoUsuario = new Usuario();

        nuevoUsuario.setNombres(dto.getNombre().trim());
        nuevoUsuario.setApellidos(dto.getApellido().trim());
        nuevoUsuario.setCorreo(correo);

        // 5. Encriptar contraseña con BCrypt
        nuevoUsuario.setPassword(
                passwordEncoder.encode(dto.getPassword())
        );

        // 6. Asignar rol
        nuevoUsuario.setRol(rolCliente);

        // 7. Usuario activo
        nuevoUsuario.setActivo(true);

        // fechaCreacion y fechaActualizacion
        // son generadas automáticamente por @PrePersist
        // dentro de Usuario.java

        // 8. Guardar en PostgreSQL / Supabase
        usuarioRepository.save(nuevoUsuario);

        Map<String, String> response = new HashMap<>();

        response.put(
                "mensaje",
                "Cuenta creada con éxito"
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    // =========================================================
    // LOGIN
    // =========================================================

    @PostMapping("/login")
    public ResponseEntity<?> loginUsuario(
            @RequestBody LoginDto loginDto
    ) {

        try {

            String correo = loginDto
                    .getEmail()
                    .trim()
                    .toLowerCase();

            // 1. Autenticar correo + contraseña
            Authentication authentication =
                    authenticationManager.authenticate(

                            new UsernamePasswordAuthenticationToken(
                                    correo,
                                    loginDto.getPassword()
                            )
                    );


            // 2. Obtener usuario autenticado
            UserDetails userDetails =
                    (UserDetails) authentication.getPrincipal();


            // 3. Generar JWT
            String jwt =
                    jwtUtil.generateToken(userDetails);


            // 4. Obtener rol
            String rol = userDetails
                    .getAuthorities()
                    .iterator()
                    .next()
                    .getAuthority();


            // 5. Crear respuesta
            Map<String, String> response =
                    new HashMap<>();

            response.put("token", jwt);

            response.put(
                    "email",
                    userDetails.getUsername()
            );

            response.put(
                    "rol",
                    rol
            );


            return ResponseEntity.ok(response);


        } catch (AuthenticationException e) {

            Map<String, String> response =
                    new HashMap<>();

            response.put(
                    "error",
                    "Correo o contraseña incorrectos"
            );

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(response);
        }
    }
}