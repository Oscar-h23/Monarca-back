package com.Monarca.Backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Datos públicos del pedido para su propietario; sin información de otros clientes. */
public record PedidoClienteResponseDto(
        Long idPedido,
        String codigoPedido,
        String estado,
        OffsetDateTime fechaPedido,
        BigDecimal subtotal,
        BigDecimal descuento,
        BigDecimal costoEnvio,
        BigDecimal total,
        List<ProductoPedido> productos,
        String estadoPago,
        String codigoMetodoPago,
        boolean evidenciaEnviada,
        String motivoRechazo,
        boolean boletaDisponible
) {
    /** Snapshot de lo adquirido, que no cambia si se modifica el catálogo. */
    public record ProductoPedido(
            Long idDetalle,
            String nombreProducto,
            String sku,
            String talla,
            String color,
            Integer cantidad,
            BigDecimal precioUnitario,
            BigDecimal subtotal
    ) {}
}
