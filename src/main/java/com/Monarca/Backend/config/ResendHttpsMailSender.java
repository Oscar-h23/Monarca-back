package com.Monarca.Backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessagePreparator;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Correo transaccional por la API HTTPS de Resend. Railway Hobby bloquea SMTP.
 * Mantiene la interfaz JavaMailSender de recuperación y notificaciones.
 */
public final class ResendHttpsMailSender extends JavaMailSenderImpl {
    private final String apiKey;
    private final HttpClient client;
    private final URI endpoint;
    private final ObjectMapper mapper;

    public ResendHttpsMailSender(String apiKey) {
        this(apiKey, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                URI.create("https://api.resend.com/emails"), new ObjectMapper());
    }

    // Solo para pruebas: se inyecta un cliente falso, nunca un servidor SMTP.
    ResendHttpsMailSender(String apiKey, HttpClient client, URI endpoint, ObjectMapper mapper) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.client = client;
        this.endpoint = endpoint;
        this.mapper = mapper;
    }

    @Override public void send(SimpleMailMessage message) {
        if (message == null) throw new MailSendException("Mensaje ausente");
        send(new SimpleMailMessage[]{message});
    }

    @Override public void send(SimpleMailMessage... messages) {
        if (apiKey.isBlank()) throw new MailSendException("Resend HTTPS: falta la API key");
        if (messages == null) throw new MailSendException("Mensajes ausentes");
        for (SimpleMailMessage message : messages) enviar(message);
    }

    private void enviar(SimpleMailMessage message) {
        if (message == null || message.getFrom() == null || message.getFrom().isBlank()
                || message.getTo() == null || message.getTo().length == 0
                || message.getSubject() == null || message.getText() == null)
            throw new MailSendException("Mensaje de correo incompleto");
        try {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("from", message.getFrom());
            cuerpo.put("to", message.getTo());
            cuerpo.put("subject", message.getSubject());
            cuerpo.put("text", message.getText());
            if (message.getCc() != null && message.getCc().length > 0) cuerpo.put("cc", message.getCc());
            if (message.getBcc() != null && message.getBcc().length > 0) cuerpo.put("bcc", message.getBcc());
            if (message.getReplyTo() != null) cuerpo.put("reply_to", message.getReplyTo());

            HttpRequest solicitud = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(cuerpo), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> respuesta = client.send(solicitud, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() < 200 || respuesta.statusCode() >= 300)
                throw new MailSendException("Resend HTTPS API HTTP " + respuesta.statusCode());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MailSendException("Conexión HTTPS con Resend interrumpida", ex);
        } catch (IOException ex) {
            throw new MailSendException("Error de conexión con la API HTTPS de Resend", ex);
        }
    }

    /** No permitir un fallback SMTP accidental cuando se seleccionó HTTPS. */
    @Override public void send(MimeMessage... messages) {
        throw new MailSendException("El transporte HTTPS admite mensajes de texto simples");
    }
    @Override public void send(MimeMessagePreparator... messages) {
        throw new MailSendException("El transporte HTTPS admite mensajes de texto simples");
    }
}
