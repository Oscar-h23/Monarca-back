package com.Monarca.Backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class ProductoDto {

    private Long idVariante;
    private String nombre;
    private String categoria;

    private String descripcion;
    private String marca;

    private String talla;
    private String color;
    private String colorHex;

    private BigDecimal precio;
    private BigDecimal precioBase;

    private Integer stock;
    private Integer stockMinimo;

    private String img;

    /** Alta múltiple: opcional para conservar compatibilidad con clientes anteriores. */
    private List<VarianteProductoInputDto> variantes;
}