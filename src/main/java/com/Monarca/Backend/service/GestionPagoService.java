package com.Monarca.Backend.service;

import com.Monarca.Backend.model.DetallePedido;
import com.Monarca.Backend.model.Pago;
import com.Monarca.Backend.model.Pedido;
import com.Monarca.Backend.model.VarianteProducto;
import com.Monarca.Backend.repository.DetallePedidoRepository;
import com.Monarca.Backend.repository.PagoRepository;
import com.Monarca.Backend.repository.PedidoRepository;
import com.Monarca.Backend.repository.VarianteProductoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Pago manual independiente de la logística. La evidencia no verifica un abono
 * por sí sola: solo un administrador puede aprobarla.
 */
@Service
public class GestionPagoService {
    private final PedidoRepository pedidos;
    private final PagoRepository pagos;
    private final DetallePedidoRepository detalles;
    private final VarianteProductoRepository variantes;
    private final EvidenciaPagoStorageService storage;
    private final BoletaPruebaPdfService pdf;
    private final NotificacionesPedidoService notificaciones;

    public GestionPagoService(PedidoRepository pedidos,
                             PagoRepository pagos,
                             DetallePedidoRepository detalles,
                             VarianteProductoRepository variantes,
                             EvidenciaPagoStorageService storage,
                             BoletaPruebaPdfService pdf,
                             NotificacionesPedidoService notificaciones) {
        this.pedidos = pedidos;
        this.pagos = pagos;
        this.detalles = detalles;
        this.variantes = variantes;
        this.storage = storage;
        this.pdf = pdf;
        this.notificaciones = notificaciones;
    }

    @Transactional
    public void enviarEvidencia(Long idPedido, String correoJwt, MultipartFile archivo) {
        Pedido pedido = pedidoBloqueado(idPedido);
        verificarPropiedad(pedido, correoJwt);
        if ("CANCELADO".equals(pedido.getEstado()) || "FINALIZADO".equals(pedido.getEstado()))
            throw conflicto("El pedido ya no admite comprobantes.");
        Pago pago = pagoBloqueado(idPedido);
        if (!List.of("PENDIENTE", "RECHAZADO").contains(pago.getEstado()))
            throw conflicto("El pago ya está en revisión o confirmado.");
        String rutaPrivada = storage.subir(idPedido, archivo);
        pago.setComprobanteUrl(rutaPrivada); // RUTA, nunca URL pública ni binario en SQL
        pago.setEstado("EN_REVISION");
        pago.setObservacion(null);
        pago.setFechaPago(OffsetDateTime.now());
        pago.setFechaConfirmacion(null);
        pagos.save(pago);
        pedido.setEstado("PENDIENTE_CONFIRMACION");
        pedidos.save(pedido);
        notificaciones.publicar(pedido, NotificacionesPedidoService.Tipo.EVIDENCIA_RECIBIDA, null);
    }

    @Transactional(readOnly = true)
    public byte[] evidenciaAdmin(Long idPedido) {
        Pago pago = pagoExistente(idPedido);
        if (pago.getComprobanteUrl() == null || pago.getComprobanteUrl().isBlank())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El cliente no envió evidencia.");
        return storage.descargar(pago.getComprobanteUrl());
    }

    @Transactional
    public void revisarPago(Long idPedido, String accion, String motivo) {
        Pedido pedido = pedidoBloqueado(idPedido);
        if ("CANCELADO".equals(pedido.getEstado()))
            throw conflicto("No se puede revisar un pedido cancelado.");
        Pago pago = pagoBloqueado(idPedido);
        if (!"EN_REVISION".equals(pago.getEstado()) ||
                pago.getComprobanteUrl() == null || pago.getComprobanteUrl().isBlank())
            throw conflicto("Primero debe existir una evidencia pendiente de revisión.");

        if ("APROBAR".equals(accion)) {
            pago.setEstado("PAGADO");
            pago.setObservacion(null);
            pago.setFechaConfirmacion(OffsetDateTime.now());
            pedido.setEstado("CONFIRMADO");
        } else if ("RECHAZAR".equals(accion)) {
            if (motivo == null || motivo.isBlank() || motivo.trim().length() > 300)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Para rechazar un pago registra un motivo de 1 a 300 caracteres.");
            pago.setEstado("RECHAZADO");
            pago.setObservacion(motivo.trim());
            pago.setFechaConfirmacion(OffsetDateTime.now());
            pedido.setEstado("PENDIENTE_PAGO");
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Acción de pago no permitida.");
        }
        pagos.save(pago);
        pedidos.save(pedido);
        notificaciones.publicar(pedido,
                "APROBAR".equals(accion)
                        ? NotificacionesPedidoService.Tipo.PAGO_APROBADO
                        : NotificacionesPedidoService.Tipo.PAGO_RECHAZADO,
                "RECHAZAR".equals(accion) ? pago.getObservacion() : null);
    }

