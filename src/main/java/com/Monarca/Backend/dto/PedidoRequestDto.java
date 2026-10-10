package com.Monarca.Backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class PedidoRequestDto {

    private Long idUsuario;

    // Se mantiene por compatibilidad.
    // El backend recalcula el total real.
    private BigDecimal total;

    private Long idMetodoPago;
    // Datos de entrega conservados en la observación existente del pedido.
    private String observacion;

    // Puede utilizarse después para RECOJO_TIENDA
    private Long idTiendaRetiro;

    private List<ItemCarritoDto> items;
}