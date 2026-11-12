package com.Monarca.Backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.UUID;

/** Guarda justificantes en un bucket PRIVADO. El cliente jamás recibe la service-role key. */
@Service
public class EvidenciaPagoStorageService {
    static final long MAX_BYTES = 5_000_000;
    private final String baseUrl;
    private final String bucket;
    private final String serviceKey;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public EvidenciaPagoStorageService(
            @Value("${monarca.storage.url}") String baseUrl,
            @Value("${monarca.storage.evidencias-bucket}") String bucket,
            @Value("${monarca.storage.service-role-key:}") String serviceKey) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.bucket = bucket;
        this.serviceKey = serviceKey;
    }

    public String subir(Long idPedido, MultipartFile archivo) {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Configura SUPABASE_SERVICE_ROLE_KEY en el backend para recibir comprobantes.");
        }
        if (archivo == null || archivo.isEmpty() || archivo.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La evidencia debe ser una imagen JPG, PNG o WebP de máximo 5 MB.");
        }
        byte[] limpio;
        try { limpio = limpiar(archivo.getBytes()); }
        catch (IOException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Imagen no válida.", ex); }
        String ruta = "pedidos/" + idPedido + "/" + UUID.randomUUID() + ".jpg";
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(baseUrl + "/storage/v1/object/" + bucket + "/" + ruta))
                .timeout(Duration.ofSeconds(40))
                .header("Authorization", "Bearer " + serviceKey)
                .header("apikey", serviceKey)
                .header("Content-Type", "image/jpeg")
                .header("x-upsert", "false")
                .POST(HttpRequest.BodyPublishers.ofByteArray(limpio)).build();
        try {
            HttpResponse<Void> respuesta = http.send(request, HttpResponse.BodyHandlers.discarding());
            if (respuesta.statusCode() == 200 || respuesta.statusCode() == 201) return ruta;
            if (respuesta.statusCode() == 401 || respuesta.statusCode() == 403)
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Supabase rechazó las credenciales privadas del backend.");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No se pudo guardar la evidencia en Storage (HTTP " + respuesta.statusCode() + ").");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Carga interrumpida.", ex);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo contactar Storage.", ex);
        }
    }

    public byte[] descargar(String ruta) {
        if (ruta == null || !ruta.matches("pedidos/[0-9]+/[0-9a-fA-F-]{36}\\.jpg"))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidencia no disponible.");
        if (serviceKey == null || serviceKey.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Storage no configurado.");
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        baseUrl + "/storage/v1/object/authenticated/" + bucket + "/" + ruta))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + serviceKey)
                .header("apikey", serviceKey).GET().build();
        try {
            HttpResponse<byte[]> respuesta = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (respuesta.statusCode() == 200 && respuesta.body().length <= MAX_BYTES) return respuesta.body();
            if (respuesta.statusCode() == 404) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidencia no encontrada.");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo consultar la evidencia privada.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Descarga interrumpida.", ex);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al descargar la evidencia.", ex);
        }
    }

    /** Decodifica la imagen realmente, limita píxeles y reescribe como JPG sin EXIF. */
    static byte[] limpiar(byte[] datos) {
        if (datos == null || datos.length == 0 || datos.length > MAX_BYTES)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tamaño de evidencia inválido.");
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(datos))) {
            if (stream == null) throw new IOException("No es imagen.");
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(stream);
            if (!lectores.hasNext()) throw new IOException("Formato de imagen no admitido.");
            ImageReader lector = lectores.next();
            BufferedImage original;
            try {
                lector.setInput(stream, true, true);
                int ancho = lector.getWidth(0), alto = lector.getHeight(0);
                if (ancho < 1 || alto < 1 || ancho > 8192 || alto > 8192 ||
                        ((long) ancho * alto) > 20_000_000L)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La imagen excede el límite de 20 MP.");
                original = lector.read(0);
            } finally { lector.dispose(); }
            if (original == null) throw new IOException("Imagen ilegible.");
            double escala = Math.min(1.0, 2048.0 / Math.max(original.getWidth(), original.getHeight()));
            int w = Math.max(1, (int) Math.round(original.getWidth() * escala));
            int h = Math.max(1, (int) Math.round(original.getHeight() * escala));
            BufferedImage jpg = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = jpg.createGraphics();
            try {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, w, h);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.drawImage(original, 0, 0, w, h, null);
            } finally { g.dispose(); }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(jpg, "jpg", out)) throw new IOException("No se pudo generar JPG.");
            if (out.size() > MAX_BYTES) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La imagen procesada supera 5 MB.");
            return out.toByteArray();
        } catch (ResponseStatusException ex) { throw ex; }
        catch (IOException | RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El archivo no es una imagen válida JPG, PNG o WebP.", ex);
        }
    }
}
