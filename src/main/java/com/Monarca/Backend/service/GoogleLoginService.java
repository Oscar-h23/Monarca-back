package com.Monarca.Backend.service;

import com.Monarca.Backend.model.Rol;
import com.Monarca.Backend.model.Usuario;
import com.Monarca.Backend.repository.RolRepository;
import com.Monarca.Backend.repository.UsuarioRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.UUID;

/** Google verifica la identidad; la sesión final sigue siendo un JWT de Monarca. */
@Service
public class GoogleLoginService {
    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final PasswordEncoder encoder;
    private final String clientId;

    public GoogleLoginService(UsuarioRepository usuarios, RolRepository roles, PasswordEncoder encoder,
                              @Value("${google.web-client-id:}") String clientId) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.encoder = encoder;
        this.clientId = clientId.trim();
    }

    public String getClientId() { return clientId; }

    private GoogleIdToken.Payload verificar(String credencial) {
        if (clientId.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "El acceso con Google todavía no está configurado.");
        if (credencial == null || credencial.isBlank() || credencial.length() > 16000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credencial de Google inválida.");
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(), JacksonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(clientId)).build();
            GoogleIdToken token = verifier.verify(credencial);
            if (token == null || token.getPayload() == null)
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google rechazó la credencial.");
            GoogleIdToken.Payload payload = token.getPayload();
            if (!Boolean.TRUE.equals(payload.getEmailVerified())
                    || payload.getEmail() == null || payload.getEmail().isBlank()
                    || payload.getSubject() == null || payload.getSubject().isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La cuenta de Google no está verificada.");
            }
            return payload;
        } catch (IOException | GeneralSecurityException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo verificar la sesión con Google.");
        }
    }

    @Transactional
    public Usuario iniciarSesion(String credencial) {
        GoogleIdToken.Payload p = verificar(credencial);
        Usuario encontrado = usuarios.findByGoogleSub(p.getSubject()).orElse(null);
        if (encontrado != null) {
            if (!Boolean.TRUE.equals(encontrado.getActivo()))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La cuenta está desactivada.");
            return encontrado;
        }
        String email = p.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        // Nunca vincular automáticamente por correo: usar el flujo autenticado de vinculación.
        if (usuarios.existsByCorreoIgnoreCase(email))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este correo ya está registrado. Entra con tu contraseña y vincula Google desde Mi perfil.");
        Rol cliente = roles.findByNombre("CLIENTE")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "No está configurado el rol CLIENTE."));
        Usuario nuevo = new Usuario();
        nuevo.setNombres(texto(p.get("given_name"), "Cliente"));
        nuevo.setApellidos(texto(p.get("family_name"), "Google"));
        nuevo.setCorreo(email);
        nuevo.setRol(cliente);
        // La contraseña no es conocida por el usuario ni permite iniciar sesión mediante Google.
        nuevo.setPassword(encoder.encode(UUID.randomUUID() + "-" + UUID.randomUUID()));
        nuevo.setGoogleSub(p.getSubject());
        nuevo.setActivo(true);
        return usuarios.save(nuevo);
    }

    @Transactional
    public void vincular(String correoAutenticado, String credencial) {
        GoogleIdToken.Payload p = verificar(credencial);
        Usuario actual = usuarios.findByCorreoIgnoreCase(correoAutenticado)
                .filter(u -> Boolean.TRUE.equals(u.getActivo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cuenta no disponible."));
        if (!actual.getCorreo().equalsIgnoreCase(p.getEmail()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El correo de Google debe coincidir con tu cuenta de Monarca.");
        Usuario dueño = usuarios.findByGoogleSub(p.getSubject()).orElse(null);
        if (dueño != null && !dueño.getIdUsuario().equals(actual.getIdUsuario()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta cuenta de Google ya está vinculada a otro usuario.");
        if (actual.getGoogleSub() != null && !actual.getGoogleSub().equals(p.getSubject()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tu perfil ya está vinculado a otra cuenta de Google.");
        actual.setGoogleSub(p.getSubject());
        usuarios.save(actual);
    }

    private static String texto(Object valor, String fallback) {
        if (!(valor instanceof String s) || s.isBlank()) return fallback;
        return s.trim().substring(0, Math.min(s.trim().length(), 100));
    }
}
