package com.Monarca.Backend.service;

import com.Monarca.Backend.model.FotoProbador;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProbadorConfiguracionTest {
    @Test
    void construyePeticionDePruebaConResolucionReducidaSinLlamarAlProveedor() throws Exception {
        ProbadorService service = new ProbadorService("", null, null, null);
        FotoProbador foto = new FotoProbador();
        foto.setMimeTipo("image/png");
        foto.setDatos(new byte[]{(byte) 137, 80, 78, 71});

        Method metodo = ProbadorService.class.getDeclaredMethod("multipart",
                String.class, FotoProbador.class, byte[].class, String.class);
        metodo.setAccessible(true);

        byte[] cuerpo = (byte[]) metodo.invoke(service, "boundary",
                foto, new byte[]{1, 2, 3}, "image/png");
        String solicitud = new String(cuerpo, StandardCharsets.ISO_8859_1);
        assertTrue(solicitud.contains("name=\"resolution\"\\r\\n\\r\\n1k\\r\\n"));
        assertTrue(solicitud.contains("name=\"generation_mode\"\\r\\n\\r\\nbalanced\\r\\n"));
    }
}
