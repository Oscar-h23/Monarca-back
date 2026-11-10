package com.Monarca.Backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.UUID;

/**
 * Solo el backend, tras validar el rol administrador, almacena fotografías del catálogo.
 * Nunca entregar SUPABASE_SERVICE_ROLE_KEY al frontend ni registrar el token en logs.
 */
@Service
public class ProductoImagenStorageService {
    static final int MAX_BYTES=5_000_000;
    static final long MAX_PIXELES=20_000_000L;
    private final String baseUrl;
    private final String bucket;
    private final String apiKey;
    private final HttpClient http=HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public ProductoImagenStorageService(
            @Value("${monarca.storage.url:https://ldjuyocsuvvvbllgdclv.supabase.co}") String baseUrl,
            @Value("${monarca.storage.bucket:monarca-productos}") String bucket,
            @Value("${monarca.storage.service-role-key:}") String apiKey) {
        this.baseUrl=baseUrl.endsWith("/")?baseUrl.substring(0,baseUrl.length()-1):baseUrl;
        this.bucket=bucket;
        this.apiKey=apiKey;
    }

    public String subir(MultipartFile archivo) {
        if(archivo==null || archivo.isEmpty() || archivo.getSize()>MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona una imagen JPG o PNG de hasta 5 MB.");
        }
        if(apiKey==null || apiKey.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Falta configurar SUPABASE_SERVICE_ROLE_KEY en Monarca-back para subir imágenes.");
        try {
            ImagenValida imagen=normalizarImagen(archivo.getBytes());
            String ruta="productos/"+UUID.randomUUID()+"."+imagen.extension();
            URI destino=URI.create(baseUrl+"/storage/v1/object/"+bucket+"/"+ruta);
            HttpRequest request=HttpRequest.newBuilder(destino)
                    .timeout(Duration.ofSeconds(45))
                    .header("Authorization","Bearer "+apiKey)
                    .header("apikey",apiKey)
                    .header("Content-Type",imagen.mime())
                    .header("Cache-Control","max-age=3600")
                    .header("x-upsert","false")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(imagen.datos()))
                    .build();
            HttpResponse<Void> response=http.send(request,HttpResponse.BodyHandlers.discarding());
            if(response.statusCode()!=200 && response.statusCode()!=201){
                if(response.statusCode()==401 || response.statusCode()==403)
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Supabase Storage rechazó la credencial del backend. Verifica SUPABASE_SERVICE_ROLE_KEY.");
                if(response.statusCode()==413)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "La imagen excede el límite de Supabase Storage.");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Supabase Storage no pudo guardar la imagen (HTTP "+response.statusCode()+").");
            }
            return baseUrl+"/storage/v1/object/public/"+bucket+"/"+ruta;
        } catch (ResponseStatusException e){throw e;}
        catch (InterruptedException e){Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Subida interrumpida.",e);
        } catch(IOException | IllegalArgumentException e){
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Error al procesar o subir la fotografía.",e);
        }
    }

    /** Elimina metadata EXIF y reduce dimensiones; evita archivos disfrazados o dañados. */
    static ImagenValida normalizarImagen(byte[] datos) {
        if(datos==null || datos.length==0 || datos.length>MAX_BYTES)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Imagen vacía o mayor de 5 MB.");
        String mime=PerfilService.detectarMime(datos);
        if(!"image/jpeg".equals(mime) && !"image/png".equals(mime))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Sube un archivo JPG o PNG. Las imágenes WebP se convierten a JPG en el navegador.");
        try(ImageInputStream input=ImageIO.createImageInputStream(new ByteArrayInputStream(datos))) {
            if(input==null)throw new IOException("Archivo sin imagen.");
            Iterator<ImageReader> lectores=ImageIO.getImageReaders(input);
            if(!lectores.hasNext())throw new IOException("Imagen no reconocida.");
            ImageReader lector=lectores.next();
            BufferedImage original;
            try {
                lector.setInput(input, true, true);
                int w=lector.getWidth(0), h=lector.getHeight(0);
                if(w<1||h<1||w>8192||h>8192||(long)w*h>MAX_PIXELES)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "La imagen excede las dimensiones máximas (20 MP).");
                original=lector.read(0);
            } finally {lector.dispose();}
            if(original==null)throw new IOException("No se puede leer la imagen.");
            int w=original.getWidth(), h=original.getHeight();
            double escala=Math.min(1.0,2048.0/Math.max(w,h));
            int ancho=Math.max(1,(int)Math.round(w*escala));
            int alto=Math.max(1,(int)Math.round(h*escala));
            BufferedImage limpia=new BufferedImage(ancho,alto,
                    "image/png".equals(mime)?BufferedImage.TYPE_INT_ARGB:BufferedImage.TYPE_INT_RGB);
            Graphics2D g=limpia.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                if("image/jpeg".equals(mime)){
                    g.setColor(Color.WHITE);
                    g.fillRect(0,0,ancho,alto);
                }
                g.drawImage(original,0,0,ancho,alto,null);
            } finally {g.dispose();}
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            String formato="image/png".equals(mime)?"png":"jpeg";
            if(!ImageIO.write(limpia,formato,output))throw new IOException("No se pudo codificar.");
            byte[] limpio=output.toByteArray();
            if(limpio.length>MAX_BYTES)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La imagen procesada excede 5 MB. Prueba una imagen de menor resolución.");
            return new ImagenValida(limpio,mime,"image/png".equals(mime)?"png":"jpg");
        } catch(ResponseStatusException e){throw e;}
        catch(IOException | RuntimeException e){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La imagen no se pudo leer. Selecciona una fotografía JPG o PNG válida.");
        }
    }

    record ImagenValida(byte[] datos,String mime,String extension) {}
}
