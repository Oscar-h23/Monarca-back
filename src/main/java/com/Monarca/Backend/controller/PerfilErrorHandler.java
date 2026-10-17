package com.Monarca.Backend.controller;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestControllerAdvice(assignableTypes={PerfilController.class,ProbadorController.class})
public class PerfilErrorHandler {
 @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
 public ResponseEntity<Map<String,String>> remoto(org.springframework.web.server.ResponseStatusException e){
   String detalle=e.getReason()!=null?e.getReason():"Error al procesar la solicitud.";
   return ResponseEntity.status(e.getStatusCode()).body(Map.of("error",detalle));
 }
 @ExceptionHandler(IllegalArgumentException.class)
 public ResponseEntity<Map<String,String>> error(IllegalArgumentException e){
   return ResponseEntity.badRequest().body(Map.of("error",e.getMessage()));
 }
}
