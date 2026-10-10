package com.Monarca.Backend.service;

import com.Monarca.Backend.model.Usuario;
import com.Monarca.Backend.repository.UsuarioRepository;
import com.Monarca.Backend.security.JwtUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordRecoveryServiceTest {
    UsuarioRepository usuarios;
    JavaMailSender correo;
    PasswordRecoveryService service;
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    Usuario usuario;
    String nueva;

    @BeforeEach void preparar() {
        usuarios = mock(UsuarioRepository.class); correo = mock(JavaMailSender.class);
        @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(correo);
        service = new PasswordRecoveryService(usuarios, encoder, provider, "smtp.example.test", "tienda@example.test", "http://localhost:5500/fronted/Paginas/login.html");
        usuario = new Usuario(); usuario.setIdUsuario(1L); usuario.setCorreo("cliente@example.test");
        usuario.setPassword(encoder.encode(UUID.randomUUID().toString()));
        nueva = UUID.randomUUID().toString();
    }
    @AfterEach void terminar() { service.cerrar(); }
    private String solicitar() {
        when(usuarios.findByCorreoIgnoreCase(usuario.getCorreo())).thenReturn(Optional.of(usuario));
        service.solicitar(usuario.getCorreo(), "127.0.0.1");
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(correo, timeout(2000)).send(captor.capture());
        SimpleMailMessage mensaje = captor.getValue();
        assertEquals(usuario.getCorreo(), mensaje.getTo()[0]);
        return mensaje.getText().split("#restablecer=")[1].split("\\s")[0];
    }
    @Test void enlaceCambiaHashUnaSolaVez() {
        String token = solicitar();
        when(usuarios.restablecerPassword(eq(1L), eq(usuario.getPassword()), anyString(), any())).thenReturn(1);
        service.restablecer(token, nueva);
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(usuarios).restablecerPassword(eq(1L), eq(usuario.getPassword()), hash.capture(), any());
        assertTrue(encoder.matches(nueva, hash.getValue()));
        assertThrows(IllegalArgumentException.class, () -> service.restablecer(token, nueva));
        verify(usuarios, times(1)).restablecerPassword(anyLong(), anyString(), anyString(), any());
    }
    @Test void enlaceVencidoNoModificaCuenta() {
        String token = solicitar();
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.now().plusSeconds(901), ZoneOffset.UTC));
        assertThrows(IllegalArgumentException.class, () -> service.restablecer(token, nueva));
        verify(usuarios, never()).restablecerPassword(anyLong(), anyString(), anyString(), any());
    }
    @Test void cambioDePasswordPrevioInvalidaEnlace() {
        String token = solicitar();
        when(usuarios.restablecerPassword(anyLong(), anyString(), anyString(), any())).thenReturn(0);
        assertThrows(IllegalArgumentException.class, () -> service.restablecer(token, nueva));
    }
    @Test void tokenManipuladoNoModificaCuenta() {
        String token = solicitar();
        String manipulado = (token.startsWith("A") ? "B" : "A") + token.substring(1);
        assertThrows(IllegalArgumentException.class, () -> service.restablecer(manipulado, nueva));
        verify(usuarios, never()).restablecerPassword(anyLong(), anyString(), anyString(), any());
    }
    @Test void correoInexistenteNoEnvia() {
        when(usuarios.findByCorreoIgnoreCase("ausente@example.test")).thenReturn(Optional.empty());
        service.solicitar("ausente@example.test", "127.0.0.1");
        verify(usuarios, timeout(2000)).findByCorreoIgnoreCase("ausente@example.test");
        verifyNoInteractions(correo);
    }
    @Test void solicitudesRepetidasSeLimitan() {
        solicitar();
        service.solicitar(usuario.getCorreo(), "127.0.0.1");
        verify(usuarios, times(1)).findByCorreoIgnoreCase(usuario.getCorreo());
    }
    @Test void passwordInvalidoNoConsumeEnlace() {
        String token = solicitar();
        assertThrows(IllegalArgumentException.class, () -> service.restablecer(token, ""));
        when(usuarios.restablecerPassword(anyLong(), anyString(), anyString(), any())).thenReturn(1);
        assertDoesNotThrow(() -> service.restablecer(token, nueva));
    }
    @Test void smtpSinConfigDevuelveIndisponibilidad() {
        ReflectionTestUtils.setField(service, "host", "");
        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> service.solicitar(usuario.getCorreo(), "127.0.0.1")).getStatusCode().value());
        verifyNoInteractions(usuarios, correo);
    }
    @Test void recuperacionNoAutenticaYResetInvalidaSesionAnterior() {
        String token = solicitar();
        JwtUtil jwt = new JwtUtil(UUID.randomUUID().toString() + UUID.randomUUID());
        assertThrows(io.jsonwebtoken.JwtException.class, () -> jwt.extractUsername(token));
        var antes = org.springframework.security.core.userdetails.User.withUsername(usuario.getCorreo())
                .password(usuario.getPassword()).roles("CLIENTE").build();
        String acceso = jwt.generateToken(antes);
        assertTrue(jwt.validateToken(acceso, antes));
        var despues = org.springframework.security.core.userdetails.User.withUsername(usuario.getCorreo())
                .password(encoder.encode(nueva)).roles("CLIENTE").build();
        assertFalse(jwt.validateToken(acceso, despues));
    }
}
