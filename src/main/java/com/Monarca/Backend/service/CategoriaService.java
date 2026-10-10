package com.Monarca.Backend.service;

import com.Monarca.Backend.model.Categoria;
import com.Monarca.Backend.repository.CategoriaRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CategoriaService {

    @Autowired
    private CategoriaRepository categoriaRepository;


    public List<Categoria> listarTodas() {
        return categoriaRepository.findAll();
    }


    public Optional<Categoria> buscarPorId(Long id) {
        return categoriaRepository.findById(id);
    }


    public Categoria guardar(Categoria categoria) {
        return categoriaRepository.save(categoria);
    }


    public Categoria actualizar(Long id, Categoria datos) {

        Categoria categoria = categoriaRepository
                .findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Categoría no encontrada"
                        )
                );

        if (datos.getNombre() != null) {
            categoria.setNombre(datos.getNombre());
        }

        if (datos.getSlug() != null) {
            categoria.setSlug(datos.getSlug());
        }

        if (datos.getDescripcion() != null) {
            categoria.setDescripcion(
                    datos.getDescripcion()
            );
        }

        if (datos.getActivo() != null) {
            categoria.setActivo(
                    datos.getActivo()
            );
        }

        return categoriaRepository.save(
                categoria
        );
    }


    public void eliminar(Long id) {

        if (!categoriaRepository.existsById(id)) {
            throw new RuntimeException(
                    "Categoría no encontrada"
            );
        }

        categoriaRepository.deleteById(id);
    }
}