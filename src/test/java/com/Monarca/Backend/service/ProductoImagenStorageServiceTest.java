package com.Monarca.Backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class ProductoImagenStorageServiceTest {
    private static byte[] imagen(String formato) throws Exception {
        var image=new BufferedImage(140,170,
                "png".equals(formato)?BufferedImage.TYPE_INT_ARGB:BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image,formato,out));
        return out.toByteArray();
    }

    @Test
    void procesaJpgYPngConExtensionCorrespondiente() throws Exception {
        var png=ProductoImagenStorageService.normalizarImagen(imagen("png"));
        assertEquals("png",png.extension());
        assertEquals("image/png",PerfilService.detectarMime(png.datos()));
        var jpg=ProductoImagenStorageService.normalizarImagen(imagen("jpeg"));
        assertEquals("jpg",jpg.extension());
        assertEquals("image/jpeg",PerfilService.detectarMime(jpg.datos()));
    }

    @Test
    void rechazaArchivoWebpAntesDeAlmacenarlo() {
        var ex=assertThrows(ResponseStatusException.class,()->
          ProductoImagenStorageService.normalizarImagen("RIFF1234WEBP".getBytes()));
        assertEquals(400,ex.getStatusCode().value());
    }

    @Test
    void sinSecretoRechazaLaSubidaAntesDeContactoExterno() throws Exception {
        var storage=new ProductoImagenStorageService(
                "https://ldjuyocsuvvvbllgdclv.supabase.co","monarca-productos","");
        var archivo=new MockMultipartFile("archivo","prenda.png","image/png",imagen("png"));
        var ex=assertThrows(ResponseStatusException.class,()->storage.subir(archivo));
        assertEquals(503,ex.getStatusCode().value());
    }
}
