package com.Monarca.Backend.service;

import com.Monarca.Backend.dto.PedidoClienteResponseDto;
import com.Monarca.Backend.model.DetallePedido;
import com.Monarca.Backend.model.Pedido;
import com.Monarca.Backend.model.Pago;
import com.Monarca.Backend.model.Usuario;
import com.Monarca.Backend.repository.DetallePedidoRepository;
import com.Monarca.Backend.repository.PedidoRepository;
import com.Monarca.Backend.repository.PagoRepository;
import com.Monarca.Backend.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Consulta solo compras del usuario asociado al JWT; nunca acepta un id de navegador. */
@Service
public class PedidoConsultaClienteService {
    private final UsuarioRepository usuarios;
    private final PedidoRepository pedidos;
    private final DetallePedidoRepository detalles;
    private final PagoRepository pagos;

    public PedidoConsultaClienteService(UsuarioRepository usuarios,
                                        PedidoRepository pedidos,
                                        DetallePedidoRepository detalles,
                                        PagoRepository pagos) {
        this.usuarios = usuarios;
        this.pedidos = pedidos;
        this.detalles = detalles;
        this.pagos = pagos;
    }

    @Transactional(readOnly = true)
    public List<PedidoClienteResponseDto> listarPorCorreo(String correoAutenticado) {
        if (correoAutenticado == null || correoAutenticado.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión no válida");
        }
        Usuario usuario = usuarios.findByCorreoIgnoreCase(correoAutenticado)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión no válida"));

        List<Pedido> compras = pedidos.findByUsuario_IdUsuarioOrderByFechaPedidoDescIdPedidoDesc(
                usuario.getIdUsuario());
        if (compras.isEmpty()) return List.of();

        List<Long> ids = compras.stream().map(Pedido::getIdPedido).toList();
        Map<Long, Pago> pagosPorPedido = new HashMap<>();
        for (Pago pago : pagos.findByPedido_IdPedidoIn(ids)) {
            pagosPorPedido.merge(pago.getPedido().getIdPedido(), pago,
                    (anterior, siguiente) -> anterior.getIdPago() > siguiente.getIdPago() ? anterior : siguiente);
        }
        Map<Long, List<PedidoClienteResponseDto.ProductoPedido>> porPedido = new HashMap<>();
        for (DetallePedido detalle : detalles.findByPedido_IdPedidoInOrderByIdDetalleAsc(ids)) {
            PedidoClienteResponseDto.ProductoPedido item = new PedidoClienteResponseDto.ProductoPedido(
                    detalle.getIdDetalle(), detalle.getNombreProducto(), detalle.getSku(),
                    detalle.getTalla(), detalle.getColor(), detalle.getCantidad(),
                    detalle.getPrecioUnitario(), detalle.getSubtotal());
            porPedido.computeIfAbsent(detalle.getPedido().getIdPedido(),
                    ignored -> new ArrayList<>()).add(item);
        }

        return compras.stream().map(p -> {
            Pago pago = pagosPorPedido.get(p.getIdPedido());
            String estadoPago = pago == null ? "PENDIENTE" : pago.getEstado();
            boolean evidencia = pago != null && pago.getComprobanteUrl() != null
                    && !pago.getComprobanteUrl().isBlank();
            return new PedidoClienteResponseDto(
                p.getIdPedido(), p.getCodigoPedido(), p.getEstado(), p.getFechaPedido(),
                p.getSubtotal(), p.getDescuento(), p.getCostoEnvio(), p.getTotal(),
                List.copyOf(porPedido.getOrDefault(p.getIdPedido(), List.of())),
                estadoPago, evidencia,
                "RECHAZADO".equals(estadoPago) ? pago.getObservacion() : null,
                "PAGADO".equals(estadoPago) && !"CANCELADO".equals(p.getEstado()));
        }).toList();
    }
}
