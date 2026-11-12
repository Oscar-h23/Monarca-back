package com.Monarca.Backend.service;

import com.Monarca.Backend.model.Pedido;
import com.Monarca.Backend.model.Usuario;
import com.Monarca.Backend.repository.UsuarioRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Avisos de pedidos únicamente DESPUÉS de confirmar la transacción de negocio.
 * Fallos SMTP nunca revierten pedidos ni cambios de estado. No envía adjuntos ni
 * claves de acceso, ni enlaces públicos a evidencias privadas.
 */
@Service
public class NotificacionesPedidoService {
    private static final Logger log = LoggerFactory.getLogger(NotificacionesPedidoService.class);

    public enum Tipo {
        NUEVO_PEDIDO, EVIDENCIA_RECIBIDA, PAGO_APROBADO, PAGO_RECHAZADO,
        EN_PREPARACION, EN_CAMINO, FINALIZADO, CANCELADO
    }

    public record Aviso(
            Tipo tipo, Long idPedido, String codigoPedido, String correoCliente,
            String nombreCliente, BigDecimal total, String motivo
    ) {}

    private final ApplicationEventPublisher eventos;
    private final UsuarioRepository usuarios;
    private final JavaMailSender correo;
    private final String desde;
    private final String adminOverride;
    private final String tiendaUrl;
    private final boolean smtpConfigurado;
    private final ExecutorService envios = new ThreadPoolExecutor(
            1, 2, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(100), tarea -> {
                Thread hilo = new Thread(tarea, "nayelis-avisos-pedidos");
                hilo.setDaemon(true);
                return hilo;
            });

