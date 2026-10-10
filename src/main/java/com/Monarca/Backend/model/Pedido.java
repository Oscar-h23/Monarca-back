package com.Monarca.Backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "pedidos", schema = "monarca")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pedido")
    private Long idPedido;

    @Column(
            name = "codigo_pedido",
            length = 40,
            unique = true
    )
    private String codigoPedido;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(
            name = "id_usuario",
            nullable = false
    )
    private Usuario usuario;

    @Column(
            name = "estado",
            nullable = false,
            length = 30
    )
    private String estado = "PENDIENTE_PAGO";

    @Column(
            name = "subtotal",
            nullable = false,
            precision = 10,
            scale = 2
    )
    private BigDecimal subtotal;

    @Column(
            name = "descuento",
            nullable = false,
            precision = 10,
            scale = 2
    )
    private BigDecimal descuento =
            BigDecimal.ZERO;

    @Column(
            name = "costo_envio",
            nullable = false,
            precision = 10,
            scale = 2
    )
    private BigDecimal costoEnvio =
            BigDecimal.ZERO;

    @Column(
            name = "total",
            nullable = false,
            precision = 10,
            scale = 2
    )
    private BigDecimal total;

    @Column(
            name = "observacion",
            length = 500
    )
    private String observacion;

    @Column(
            name = "fecha_pedido",
            nullable = false
    )
    private OffsetDateTime fechaPedido;

    @Column(
            name = "fecha_actualizacion",
            nullable = false
    )
    private OffsetDateTime fechaActualizacion;


    @PrePersist
    public void prePersist() {

        OffsetDateTime ahora =
                OffsetDateTime.now();

        if (estado == null) {
            estado = "PENDIENTE_PAGO";
        }

        if (descuento == null) {
            descuento = BigDecimal.ZERO;
        }

        if (costoEnvio == null) {
            costoEnvio = BigDecimal.ZERO;
        }

        if (fechaPedido == null) {
            fechaPedido = ahora;
        }

        if (fechaActualizacion == null) {
            fechaActualizacion = ahora;
        }
    }


    @PreUpdate
    public void preUpdate() {
        fechaActualizacion =
                OffsetDateTime.now();
    }
}