package com.Monarca.Backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegistroDto {
    private String nombre;
    private String apellido;
    private String email;
    private String password;
}