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
    private BigDecimal costoCompra;

    private Integer stock;
    private Integer stockMinimo;

    // Medidas de la variante seleccionada al editar un producto.
    private BigDecimal pechoCm;
    private BigDecimal cinturaCm;
    private BigDecimal caderaCm;
    private BigDecimal largoCm;

    private String img;

    /** Alta múltiple: opcional para conservar compatibilidad con clientes anteriores. */
    private List<VarianteProductoInputDto> variantes;
}