package com.Monarca.Backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class AgalazProbadorServiceTest {
    @Test
    void requiereTokenAntesDeEnviarFotos() {
        AgalazProbadorService service = new AgalazProbadorService("");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.generar(new byte[]{1}, new byte[]{2}));
        assertEquals(503, error.getStatusCode().value());
    }

    @Test
    void extraeImagenBase64SinNecesidadDeAPIExterna() {
        byte[] png = new byte[]{(byte) 137,80,78,71,13,10,26,10,0,0,0,0};
        String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
        assertArrayEquals(png, AgalazProbadorService.decodificarImagen(dataUrl));
    }

    @Test
    void rechazaDatosQueNoSonImagen() {
        assertThrows(ResponseStatusException.class,
                () -> AgalazProbadorService.decodificarImagen("data:text/html;base64,PGh0bWw+"));
    }
}
