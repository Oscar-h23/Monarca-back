package com.Monarca.Backend.service;

import com.Monarca.Backend.dto.*;
import com.Monarca.Backend.model.*;
import com.Monarca.Backend.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;

@Service
public class PerfilService {
 private final UsuarioRepository usuarios;
 private final FotoProbadorRepository fotos;
 public PerfilService(UsuarioRepository usuarios,FotoProbadorRepository fotos) {
   this.usuarios=usuarios;this.fotos=fotos;
 }
 @Transactional(readOnly=true)
 public PerfilDto consultar(String correo){
   Usuario u=usuario(correo);
   return new PerfilDto(u.getNombres(),u.getApellidos(),u.getCorreo(),u.getTelefono(),
     u.getTipoDocumento(),u.getNumeroDocumento(),u.getDireccion(),u.getDistrito(),
     u.getProvincia(),u.getDepartamento(),u.getReferencia(),fotos.existsById(u.getIdUsuario()));
 }
 @Transactional
 public PerfilDto actualizar(String correo,PerfilUpdateDto dto){
   if(dto==null)throw new IllegalArgumentException("Datos de perfil requeridos.");
   Usuario u=usuario(correo);
   u.setNombres(requerido(dto.nombres(),100,"Nombres"));
   u.setApellidos(requerido(dto.apellidos(),100,"Apellidos"));
   u.setTelefono(opcional(dto.telefono(),20,"Teléfono"));
   u.setTipoDocumento(opcional(dto.tipoDocumento(),20,"Tipo de documento"));
   u.setNumeroDocumento(opcional(dto.numeroDocumento(),20,"Número de documento"));
   u.setDireccion(opcional(dto.direccion(),200,"Dirección"));
   u.setDistrito(opcional(dto.distrito(),100,"Distrito"));
   u.setProvincia(opcional(dto.provincia(),100,"Provincia"));
   u.setDepartamento(opcional(dto.departamento(),100,"Departamento"));
   u.setReferencia(opcional(dto.referencia(),200,"Referencia"));
   usuarios.save(u);
   return consultar(correo);
 }
 @Transactional
 public void guardarFoto(String correo,MultipartFile archivo) throws IOException {
   if(archivo==null||archivo.isEmpty()||archivo.getSize()>4000000L)
     throw new IllegalArgumentException("La foto debe pesar máximo 4 MB.");
   byte[] bytes=archivo.getBytes();
   String mime=detectarMime(bytes);
   if(mime==null)throw new IllegalArgumentException("Solo JPG, PNG y WebP.");
   Long id=usuario(correo).getIdUsuario();
   FotoProbador foto=fotos.findById(id).orElseGet(FotoProbador::new);
   foto.setIdUsuario(id);foto.setMimeTipo(mime);
   foto.setDatos(bytes);foto.setFechaActualizacion(OffsetDateTime.now());
   fotos.save(foto);
 }
 @Transactional(readOnly=true)
 public FotoProbador foto(String correo){
   return fotos.findById(usuario(correo).getIdUsuario()).orElseThrow(
     ()->new ResponseStatusException(HttpStatus.NOT_FOUND,"No hay foto registrada."));
 }
 @Transactional
 public void borrarFoto(String correo){
   fotos.deleteById(usuario(correo).getIdUsuario());
 }
 @Transactional(readOnly=true)
 public Usuario usuario(String correo){
   return usuarios.findByCorreoIgnoreCase(correo)
     .filter(u->Boolean.TRUE.equals(u.getActivo()))
     .orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Cuenta no disponible."));
 }
 public static String detectarMime(byte[] b){
   if(b==null)return null;
   if(b.length>=3&&(b[0]&255)==255&&(b[1]&255)==216&&(b[2]&255)==255)return "image/jpeg";
   if(b.length>=8&&Arrays.equals(Arrays.copyOf(b,8),new byte[]{(byte)137,80,78,71,13,10,26,10}))return "image/png";
   if(b.length>=12&&new String(b,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
     &&new String(b,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP"))return "image/webp";
   return null;
 }
 private static String requerido(String x,int n,String k){
   if(x==null||x.isBlank())throw new IllegalArgumentException(k+" es obligatorio.");
   return opcional(x,n,k);
 }
 private static String opcional(String x,int n,String k){
   if(x==null||x.isBlank())return null;
   x=x.trim();
   if(x.length()>n)throw new IllegalArgumentException(k+" excede "+n+" caracteres.");
   return x;
 }
}