    public NotificacionesPedidoService(
            ApplicationEventPublisher eventos,
            UsuarioRepository usuarios,
            ObjectProvider<JavaMailSender> mailProvider,
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.username:}") String usuarioSmtp,
            @Value("${spring.mail.password:}") String passwordSmtp,
            @Value("${monarca.mail.from:}") String desde,
            @Value("${monarca.mail.admin-to:}") String adminOverride,
            @Value("${monarca.mail.shop-url:http://localhost:4200}") String tiendaUrl) {
        this.eventos = eventos;
        this.usuarios = usuarios;
        this.correo = mailProvider.getIfAvailable();
        this.desde = desde == null ? "" : desde.trim();
        this.adminOverride = adminOverride == null ? "" : adminOverride.trim();
        this.tiendaUrl = tiendaUrl == null ? "" : tiendaUrl.replaceAll("/+$", "");
        // No conservar una copia adicional de la contraseña de Resend en la memoria.
        this.smtpConfigurado = correo != null && valido(host) && valido(usuarioSmtp)
                && valido(passwordSmtp) && PasswordRecoveryService.emailValido(this.desde);
    }

    /** Se invoca DENTRO de la transacción de compra/pago; no envía hasta el commit. */
    public void publicar(Pedido pedido, Tipo tipo, String motivo) {
        Usuario usuario = pedido.getUsuario();
        if (usuario == null || !PasswordRecoveryService.emailValido(usuario.getCorreo())) {
            log.warn("Pedido {} no dispone de correo válido para notificar.", pedido.getIdPedido());
            return;
        }
        eventos.publishEvent(new Aviso(
                tipo, pedido.getIdPedido(), pedido.getCodigoPedido(), usuario.getCorreo(),
                usuario.getNombres(), pedido.getTotal(), motivo));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alConfirmarTransaccion(Aviso aviso) {
        if (!smtpConfigurado) {
            log.info("Notificación de pedido {} omitida: SMTP sin configurar.", aviso.idPedido());
            return;
        }
        try {
            envios.execute(() -> entregar(aviso));
        } catch (RejectedExecutionException ex) {
            // Sin reintentos automáticos que dupliquen notificaciones.
            log.warn("Cola de correo llena: pedido {} no notificado.", aviso.idPedido());
        }
    }

    /** Accesible desde los tests; el envío real se realiza fuera de la transacción. */
    void entregar(Aviso aviso) {
        enviarCliente(aviso);
        if (aviso.tipo() == Tipo.NUEVO_PEDIDO || aviso.tipo() == Tipo.EVIDENCIA_RECIBIDA)
            enviarAdministradores(aviso);
    }

    private void enviarCliente(Aviso aviso) {
        String asunto = switch (aviso.tipo()) {
            case NUEVO_PEDIDO -> "Recibimos tu pedido";
            case EVIDENCIA_RECIBIDA -> "Recibimos tu comprobante de pago";
            case PAGO_APROBADO -> "Tu pago fue confirmado";
            case PAGO_RECHAZADO -> "Necesitamos que revises tu comprobante";
            case EN_PREPARACION -> "Estamos preparando tu pedido";
            case EN_CAMINO -> "Tu pedido está en camino";
            case FINALIZADO -> "Tu pedido fue finalizado";
            case CANCELADO -> "Tu pedido fue cancelado";
        };
        String mensaje = switch (aviso.tipo()) {
            case NUEVO_PEDIDO ->
                    "Tu pedido fue registrado y está pendiente de pago. En Mis pedidos puedes adjuntar tu comprobante.";
            case EVIDENCIA_RECIBIDA ->
                    "Tu evidencia fue recibida y está en revisión. El pago aún NO está confirmado.";
            case PAGO_APROBADO ->
                    "El administrador verificó tu pago. Ya puedes descargar tu boleta PDF DE PRUEBA desde Mis pedidos (no válida ante SUNAT).";
            case PAGO_RECHAZADO ->
                    "El comprobante fue rechazado. Motivo: " + seguro(aviso.motivo())
                            + ". Puedes adjuntar una nueva evidencia desde Mis pedidos.";
            case EN_PREPARACION -> "Tu pago fue confirmado y estamos preparando los productos.";
            case EN_CAMINO -> "Tu pedido salió para su entrega.";
            case FINALIZADO -> "Tu pedido fue marcado como finalizado.";
            case CANCELADO -> "Tu pedido fue cancelado. Si necesitas ayuda, contacta con la tienda.";
        };
        String texto = "Hola " + seguro(aviso.nombreCliente()) + ",\n\n" + mensaje
                + "\n\nPedido: " + seguro(aviso.codigoPedido())
                + "\nImporte: " + dinero(aviso.total())
                + "\n\nConsulta tus pedidos: " + tiendaUrl + "/mis-pedidos"
                + "\n\nNayeli's Moda";
        enviar(aviso.correoCliente(), "Nayeli's Moda | " + asunto, texto, aviso.idPedido());
    }

    private void enviarAdministradores(Aviso aviso) {
        Set<String> destinatarios = new LinkedHashSet<>();
        if (!adminOverride.isBlank()) {
            Arrays.stream(adminOverride.split(",")).map(String::trim)
                    .filter(PasswordRecoveryService::emailValido)
                    .forEach(destinatarios::add);
        } else {
            try {
                // Sin dirección inventada: cuentas ADMIN activas en MonarcaDB.
                for (Usuario u : usuarios.findByRol_NombreIgnoreCaseAndActivoTrue("ADMIN")) {
                    if (PasswordRecoveryService.emailValido(u.getCorreo()))
                        destinatarios.add(u.getCorreo());
                }
            } catch (RuntimeException ex) {
                log.warn("No se pudieron consultar los correos de administradores para pedido {}.", aviso.idPedido());
            }
        }
        if (destinatarios.isEmpty()) {
            log.warn("Sin administrador destinatario para el pedido {}. Configura MAIL_ADMIN_TO.", aviso.idPedido());
            return;
        }
        String asunto = aviso.tipo() == Tipo.NUEVO_PEDIDO ? "Nuevo pedido pendiente" : "Comprobante pendiente de revisión";
        String descripcion = aviso.tipo() == Tipo.NUEVO_PEDIDO
                ? "Un cliente registró un nuevo pedido pendiente de pago."
                : "Un cliente subió una evidencia de pago que requiere validación manual.";
        String mensaje = descripcion
                + "\n\nPedido: " + seguro(aviso.codigoPedido())
                + "\nCliente: " + seguro(aviso.correoCliente())
                + "\nImporte: " + dinero(aviso.total())
                + "\n\nRevisa el panel: " + tiendaUrl + "/admin"
                + "\nNo apruebes el pago sin comprobar el abono real.";
        for (String destinatario : destinatarios)
            enviar(destinatario, "Nayeli's Moda | " + asunto, mensaje, aviso.idPedido());
    }

    private void enviar(String destinatario, String asunto, String texto, Long pedidoId) {
        try {
            SimpleMailMessage email = new SimpleMailMessage();
            email.setFrom(desde);
            email.setTo(destinatario);
            email.setSubject(asunto);
            email.setText(texto);
            correo.send(email);
        } catch (RuntimeException ex) {
            // No registrar tokens SMTP, datos personales ni contenido del correo.
            log.warn("No se pudo enviar una notificación del pedido {}. Revisa SMTP/Resend.", pedidoId);
        }
    }

    private static boolean valido(String valor) {
        return valor != null && !valor.isBlank();
    }

    private static String dinero(BigDecimal total) {
        return "S/ " + (total == null ? "0.00" : total.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
    }

    private static String seguro(String texto) {
        return texto == null || texto.isBlank() ? "cliente"
                : texto.replace('\r', ' ').replace('\n', ' ').trim();
    }

    @PreDestroy public void cerrar() {
        envios.shutdown();
    }
}
