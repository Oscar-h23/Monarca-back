package com.Monarca.Backend.service;

import com.Monarca.Backend.dto.*;
import com.Monarca.Backend.model.*;
import com.Monarca.Backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoVariantesTest {
    @Mock PedidoRepository pedidos;
    @Mock DetallePedidoRepository detalles;
    @Mock VarianteProductoRepository variantes;
    @Mock UsuarioRepository usuarios;
    @Mock MetodoPagoRepository metodos;
    @Mock PagoRepository pagos;
    @InjectMocks PedidoService service;
    VarianteProducto variante;
    PedidoRequestDto solicitud;

    @BeforeEach void preparar() {
        Producto producto = new Producto(); producto.setIdProducto(1L); producto.setNombre("Top Ana");
        producto.setPrecioBase(new BigDecimal("59.00"));
        variante = new VarianteProducto(); variante.setProducto(producto); variante.setIdVariante(10L);
        variante.setSku("ANA-S-NEG"); variante.setTalla("S"); variante.setColor("Negro");
        variante.setPrecio(new BigDecimal("65.00")); variante.setStock(5);
        solicitud = new PedidoRequestDto(); solicitud.setIdUsuario(2L); solicitud.setIdMetodoPago(3L);
        solicitud.setTotal(new BigDecimal("0.01")); solicitud.setItems(List.of(item(10L, 2)));
        when(usuarios.findById(2L)).thenReturn(Optional.of(new Usuario()));
        when(metodos.findById(3L)).thenReturn(Optional.of(new MetodoPago()));
    }
    private ItemCarritoDto item(Long id, int cantidad) {
        ItemCarritoDto item = new ItemCarritoDto(); item.setIdVariante(id); item.setCantidad(cantidad); return item;
    }
    @Test void recalculaPrecioDescuentaVarianteYConservaSnapshot() {
        when(variantes.findByIdForUpdate(10L)).thenReturn(Optional.of(variante));
        when(pedidos.save(any())).thenAnswer(i -> i.getArgument(0));
        Pedido pedido = service.procesarCompra(solicitud);
        assertEquals(new BigDecimal("130.00"), pedido.getTotal());
        assertEquals(3, variante.getStock());
        ArgumentCaptor<DetallePedido> captura = ArgumentCaptor.forClass(DetallePedido.class);
        verify(detalles).save(captura.capture());
        DetallePedido detalle = captura.getValue();
        assertAll(() -> assertEquals("Top Ana", detalle.getNombreProducto()),
                () -> assertEquals("ANA-S-NEG", detalle.getSku()), () -> assertEquals("S", detalle.getTalla()),
                () -> assertEquals("Negro", detalle.getColor()),
                () -> assertEquals(new BigDecimal("65.00"), detalle.getPrecioUnitario()),
                () -> assertEquals(new BigDecimal("130.00"), detalle.getSubtotal()));
        verify(pagos).save(argThat(p -> p.getMonto().equals(pedido.getTotal())));
    }
    @Test void rechazaStockInsuficienteSinCrearPedido() {
        solicitud.setItems(List.of(item(10L,6)));
        when(variantes.findByIdForUpdate(10L)).thenReturn(Optional.of(variante));
        assertThrows(RuntimeException.class, () -> service.procesarCompra(solicitud));
        assertEquals(5, variante.getStock()); verifyNoInteractions(pedidos, detalles, pagos);
    }
    @Test void rechazaProductoInactivo() {
        variante.getProducto().setActivo(false);
        when(variantes.findByIdForUpdate(10L)).thenReturn(Optional.of(variante));
        assertThrows(RuntimeException.class, () -> service.procesarCompra(solicitud));
        verify(variantes, never()).save(any());
    }
    @Test void exigeIdVariante() {
        solicitud.setItems(List.of(item(null,1)));
        assertThrows(IllegalArgumentException.class, () -> service.procesarCompra(solicitud));
        verifyNoInteractions(variantes, pedidos, detalles, pagos);
    }
    @Test void rechazaCantidadesNoPositivas() {
        solicitud.setItems(List.of(item(10L,0)));
        assertThrows(RuntimeException.class, () -> service.procesarCompra(solicitud));
        verifyNoInteractions(variantes, pedidos, detalles, pagos);
    }
    @Test void lineasRepetidasNoPermitenSuperarStock() {
        solicitud.setItems(List.of(item(10L,3), item(10L,3)));
        when(variantes.findByIdForUpdate(10L)).thenReturn(Optional.of(variante));
        assertThrows(RuntimeException.class, () -> service.procesarCompra(solicitud));
        verifyNoInteractions(pedidos, detalles, pagos);
        // El rollback real de JPA requiere la prueba de integración contra PostgreSQL.
    }
}
