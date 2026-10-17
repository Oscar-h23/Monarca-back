package com.Monarca.Backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.util.Iterator;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * API Developer de TryOnCloud (no confundir con Platform API).
 * Sin reintentos automáticos: cada generación puede consumir un crédito.
 */
@Service
public class TryOnCloudProbadorService {
    private static final URI API = URI.create("https://www.tryoncloud.com/api/v1/generate");
    private final String key;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public TryOnCloudProbadorService(@Value("${monarca.tryon.tryoncloud.key:}") String key) {
        this.key = key;
    }

    public byte[] generar(byte[] persona, byte[] prenda) {
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Configura TRYONCLOUD_API_KEY en Monarca-back para utilizar las 10 pruebas gratuitas.");
        }
        try {
            String boundary = "monarca-cloud-" + UUID.randomUUID();
            byte[] payload = crearMultipart(boundary, persona, prenda);
            HttpRequest request = HttpRequest.newBuilder(API)
                    .timeout(Duration.ofSeconds(135))
                    .header("X-API-KEY", key)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                    .build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                byte[] imagen = response.body();
                if (imagen.length == 0 || imagen.length > 12_000_000
                        || PerfilService.detectarMime(imagen) == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "TryOnCloud no devolvió una imagen válida. Revisa tu saldo antes de repetir.");
                }
                return imagen;
            }
            throw errorProveedor(response.statusCode());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "La generación con TryOnCloud fue interrumpida. Revisa tus créditos antes de repetir.", e);
        } catch (IOException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No se pudo completar la comunicación con TryOnCloud. Revisa tu saldo antes de repetir.", e);
        }
    }

    static byte[] crearMultipart(String boundary, byte[] persona, byte[] prenda) throws IOException {
        // Convierte WebP a JPEG localmente, sin consumir créditos ni modificar el perfil.
        byte[] personaCompatible = prepararImagen(persona);
        byte[] prendaCompatible = prepararImagen(prenda);
        String personaMime = validarImagen(personaCompatible);
        String prendaMime = validarImagen(prendaCompatible);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        agregarArchivo(out, boundary, "person_image", personaMime, personaCompatible);
        agregarArchivo(out, boundary, "garment_image", prendaMime, prendaCompatible);
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static String validarImagen(byte[] datos) {
        if (datos == null || datos.length == 0 || datos.length > 15_000_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La imagen está vacía o excede el límite de 15 MB.");
        }
        String mime = PerfilService.detectarMime(datos);
        if (!"image/jpeg".equals(mime) && !"image/png".equals(mime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Formato de imagen no admitido. Se aceptan JPG, PNG y WebP.");
        }
        return mime;
    }

    /**
     * Convierte WebP a JPEG en memoria antes del envío al proveedor.
     * Limita las dimensiones del origen y reduce el resultado a un máximo de 2048 px.
     */
    static byte[] prepararImagen(byte[] datos) {
        if (datos == null || datos.length == 0 || datos.length > 15_000_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La imagen está vacía o excede el límite de 15 MB.");
        }
        String mime = PerfilService.detectarMime(datos);
        if ("image/png".equals(mime) || "image/jpeg".equals(mime)) return datos;
        if (!"image/webp".equals(mime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La imagen debe ser JPG, PNG o WebP.");
        }

        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(datos))) {
            if (input == null) throw new IOException("No se puede leer el archivo.");
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(input);
            if (!lectores.hasNext()) throw new IOException("Decodificador WebP no disponible.");
            ImageReader lector = lectores.next();
            BufferedImage origen;
            try {
                lector.setInput(input, true, true);
                int w = lector.getWidth(0), h = lector.getHeight(0);
                if (w < 1 || h < 1 || w > 8192 || h > 8192 || (long) w * h > 20_000_000L) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "La imagen WebP tiene dimensiones demasiado grandes.");
                }
                origen = lector.read(0);
            } finally {
                lector.dispose();
            }
            if (origen == null) throw new IOException("WebP sin imagen.");
            int w = origen.getWidth(), h = origen.getHeight();
            double escala = Math.min(1.0, 2048.0 / Math.max(w, h));
            int ancho = Math.max(1, (int) Math.round(w * escala));
            int alto = Math.max(1, (int) Math.round(h * escala));
            BufferedImage jpg = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
            Graphics2D grafico = jpg.createGraphics();
            try {
                grafico.setColor(Color.WHITE);
                grafico.fillRect(0, 0, ancho, alto);
                grafico.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                grafico.drawImage(origen, 0, 0, ancho, alto, null);
            } finally {
                grafico.dispose();
            }
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            if (!ImageIO.write(jpg, "jpeg", salida)) throw new IOException("Codificador JPEG no disponible.");
            byte[] convertido = salida.toByteArray();
            if (convertido.length > 15_000_000)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Imagen convertida mayor que 15 MB.");
            return convertido;
        } catch (IOException | RuntimeException e) {
            if (e instanceof ResponseStatusException ex) throw ex;
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se pudo convertir la imagen WebP. Usa otra foto JPG o PNG.");
        }
    }

    private static void agregarArchivo(ByteArrayOutputStream out, String boundary,
                                     String campo, String mime, byte[] datos) throws IOException {
        String extension = "image/png".equals(mime) ? "png" : "jpg";
        out.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + campo + "\"; filename=\"" + campo + "." + extension + "\"\r\n"
                + "Content-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(datos);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static ResponseStatusException errorProveedor(int status) {
        if (status == 400 || status == 413 || status == 422)
            return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "TryOnCloud rechazó una imagen o su contenido (HTTP " + status + ").");
        if (status == 401 || status == 403)
            return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "TryOnCloud rechazó la API key. Comprueba que sea una clave Developer tk_dev_v1_.");
        if (status == 402)
            return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Se agotaron las generaciones gratuitas de TryOnCloud.");
        if (status == 429)
            return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "TryOnCloud limitó temporalmente las solicitudes.");
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "TryOnCloud devolvió HTTP " + status + ". Revisa tu saldo antes de reintentar.");
    }
}
