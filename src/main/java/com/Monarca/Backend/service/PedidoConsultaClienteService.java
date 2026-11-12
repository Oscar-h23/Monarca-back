package com.Monarca.Backend.service;

import com.Monarca.Backend.dto.PedidoClienteResponseDto;
import com.Monarca.Backend.model.DetallePedido;
import com.Monarca.Backend.model.Pedido;
import com.Monarca.Backend.model.Usuario;
import com.Monarca.Backend.repository.DetallePedidoRepository;
import com.Monarca.Backend.repository.PedidoRepository;
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

    public PedidoConsultaClienteService(UsuarioRepository usuarios,
                                        PedidoRepository pedidos,
                                        DetallePedidoRepository detalles) {
        this.usuarios = usuarios;
        this.pedidos = pedidos;
        this.detalles = detalles;
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
        Map<Long, List<PedidoClienteResponseDto.ProductoPedido>> porPedido = new HashMap<>();
        for (DetallePedido detalle : detalles.findByPedido_IdPedidoInOrderByIdDetalleAsc(ids)) {
            PedidoClienteResponseDto.ProductoPedido item = new PedidoClienteResponseDto.ProductoPedido(
                    detalle.getIdDetalle(), detalle.getNombreProducto(), detalle.getSku(),
                    detalle.getTalla(), detalle.getColor(), detalle.getCantidad(),
                    detalle.getPrecioUnitario(), detalle.getSubtotal());
            porPedido.computeIfAbsent(detalle.getPedido().getIdPedido(),
                    ignored -> new ArrayList<>()).add(item);
        }

        return compras.stream().map(p -> new PedidoClienteResponseDto(
                p.getIdPedido(), p.getCodigoPedido(), p.getEstado(), p.getFechaPedido(),
                p.getSubtotal(), p.getDescuento(), p.getCostoEnvio(), p.getTotal(),
                List.copyOf(porPedido.getOrDefault(p.getIdPedido(), List.of())))).toList();
    }
}
