package com.Monarca.Backend.controller;

import com.Monarca.Backend.service.GestionPagoService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** Endpoints de prueba para pagos manuales; la acreditación es siempre humana. */
@RestController
public class GestionPagoController {
    private final GestionPagoService service;

    public GestionPagoController(GestionPagoService service) {
        this.service = service;
    }

    /**
     * Respuesta legible para el formulario; no expone claves ni detalles privados
     * de conexión a Supabase. Se aplica únicamente a los endpoints de este controlador.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> errorDeOperacion(ResponseStatusException error) {
        String mensaje = error.getStatusCode().value() == 503
                ? "El almacenamiento de comprobantes no está disponible. La tienda debe revisar la configuración privada de Supabase Storage."
                : error.getReason() != null ? error.getReason() : "No se pudo completar la operación.";
        return ResponseEntity.status(error.getStatusCode())
                .cacheControl(CacheControl.noStore())
                .body(Map.of("mensaje", mensaje));
    }

    @PostMapping(value="/api/pedidos/{id}/evidencia", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> subir(@PathVariable Long id,
                                     @RequestParam("archivo") MultipartFile archivo,
                                     Authentication auth) {
        if (auth == null || !auth.isAuthenticated())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión.");
        service.enviarEvidencia(id, auth.getName(), archivo);
        return Map.of("estadoPago", "EN_REVISION", "mensaje", "Evidencia recibida. El pago no se aprueba automáticamente.");
    }

    @GetMapping(value="/api/admin/pedidos/{id}/evidencia", produces=MediaType.IMAGE_JPEG_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<byte[]> verEvidencia(@PathVariable Long id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.IMAGE_JPEG)
                .body(service.evidenciaAdmin(id));
    }

    public record DecisionPago(String accion, String motivo) {}

    @PutMapping("/api/admin/pedidos/{id}/pago")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Map<String,String> revisar(@PathVariable Long id, @RequestBody DecisionPago decision) {
        if (decision == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decisión requerida.");
        service.revisarPago(id, decision.accion(), decision.motivo());
        return Map.of("mensaje", "Revisión de pago registrada.");
    }

    @PutMapping("/api/admin/pedidos/{id}/confirmar-pago-manual")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Map<String,String> confirmarPagoManual(@PathVariable Long id) {
        service.confirmarPagoManualSinComprobante(id);
        return Map.of("mensaje", "Pago confirmado manualmente sin comprobante adjunto.");
    }

    public record EstadoPedido(String estado) {}

    @PutMapping("/api/admin/pedidos/{id}/estado")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Map<String,String> estado(@PathVariable Long id, @RequestBody EstadoPedido estado) {
        if (estado == null || estado.estado() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado requerido.");
        service.actualizarEstado(id, estado.estado());
        return Map.of("mensaje", "Estado del pedido actualizado.");
    }

    @GetMapping(value="/api/pedidos/{id}/boleta-prueba", produces=MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> boleta(@PathVariable Long id, Authentication auth) {
        if (auth == null || !auth.isAuthenticated())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión.");
        boolean admin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        byte[] archivo = service.boletaDePrueba(id, auth.getName(), admin);
        String nombre = "boleta-prueba-pedido-" + id + ".pdf";
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(nombre).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(archivo);
    }
}
