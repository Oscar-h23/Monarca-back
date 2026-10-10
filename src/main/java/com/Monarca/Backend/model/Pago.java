package com.Monarca.Backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "pagos", schema = "monarca")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pago")
    private Long idPago;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "id_pedido",
            nullable = false
    )
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(
            name = "id_metodo_pago",
            nullable = false
    )
    private MetodoPago metodoPago;

    @Column(
            name = "monto",
            nullable = false,
            precision = 10,
            scale = 2
    )
    private BigDecimal monto;

    @Column(
            name = "estado",
            nullable = false,
            length = 30
    )
    private String estado = "PENDIENTE";

    @Column(
            name = "numero_operacion",
            length = 100
    )
    private String numeroOperacion;

    @Column(
            name = "comprobante_url",
            columnDefinition = "TEXT"
    )
    private String comprobanteUrl;

    @Column(
            name = "proveedor_pago",
            length = 100
    )
    private String proveedorPago;

    @Column(
            name = "referencia_externa",
            length = 150
    )
    private String referenciaExterna;

    @Column(
            name = "observacion",
            length = 300
    )
    private String observacion;

    @Column(
            name = "fecha_creacion",
            nullable = false
    )
    private OffsetDateTime fechaCreacion;

    @Column(name = "fecha_pago")
    private OffsetDateTime fechaPago;

    @Column(name = "fecha_confirmacion")
    private OffsetDateTime fechaConfirmacion;


    @PrePersist
    public void prePersist() {

        if (estado == null) {
            estado = "PENDIENTE";
        }

        if (fechaCreacion == null) {
            fechaCreacion =
                    OffsetDateTime.now();
        }
    }
}