package com.Monarca.Backend.dto;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

/** Una variante con talla, color, precio y stock propios. */
@Getter
@Setter
public class VarianteProductoInputDto {
    private String talla;
    private String color;
    private String colorHex;
    private BigDecimal precio;
    private Integer stock;
    private Integer stockMinimo;
}
