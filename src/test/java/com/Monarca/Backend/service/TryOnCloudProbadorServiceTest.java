package com.Monarca.Backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class TryOnCloudProbadorServiceTest {
    private static final byte[] PNG={(byte) 137,80,78,71,13,10,26,10,0,0,0,0};

    @Test
    void exigeClaveSinConsumirLlamadas() {
        var service = new TryOnCloudProbadorService("");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.generar(PNG, PNG));
        assertEquals(503, error.getStatusCode().value());
    }

    @Test
    void construyeMultipartCorrectoConDosImagenes() throws Exception {
        byte[] form = TryOnCloudProbadorService.crearMultipart("limite", PNG, PNG);
        String body = new String(form, StandardCharsets.ISO_8859_1);
        assertTrue(body.contains("name=\"person_image\"; filename=\"person_image.png\""));
        assertTrue(body.contains("name=\"garment_image\"; filename=\"garment_image.png\""));
        assertTrue(body.endsWith("--limite--\r\n"));
    }

    @Test
    void convierteWebpAntesDeContactarLaApi() throws Exception {
        byte[] webp = java.util.Base64.getDecoder().decode(
                "UklGRhwAAABXRUJQVlA4TA8AAAAvB8ABEAcQ/Y8CBiKi/wEA");
        byte[] jpeg = TryOnCloudProbadorService.prepararImagen(webp);
        assertEquals("image/jpeg", PerfilService.detectarMime(jpeg));
        byte[] form = TryOnCloudProbadorService.crearMultipart("limite", webp, webp);
        String body = new String(form, StandardCharsets.ISO_8859_1);
        assertTrue(body.contains("name=\"person_image\"; filename=\"person_image.jpg\""));
        assertTrue(body.contains("name=\"garment_image\"; filename=\"garment_image.jpg\""));
    }

    @Test
    void rechazaWebpCorruptoSinEnviarDatos() {
        byte[] webp="RIFF1234WEBP".getBytes(StandardCharsets.US_ASCII);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> TryOnCloudProbadorService.crearMultipart("limite", webp, PNG));
        assertEquals(400, error.getStatusCode().value());
    }
}
