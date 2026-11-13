package com.Monarca.Backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ResendHttpsMailSenderTest {
    private final HttpClient client = mock(HttpClient.class);

    private ResendHttpsMailSender sender(String key) {
        return new ResendHttpsMailSender(key, client, URI.create("https://api.resend.com/emails"),
                new ObjectMapper());
    }

    private SimpleMailMessage email() {
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom("Nayeli's Moda <avisos@example.test>");
        m.setTo("cliente@example.test");
        m.setSubject("Prueba de compra");
        m.setText("Tu pedido fue creado correctamente");
        return m;
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(int status) {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(status);
        return r;
    }

    @Test void envioUsaPuertoHttpsYTokenSoloEnHeader() throws Exception {
        HttpResponse<String> respuesta = response(200);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(respuesta);
        sender("re_example_test_only").send(email());
        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        var request = captor.getValue();
        assertEquals("https", request.uri().getScheme());
        assertEquals("api.resend.com", request.uri().getHost());
        assertEquals(-1, request.uri().getPort());
        assertEquals("POST", request.method());
        assertEquals("/emails", request.uri().getPath());
        assertEquals("Bearer re_example_test_only",
                request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("application/json",
                request.headers().firstValue("Content-Type").orElseThrow());
    }

    @Test void erroresHttpNoExponenCredenciales() throws Exception {
        HttpResponse<String> respuesta = response(401);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(respuesta);
        var ex = assertThrows(MailSendException.class, () -> sender("re_example_test_only").send(email()));
        assertEquals("Resend HTTPS API HTTP 401", ex.getMessage());
        assertFalse(ex.getMessage().contains("re_example_test_only"));
    }

    @Test void errorDeRedNoSeConvierteEnExito() throws Exception {
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("fallo de red de prueba"));
        var ex = assertThrows(MailSendException.class, () -> sender("re_example_test_only").send(email()));
        assertTrue(ex.getMessage().contains("conexión"));
    }

    @Test void bloqueaElTransporteSmtpYCredencialesVacias() {
        assertThrows(MailSendException.class,
                () -> sender("re_example_test_only").send(new MimeMessage[0]));
        assertThrows(MailSendException.class, () -> sender("").send(email()));
        verifyNoInteractions(client);
    }
}
