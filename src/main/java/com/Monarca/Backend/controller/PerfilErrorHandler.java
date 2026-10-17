package com.Monarca.Backend.controller;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestControllerAdvice(assignableTypes={PerfilController.class,ProbadorController.class})
public class PerfilErrorHandler {
 @ExceptionHandler(IllegalArgumentException.class)
 public ResponseEntity<Map<String,String>> error(IllegalArgumentException e){
   return ResponseEntity.badRequest().body(Map.of("error",e.getMessage()));
 }
}