    /**
     * Excepción temporal de pruebas: el administrador confirma el pedido sin
     * acreditar un ingreso real. Se marca para excluirlo de los reportes de ventas.
     * No envía el correo de pago aprobado, pues no existe abono verificado.
     */
    @Transactional
    public void aprobarPedidoPruebaSinEvidencia(Long idPedido) {
        Pedido pedido = pedidoBloqueado(idPedido);
        if ("CANCELADO".equals(pedido.getEstado()) || "FINALIZADO".equals(pedido.getEstado()))
            throw conflicto("Este pedido no puede confirmarse como prueba.");
        Pago pago = pagoBloqueado(idPedido);
        if (!List.of("PENDIENTE", "RECHAZADO").contains(pago.getEstado())
                || (pago.getComprobanteUrl() != null && !pago.getComprobanteUrl().isBlank()))
            throw conflicto("La aprobación de prueba es solo para pedidos sin comprobante y pendientes.");
        pago.setEstado("PAGADO");
        pago.setObservacion("APROBACION_PRUEBA_SIN_PAGO");
        pago.setFechaConfirmacion(OffsetDateTime.now());
        pedido.setEstado("CONFIRMADO");
        pagos.save(pago);
        pedidos.save(pedido);
    }

    @Transactional
    public void actualizarEstado(Long idPedido, String nuevo) {
        Pedido pedido = pedidoBloqueado(idPedido);
        Pago pago = pagoBloqueado(idPedido);
        String actual = pedido.getEstado();

        if ("CANCELADO".equals(nuevo)) {
            if ("CANCELADO".equals(actual) || "FINALIZADO".equals(actual))
                throw conflicto("El pedido no puede cancelarse desde su estado actual.");
            if ("PAGADO".equals(pago.getEstado()))
                throw conflicto("No se puede cancelar un pedido pagado sin gestionar antes su devolución.");
            // Devuelve existencias exactamente una vez, dentro de la misma transacción.
            List<DetallePedido> lineas = detalles.findByPedido_IdPedidoInOrderByIdDetalleAsc(List.of(idPedido));
            for (DetallePedido linea : lineas.stream()
                    .sorted(Comparator.comparing(d -> d.getVariante().getIdVariante())).toList()) {
                VarianteProducto variante = variantes.findByIdForUpdate(linea.getVariante().getIdVariante())
                        .orElseThrow(() -> conflicto("No se encontró una variante para devolver stock."));
                variante.setStock(Math.addExact(variante.getStock(), linea.getCantidad()));
                variantes.save(variante);
            }
            pedido.setEstado("CANCELADO");
            pago.setEstado("CANCELADO");
            pagos.save(pago);
            pedidos.save(pedido);
            notificaciones.publicar(pedido, NotificacionesPedidoService.Tipo.CANCELADO, null);
            return;
        }

        Map<String, String> siguientes = Map.of(
                "CONFIRMADO", "EN_PREPARACION",
                "EN_PREPARACION", "EN_CAMINO",
                "EN_CAMINO", "FINALIZADO");
        if (!"PAGADO".equals(pago.getEstado()) || !nuevo.equals(siguientes.get(actual)))
            throw conflicto("Transición inválida. Verifica el pago y sigue el orden preparación → camino → finalizado.");
        pedido.setEstado(nuevo);
        pedidos.save(pedido);
        notificaciones.publicar(pedido, NotificacionesPedidoService.Tipo.valueOf(nuevo), null);
    }

    @Transactional(readOnly = true)
    public byte[] boletaDePrueba(Long idPedido, String correoJwt, boolean administrador) {
        Pedido pedido = pedidos.findById(idPedido).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado."));
        if (!administrador) verificarPropiedad(pedido, correoJwt);
        Pago pago = pagoExistente(idPedido);
        if (!"PAGADO".equals(pago.getEstado()) || "CANCELADO".equals(pedido.getEstado()))
            throw conflicto("La boleta de prueba solo se habilita después de confirmar el pago.");
        return pdf.generar(pedido, detalles.findByPedido_IdPedidoInOrderByIdDetalleAsc(List.of(idPedido)));
    }

    private Pedido pedidoBloqueado(Long id) {
        return pedidos.bloquearPorId(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado."));
    }
    private Pago pagoBloqueado(Long id) {
        return pagos.bloquearPorPedido(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pago no encontrado."));
    }
    private Pago pagoExistente(Long id) {
        return pagos.findFirstByPedido_IdPedidoOrderByIdPagoDesc(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pago no encontrado."));
    }
    private void verificarPropiedad(Pedido pedido, String correoJwt) {
        if (correoJwt == null || !pedido.getUsuario().getCorreo().equalsIgnoreCase(correoJwt))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado.");
    }
    private ResponseStatusException conflicto(String mensaje) {
        return new ResponseStatusException(HttpStatus.CONFLICT, mensaje);
    }
}
