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
    void bloqueaWebpAntesDeContactarLaApi() {
        byte[] webp="RIFF1234WEBP".getBytes(StandardCharsets.US_ASCII);
        assertThrows(ResponseStatusException.class,
                () -> TryOnCloudProbadorService.crearMultipart("limite", webp, PNG));
    }
}
