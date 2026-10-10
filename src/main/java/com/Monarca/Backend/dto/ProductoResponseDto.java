package com.Monarca.Backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class ProductoResponseDto {

    private Long idProducto;

    private String nombre;
    private String slug;
    private String descripcion;
    private String marca;

    private BigDecimal precioBase;

    private Boolean destacado;

    private String categoria;

    private String imagen;

    private List<VarianteProductoResponseDto> variantes;
}