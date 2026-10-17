package com.Monarca.Backend.model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
@Entity @Table(name="fotos_probador",schema="monarca")
@Getter @Setter public class FotoProbador {
 @Id @Column(name="id_usuario") private Long idUsuario;
 @Column(name="mime_tipo",nullable=false,length=25) private String mimeTipo;
 @Column(name="datos",nullable=false,columnDefinition="bytea") private byte[] datos;
 @Column(name="fecha_actualizacion",nullable=false) private OffsetDateTime fechaActualizacion;
}
