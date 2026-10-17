package com.Monarca.Backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
        // TryOnCloud documenta JPG/PNG. Rechaza WEBP antes de llamar a la API y gastar créditos.
        String personaMime = validarImagen(persona);
        String prendaMime = validarImagen(prenda);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        agregarArchivo(out, boundary, "person_image", personaMime, persona);
        agregarArchivo(out, boundary, "garment_image", prendaMime, prenda);
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
                    "TryOnCloud admite imágenes JPG o PNG. Cambia la foto WebP antes de probar.");
        }
        return mime;
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
