package com.Monarca.Backend.controller;

import com.Monarca.Backend.model.Categoria;
import com.Monarca.Backend.repository.CategoriaRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categorias")
@CrossOrigin(origins = "*")
public class CategoriaController {

    @Autowired
    private CategoriaRepository categoriaRepository;


    @GetMapping
    public ResponseEntity<List<Categoria>> listarCategorias() {

        return ResponseEntity.ok(
                categoriaRepository.findAll()
        );
    }


    @GetMapping("/{id}")
    public ResponseEntity<?> buscarCategoria(
            @PathVariable Long id
    ) {

        return categoriaRepository
                .findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() ->
                        ResponseEntity
                                .notFound()
                                .build()
                );
    }
}