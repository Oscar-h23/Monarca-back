package com.Monarca.Backend.controller;

import com.Monarca.Backend.dto.*;
import com.Monarca.Backend.model.FotoProbador;
import com.Monarca.Backend.service.PerfilService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.security.Principal;

@RestController
@RequestMapping("/api/perfil")
public class PerfilController {
 private final PerfilService perfiles;
 public PerfilController(PerfilService perfiles){this.perfiles=perfiles;}
 @GetMapping public PerfilDto obtener(Principal principal){return perfiles.consultar(principal.getName());}
 @PutMapping public PerfilDto editar(Principal principal,@RequestBody PerfilUpdateDto datos){
   return perfiles.actualizar(principal.getName(),datos);
 }
 @PostMapping(value="/foto",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
 public ResponseEntity<Void> subir(Principal principal,@RequestPart("foto") MultipartFile foto)throws IOException{
   perfiles.guardarFoto(principal.getName(),foto);return ResponseEntity.noContent().build();
 }
 @GetMapping("/foto")
 public ResponseEntity<byte[]> foto(Principal principal){
   FotoProbador foto=perfiles.foto(principal.getName());
   return ResponseEntity.ok().cacheControl(CacheControl.noStore())
     .contentType(MediaType.parseMediaType(foto.getMimeTipo())).body(foto.getDatos());
 }
 @DeleteMapping("/foto")
 public ResponseEntity<Void> borrar(Principal principal){
   perfiles.borrarFoto(principal.getName());return ResponseEntity.noContent().build();
 }
}
