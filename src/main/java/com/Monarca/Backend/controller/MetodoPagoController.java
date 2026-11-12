package com.Monarca.Backend.controller;

import com.Monarca.Backend.model.MetodoPago;
import com.Monarca.Backend.repository.MetodoPagoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Locale;

/** Configuración de cobro manual, pública para compradores y editable solo por ADMIN. */
@RestController
@RequestMapping("/api")
public class MetodoPagoController {
    private final MetodoPagoRepository repository;
    public MetodoPagoController(MetodoPagoRepository repository) { this.repository = repository; }

    public record VistaMetodoPago(Long idMetodoPago, String codigo, String nombre,
            String descripcion, boolean requiereComprobante, boolean activo, boolean configurado,
            String telefonoYape, String qrUrl, String titular, String banco, String numeroCuenta, String cci) {}
    public record DatosPago(Boolean activo, String telefonoYape, String qrUrl,
            String titular, String banco, String numeroCuenta, String cci) {}

    @GetMapping("/metodos-pago")
    public List<VistaMetodoPago> listar() {
        return repository.findAll().stream().filter(m -> Boolean.TRUE.equals(m.getActivo()))
                .map(MetodoPagoController::vista).toList();
    }
    @GetMapping("/admin/metodos-pago")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public List<VistaMetodoPago> listarAdmin() {
        return repository.findAll().stream().map(MetodoPagoController::vista).toList();
    }
    @PutMapping("/admin/metodos-pago/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Transactional
    public VistaMetodoPago configurar(@PathVariable Long id, @RequestBody DatosPago datos) {
        if (datos == null || datos.activo() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica si el método está activo.");
        MetodoPago metodo = repository.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Método no encontrado."));
        String codigo = metodo.getCodigo() == null ? "" : metodo.getCodigo().toUpperCase(Locale.ROOT);
        if ("YAPE".equals(codigo)) {
            String celular = normalizar(datos.telefonoYape(), 20);
            String qr = normalizar(datos.qrUrl(), 400);
            if (celular != null && !celular.matches("9[0-9]{8}"))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Yape necesita 9 dígitos y empezar por 9.");
            if (qr != null && !qr.matches(
                    "https://ldjuyocsuvvvbllgdclv\\.supabase\\.co/storage/v1/object/public/monarca-productos/productos/[0-9a-fA-F-]{36}\\.(jpg|png)"))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Carga el QR desde el panel antes de guardar.");
            metodo.setTelefonoYape(celular);
            metodo.setQrUrl(qr);
            metodo.setTitular(normalizar(datos.titular(), 120));
        } else if ("TRANSFERENCIA".equals(codigo)) {
            String cuenta = normalizar(datos.numeroCuenta(), 40);
            String cci = normalizar(datos.cci(), 30);
            if (cuenta != null && !cuenta.matches("[0-9-]{5,40}"))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cuenta debe contener dígitos o guiones (5-40).");
            if (cci != null && !cci.matches("[0-9]{20}"))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El CCI debe tener exactamente 20 dígitos.");
            metodo.setBanco(normalizar(datos.banco(), 120));
            metodo.setNumeroCuenta(cuenta);
            metodo.setCci(cci);
            metodo.setTitular(normalizar(datos.titular(), 120));
        }
        metodo.setActivo(datos.activo());
        return vista(repository.save(metodo));
    }

    private static VistaMetodoPago vista(MetodoPago m) {
        String codigo = m.getCodigo() == null ? "" : m.getCodigo().toUpperCase(Locale.ROOT);
        boolean configurado = switch (codigo) {
            case "YAPE" -> m.getTelefonoYape() != null && m.getTelefonoYape().matches("9[0-9]{8}");
            case "TRANSFERENCIA" -> m.getBanco() != null && !m.getBanco().isBlank()
                    && m.getNumeroCuenta() != null && !m.getNumeroCuenta().isBlank()
                    && m.getCci() != null && m.getCci().matches("[0-9]{20}");
            default -> true;
        };
        return new VistaMetodoPago(m.getIdMetodoPago(),m.getCodigo(),m.getNombre(),
                m.getDescripcion(),Boolean.TRUE.equals(m.getRequiereComprobante()),
                Boolean.TRUE.equals(m.getActivo()),configurado,m.getTelefonoYape(),
                m.getQrUrl(),m.getTitular(),m.getBanco(),m.getNumeroCuenta(),m.getCci());
    }

    private static String normalizar(String entrada, int max) {
        if (entrada == null || entrada.isBlank()) return null;
        String valor = entrada.trim();
        if (valor.length() > max || valor.contains("\r") || valor.contains("\n"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Campo excede el límite permitido.");
        return valor;
    }
}
