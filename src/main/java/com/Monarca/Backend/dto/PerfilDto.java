package com.Monarca.Backend.dto;
public record PerfilDto(String nombres,String apellidos,String correo,String telefono,
 String tipoDocumento,String numeroDocumento,String direccion,String distrito,
 String provincia,String departamento,String referencia,boolean tieneFoto,
 java.math.BigDecimal bustoCm,java.math.BigDecimal cinturaCm,
 java.math.BigDecimal caderaCm,java.math.BigDecimal alturaCm,boolean googleVinculado) {}
