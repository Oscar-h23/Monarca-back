package com.Monarca.Backend.repository;

import com.Monarca.Backend.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductoRepository
        extends JpaRepository<Producto, Long> {

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdProductoNot(
            String slug,
            Long idProducto
    );
}