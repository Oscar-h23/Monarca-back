package com.Monarca.Backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "metodos_pago",
        schema = "monarca"
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MetodoPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_metodo_pago")
    private Long idMetodoPago;

    @Column(
            name = "codigo",
            nullable = false,
            unique = true,
            length = 30
    )
    private String codigo;

    @Column(
            name = "nombre",
            nullable = false,
            unique = true,
            length = 100
    )
    private String nombre;

    @Column(
            name = "descripcion",
            length = 250
    )
    private String descripcion;

    @Column(
            name = "requiere_comprobante",
            nullable = false
    )
    private Boolean requiereComprobante = false;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @Column(name = "telefono_yape", length = 20) private String telefonoYape;
    @Column(name = "qr_url", columnDefinition = "TEXT") private String qrUrl;
    @Column(name = "titular", length = 120) private String titular;
    @Column(name = "banco", length = 120) private String banco;
    @Column(name = "numero_cuenta", length = 40) private String numeroCuenta;
    @Column(name = "cci", length = 30) private String cci;
}