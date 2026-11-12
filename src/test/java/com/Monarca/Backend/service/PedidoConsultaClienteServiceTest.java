package com.Monarca.Backend.service;

import com.Monarca.Backend.dto.PedidoClienteResponseDto;
import com.Monarca.Backend.model.DetallePedido;
import com.Monarca.Backend.model.Pedido;
import com.Monarca.Backend.repository.PagoRepository;
import com.Monarca.Backend.model.Usuario;
import com.Monarca.Backend.repository.DetallePedidoRepository;
import com.Monarca.Backend.repository.PedidoRepository;
import com.Monarca.Backend.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoConsultaClienteServiceTest {
    @Mock UsuarioRepository usuarios;
    @Mock PedidoRepository pedidos;
    @Mock DetallePedidoRepository detalles;
    @Mock PagoRepository pagos;
    @InjectMocks PedidoConsultaClienteService service;

    @Test void consultaSoloPedidosDelPropietarioConDetalleYOrden() {
        Usuario cliente = new Usuario();
        cliente.setIdUsuario(15L);
        when(usuarios.findByCorreoIgnoreCase("cliente@example.test")).thenReturn(Optional.of(cliente));

        Pedido reciente = pedido(20L, "PED-20", "PENDIENTE_PAGO", 2026, 11, 12);
        Pedido anterior = pedido(10L, "PED-10", "ENTREGADO", 2026, 9, 10);
        when(pedidos.findByUsuario_IdUsuarioOrderByFechaPedidoDescIdPedidoDesc(15L))
                .thenReturn(List.of(reciente, anterior));

        DetallePedido item = new DetallePedido();
        item.setIdDetalle(30L);
        item.setPedido(anterior);
        item.setNombreProducto("Blusa crema");
        item.setSku("BLU-M");
        item.setTalla("M");
        item.setColor("Crema");
        item.setCantidad(2);
        item.setPrecioUnitario(new BigDecimal("39.90"));
        item.setSubtotal(new BigDecimal("79.80"));
        when(detalles.findByPedido_IdPedidoInOrderByIdDetalleAsc(List.of(20L, 10L)))
                .thenReturn(List.of(item));

        List<PedidoClienteResponseDto> respuesta = service.listarPorCorreo("cliente@example.test");
        assertEquals(List.of("PED-20", "PED-10"),
                respuesta.stream().map(PedidoClienteResponseDto::codigoPedido).toList());
        assertEquals("PENDIENTE_PAGO", respuesta.get(0).estado());
        assertTrue(respuesta.get(0).productos().isEmpty());
        assertEquals("ENTREGADO", respuesta.get(1).estado());
        assertEquals("Blusa crema", respuesta.get(1).productos().getFirst().nombreProducto());
        assertEquals(new BigDecimal("79.80"), respuesta.get(1).productos().getFirst().subtotal());
        verify(pedidos).findByUsuario_IdUsuarioOrderByFechaPedidoDescIdPedidoDesc(15L);
        verify(pedidos, never()).findAll();
    }

    @Test void cuentaSinPedidosDevuelveListaVacia() {
        Usuario cliente = new Usuario();
        cliente.setIdUsuario(5L);
        when(usuarios.findByCorreoIgnoreCase("cliente@example.test")).thenReturn(Optional.of(cliente));
        when(pedidos.findByUsuario_IdUsuarioOrderByFechaPedidoDescIdPedidoDesc(5L))
                .thenReturn(List.of());
        assertTrue(service.listarPorCorreo("cliente@example.test").isEmpty());
        verifyNoInteractions(detalles);
    }

    @Test void usuarioAusenteNoPuedeConsultarPedidos() {
        when(usuarios.findByCorreoIgnoreCase("otro@example.test")).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.listarPorCorreo("otro@example.test"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verifyNoInteractions(pedidos, detalles);
    }

    private Pedido pedido(long id, String codigo, String estado, int ano, int mes, int dia) {
        Pedido pedido = new Pedido();
        pedido.setIdPedido(id);
        pedido.setCodigoPedido(codigo);
        pedido.setEstado(estado);
        pedido.setFechaPedido(OffsetDateTime.parse(
                String.format("%04d-%02d-%02dT11:00:00-05:00", ano, mes, dia)));
        pedido.setSubtotal(new BigDecimal("79.80"));
        pedido.setDescuento(BigDecimal.ZERO);
        pedido.setCostoEnvio(BigDecimal.ZERO);
        pedido.setTotal(new BigDecimal("79.80"));
        return pedido;
    }
}
