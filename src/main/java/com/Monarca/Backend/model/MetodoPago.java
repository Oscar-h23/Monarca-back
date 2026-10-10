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
}