package com.Monarca.Backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
public class VarianteProductoResponseDto {

    private Long idVariante;
    private String sku;
    private String talla;
    private String color;
    private String colorHex;
    private BigDecimal precio;
    private Integer stock;
}