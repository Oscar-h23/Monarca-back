package com.Monarca.Backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class EvidenciaPagoStorageServiceTest {
    @Test void reescribeSinMetadataYConviertePNGEnJPG() throws Exception {
        BufferedImage origen = new BufferedImage(15,15, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream datos = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(origen,"png",datos));
        byte[] limpia = EvidenciaPagoStorageService.limpiar(datos.toByteArray());
        assertEquals((byte)0xff,limpia[0]);
        assertEquals((byte)0xd8,limpia[1]);
        assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(limpia)));
    }
    @Test void rechazaGifAunqueSeaImagen() throws Exception {
        BufferedImage gif = new BufferedImage(10,10,BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream datos = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(gif,"gif",datos));
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> EvidenciaPagoStorageService.limpiar(datos.toByteArray())).getStatusCode());
    }
    @Test void rechazaArchivoSinImagenYArchivoVacio() {
        assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ResponseStatusException.class,
                () -> EvidenciaPagoStorageService.limpiar("texto cualquiera".getBytes())).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ResponseStatusException.class,
                () -> EvidenciaPagoStorageService.limpiar(new byte[0])).getStatusCode());
    }
}
