package com.Monarca.Backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Adaptador de Agalaz. Una llamada síncrona por generación autorizada.
 * Nunca registra ni devuelve en logs la fotografía o la clave de la API.
 */
@Service
public class AgalazProbadorService {
    private static final URI API = URI.create("https://agalaz.com/api/v1/tryon");
    private static final int MAX_RESULT = 12_000_000;
    private final String key;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public AgalazProbadorService(@Value("${monarca.tryon.agalaz.key:}") String key) {
        this.key = key;
    }

    public byte[] generar(byte[] persona, byte[] prenda) {
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Agalaz aún no está configurado. Agrega AGALAZ_API_KEY en Monarca-back.");
        }
        if (persona == null || prenda == null || persona.length == 0 || prenda.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta una de las fotografías.");
        }

        try {
            ObjectNode payload = json.createObjectNode();
            payload.put("userImage", Base64.getEncoder().encodeToString(persona));
            payload.put("clothingImage", Base64.getEncoder().encodeToString(prenda));

            HttpRequest request = HttpRequest.newBuilder(API)
                    .timeout(Duration.ofSeconds(135))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload),
                            StandardCharsets.UTF_8))
                    .build();

            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            switch (response.statusCode()) {
                case 200:
                    if (response.body().length > 20_000_000)
                        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                "Agalaz devolvió una respuesta demasiado grande.");
                    JsonNode result = json.readTree(response.body());
                    return decodificarImagen(result.path("image").asText(""));
                case 400:
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Agalaz rechazó las imágenes. Comprueba que tengan al menos 500 × 500 píxeles y un formato válido.");
                case 401:
                case 403:
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Agalaz rechazó la API key o el dominio registrado. Revisa AGALAZ_API_KEY y tu dominio de pruebas.");
                case 402:
                    throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                            "Se terminó el periodo gratuito o la cuota de pruebas de Agalaz.");
                case 429:
                    throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                            "Agalaz alcanzó el límite temporal de solicitudes.");
                default:
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "Agalaz devolvió HTTP " + response.statusCode() + ". Revisa el estado del servicio.");
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "La solicitud a Agalaz fue interrumpida.", e);
        } catch (IOException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No se pudo procesar la respuesta de Agalaz.", e);
        }
    }

    static byte[] decodificarImagen(String imageDataUrl) {
        String prefijo;
        if (imageDataUrl.startsWith("data:image/png;base64,"))
            prefijo = "data:image/png;base64,";
        else if (imageDataUrl.startsWith("data:image/jpeg;base64,"))
            prefijo = "data:image/jpeg;base64,";
        else if (imageDataUrl.startsWith("data:image/webp;base64,"))
            prefijo = "data:image/webp;base64,";
        else
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Agalaz no devolvió una imagen compatible con el probador.");

        String codificado = imageDataUrl.substring(prefijo.length());
        if (codificado.length() > (MAX_RESULT * 4L / 3L + 32))
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "La imagen generada por Agalaz es demasiado grande.");
        try {
            byte[] imagen = Base64.getDecoder().decode(codificado);
            if (imagen.length == 0 || imagen.length > MAX_RESULT
                    || PerfilService.detectarMime(imagen) == null)
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "La imagen generada por Agalaz no tiene un formato válido.");
            return imagen;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Agalaz devolvió datos de imagen incorrectos.");
        }
    }
}
