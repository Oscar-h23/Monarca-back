package com.Monarca.Backend.service;

import com.Monarca.Backend.model.*;
import com.Monarca.Backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GestionPagoServiceTest {
    @Mock PedidoRepository pedidos;
    @Mock PagoRepository pagos;
    @Mock DetallePedidoRepository detalles;
    @Mock VarianteProductoRepository variantes;
    @Mock EvidenciaPagoStorageService storage;
    @Mock BoletaPruebaPdfService pdf;
    @InjectMocks GestionPagoService service;

    private Pedido pedido;
    private Pago pago;

    @BeforeEach void preparar() {
        Usuario cliente = new Usuario();
        cliente.setIdUsuario(4L);
        cliente.setCorreo("propietaria@example.test");
        pedido = new Pedido();
        pedido.setIdPedido(12L);
        pedido.setUsuario(cliente);
        pedido.setEstado("PENDIENTE_PAGO");
        pago = new Pago();
        pago.setIdPago(1L);
        pago.setPedido(pedido);
        pago.setEstado("PENDIENTE");
    }

    @Test void otroClienteNoPuedeSubirComprobante() {
        when(pedidos.bloquearPorId(12L)).thenReturn(Optional.of(pedido));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.enviarEvidencia(12L, "intruso@example.test", new MockMultipartFile(
                        "archivo","imagen.jpg","image/jpeg",new byte[] {1})));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        verifyNoInteractions(storage, pagos);
    }

    @Test void subirEvidenciaSoloEnviaARevisionNoApruebaPago() {
        when(pedidos.bloquearPorId(12L)).thenReturn(Optional.of(pedido));
        when(pagos.bloquearPorPedido(12L)).thenReturn(Optional.of(pago));
        MockMultipartFile archivo = new MockMultipartFile("archivo","prueba.png","image/png",new byte[] {1,2});
        when(storage.subir(12L,archivo)).thenReturn("pedidos/12/comprobante.jpg");
        service.enviarEvidencia(12L, "propietaria@example.test", archivo);
        assertEquals("EN_REVISION", pago.getEstado());
        assertEquals("PENDIENTE_CONFIRMACION", pedido.getEstado());
        assertEquals("pedidos/12/comprobante.jpg", pago.getComprobanteUrl());
        assertNull(pago.getFechaConfirmacion());
        verify(pagos).save(pago);
    }

    @Test void administradorConfirmaSoloSiHayEvidenciaPendiente() {
        pago.setEstado("EN_REVISION");
        pago.setComprobanteUrl("pedidos/12/comprobante.jpg");
        when(pedidos.bloquearPorId(12L)).thenReturn(Optional.of(pedido));
        when(pagos.bloquearPorPedido(12L)).thenReturn(Optional.of(pago));
        service.revisarPago(12L,"APROBAR",null);
        assertEquals("PAGADO",pago.getEstado());
        assertEquals("CONFIRMADO",pedido.getEstado());
        assertNotNull(pago.getFechaConfirmacion());
    }

    @Test void rechazoExigeMotivoYPermiteReenvio() {
        pago.setEstado("EN_REVISION");
        pago.setComprobanteUrl("pedidos/12/comprobante.jpg");
        when(pedidos.bloquearPorId(12L)).thenReturn(Optional.of(pedido));
        when(pagos.bloquearPorPedido(12L)).thenReturn(Optional.of(pago));
        assertThrows(ResponseStatusException.class,() -> service.revisarPago(12L,"RECHAZAR"," "));
        verify(pagos, never()).save(any());
        service.revisarPago(12L,"RECHAZAR","No se encontró el abono");
        assertEquals("RECHAZADO",pago.getEstado());
        assertEquals("PENDIENTE_PAGO",pedido.getEstado());
        assertEquals("No se encontró el abono",pago.getObservacion());
    }

    @Test void noPermiteBoletaSinPagoConfirmado() {
        when(pedidos.findById(12L)).thenReturn(Optional.of(pedido));
        when(pagos.findFirstByPedido_IdPedidoOrderByIdPagoDesc(12L)).thenReturn(Optional.of(pago));
        assertThrows(ResponseStatusException.class,() ->
                service.boletaDePrueba(12L,"propietaria@example.test",false));
        verifyNoInteractions(pdf, detalles);
    }

    @Test void noPermiteAvanzarAEnvioSinConfirmacion() {
        when(pedidos.bloquearPorId(12L)).thenReturn(Optional.of(pedido));
        when(pagos.bloquearPorPedido(12L)).thenReturn(Optional.of(pago));
        assertThrows(ResponseStatusException.class,() -> service.actualizarEstado(12L,"EN_CAMINO"));
        assertEquals("PENDIENTE_PAGO",pedido.getEstado());
    }

    @Test void cancelaPedidoNoPagadoYDevuelveStockUnaSolaVez() {
        Producto producto = new Producto();
        producto.setIdProducto(7L);
        VarianteProducto variante = new VarianteProducto();
        variante.setIdVariante(3L);
        variante.setProducto(producto);
        variante.setStock(10);
        DetallePedido linea = new DetallePedido();
        linea.setVariante(variante);
        linea.setCantidad(2);
        when(pedidos.bloquearPorId(12L)).thenReturn(Optional.of(pedido));
        when(pagos.bloquearPorPedido(12L)).thenReturn(Optional.of(pago));
        when(detalles.findByPedido_IdPedidoInOrderByIdDetalleAsc(List.of(12L)))
                .thenReturn(List.of(linea));
        when(variantes.findByIdForUpdate(3L)).thenReturn(Optional.of(variante));

        service.actualizarEstado(12L,"CANCELADO");
        assertEquals(12,variante.getStock());
        assertEquals("CANCELADO",pedido.getEstado());
        assertEquals("CANCELADO",pago.getEstado());
        assertThrows(ResponseStatusException.class,() -> service.actualizarEstado(12L,"CANCELADO"));
        verify(variantes,times(1)).save(any());
    }
}
