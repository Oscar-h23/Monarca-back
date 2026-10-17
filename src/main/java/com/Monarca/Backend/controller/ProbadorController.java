package com.Monarca.Backend.controller;

import com.Monarca.Backend.service.ProbadorService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/probador")
public class ProbadorController {
 private final ProbadorService service;
 public ProbadorController(ProbadorService service){this.service=service;}
 @PostMapping("/{idProducto}")
 public ResponseEntity<byte[]> generar(Principal principal,@PathVariable Long idProducto,
   @RequestBody Map<String,Boolean> body){
   boolean acepto=body!=null&&Boolean.TRUE.equals(body.get("consentimiento"));
   byte[] bytes=service.probar(principal.getName(),idProducto,acepto);
   String mime=com.Monarca.Backend.service.PerfilService.detectarMime(bytes);
   return ResponseEntity.ok().cacheControl(CacheControl.noStore())
     .contentType(MediaType.parseMediaType(mime)).body(bytes);
 }
}
