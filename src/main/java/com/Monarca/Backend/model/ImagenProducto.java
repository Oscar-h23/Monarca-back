package com.Monarca.Backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "imagenes_producto", schema = "monarca")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ImagenProducto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_imagen")
    private Long idImagen;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_variante")
    private VarianteProducto variante;

    @Column(name = "url", nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(name = "texto_alternativo", length = 200)
    private String textoAlternativo;

    @Column(name = "principal", nullable = false)
    private Boolean principal = false;

    @Column(name = "orden", nullable = false)
    private Integer orden = 0;

    @Column(name = "fecha_creacion", nullable = false)
    private OffsetDateTime fechaCreacion;

    @PrePersist
    public void prePersist() {

        if (principal == null) {
            principal = false;
        }

        if (orden == null) {
            orden = 0;
        }

        if (fechaCreacion == null) {
            fechaCreacion = OffsetDateTime.now();
        }
    }
}