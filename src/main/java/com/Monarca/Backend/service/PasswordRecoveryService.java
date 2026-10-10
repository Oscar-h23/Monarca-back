package com.Monarca.Backend.service;

import com.Monarca.Backend.model.Usuario;
import com.Monarca.Backend.repository.UsuarioRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.*;

/** Recuperación para una instancia: solo hashes de tokens en memoria, nunca en logs. */
@Service
public class PasswordRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryService.class);
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JavaMailSender correo;
    private final String remitente;
    private final String host;
    private final String pagina;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentMap<String, Enlace> enlaces = new ConcurrentHashMap<>();
    private final Map<String, Instant> solicitudes = new HashMap<>();
    private Clock clock = Clock.systemUTC();
    private final ExecutorService envios = new ThreadPoolExecutor(1, 2, 60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(50), r -> {
                Thread t = new Thread(r, "monarca-recuperacion"); t.setDaemon(true); return t;
            });

    public PasswordRecoveryService(UsuarioRepository usuarios, PasswordEncoder encoder,
            ObjectProvider<JavaMailSender> proveedor,
            @Value("${spring.mail.host:}") String host,
            @Value("${monarca.mail.from:}") String remitente,
            @Value("${monarca.password-reset-url:}") String pagina) {
        this.usuarios = usuarios; this.encoder = encoder; this.correo = proveedor.getIfAvailable();
        this.host = host; this.remitente = remitente; this.pagina = pagina;
    }

    public void solicitar(String email, String direccion) {
        if (!emailValido(email)) throw new IllegalArgumentException("Introduce un correo válido.");
        if (!configurado()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "La recuperación por correo todavía no está configurada. Contacta con Monarca.");
        String normalizado = email.trim().toLowerCase(Locale.ROOT);
        if (!permitir(normalizado, direccion)) return;
        try {
            // La respuesta HTTP es la misma y no espera al envío, exista o no la cuenta.
            envios.execute(() -> enviar(normalizado));
        } catch (RejectedExecutionException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El servicio está ocupado. Inténtalo en unos minutos.");
        }
    }

    private synchronized boolean permitir(String email, String direccion) {
        Instant ahora = clock.instant();
        solicitudes.entrySet().removeIf(e -> !e.getValue().isAfter(ahora));
        enlaces.entrySet().removeIf(e -> !e.getValue().vence().isAfter(ahora));
        String cuenta = "email:" + hash(email), ip = "ip:" + direccion;
        if (solicitudes.containsKey(cuenta) || solicitudes.containsKey(ip)
                || solicitudes.size() >= 10000 || enlaces.size() >= 10000) return false;
        solicitudes.put(cuenta, ahora.plusSeconds(60));
        solicitudes.put(ip, ahora.plusSeconds(30));
        return true;
    }

    private void enviar(String email) {
        String clave = null;
        try {
            Usuario usuario = usuarios.findByCorreoIgnoreCase(email).filter(u -> Boolean.TRUE.equals(u.getActivo())).orElse(null);
            if (usuario == null) return;
            byte[] bytes = new byte[32]; random.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            clave = hash(token);
            enlaces.put(clave, new Enlace(usuario.getIdUsuario(), usuario.getPassword(), clock.instant().plusSeconds(900)));
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setFrom(remitente); mensaje.setTo(usuario.getCorreo());
            mensaje.setSubject("Restablecer tu contraseña de Monarca");
            // El fragmento no se envía al servidor web ni como Referer.
            mensaje.setText("Recibimos una solicitud para cambiar tu contraseña de Monarca.\n\n"
                    + pagina + "#restablecer=" + token
                    + "\n\nEl enlace vence en 15 minutos y solo puede usarse una vez."
                    + "\nSi no solicitaste el cambio, ignora este mensaje.");
            correo.send(mensaje);
        } catch (RuntimeException ex) {
            if (clave != null) enlaces.remove(clave);
            log.warn("No se pudo enviar un correo de recuperación. Revisa la configuración SMTP y la conexión.");
        }
    }

    public void restablecer(String token, String password) {
        if (!passwordValido(password)) throw new IllegalArgumentException("Usa al menos 8 caracteres y un máximo de 72 bytes para la contraseña.");
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw enlaceInvalido();
        Enlace enlace = enlaces.get(hash(token));
        if (enlace == null || !enlace.vence().isAfter(clock.instant())) throw enlaceInvalido();
        String nueva = encoder.encode(password);
        // CAS en PostgreSQL: dos solicitudes simultáneas no pueden usar el mismo estado anterior.
        int cambios = usuarios.restablecerPassword(enlace.usuario(), enlace.passwordAnterior(), nueva, java.time.OffsetDateTime.now(clock));
        enlaces.remove(hash(token));
        if (cambios != 1) throw enlaceInvalido();
        enlaces.entrySet().removeIf(e -> e.getValue().usuario().equals(enlace.usuario()));
    }

    private IllegalArgumentException enlaceInvalido() {
        return new IllegalArgumentException("El enlace no es válido, ya se usó o ha vencido. Solicita otro.");
    }
    private boolean configurado() {
        if (correo == null || host.isBlank() || remitente.isBlank() || pagina.isBlank()) return false;
        try {
            URI uri = URI.create(pagina);
            boolean local = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
            return uri.getHost() != null && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && ("https".equals(uri.getScheme()) || (local && "http".equals(uri.getScheme())));
        } catch (IllegalArgumentException ex) { return false; }
    }
    public static boolean emailValido(String email) {
        return email != null && email.trim().length() <= 150
                && email.trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
    }
    public static boolean passwordValido(String password) {
        return password != null && password.length() >= 8 && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
    public static String hash(String texto) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    @PreDestroy public void cerrar() { envios.shutdownNow(); }
    private record Enlace(Long usuario, String passwordAnterior, Instant vence) {}
}
