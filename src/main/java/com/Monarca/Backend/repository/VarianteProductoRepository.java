package com.Monarca.Backend.repository;

import com.Monarca.Backend.model.VarianteProducto;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VarianteProductoRepository
        extends JpaRepository<VarianteProducto, Long> {

    List<VarianteProducto> findByProducto_IdProducto(
            Long idProducto
    );

    List<VarianteProducto> findByProducto_IdProductoAndActivoTrue(
            Long idProducto
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT v
            FROM VarianteProducto v
            JOIN FETCH v.producto
            WHERE v.idVariante = :id
            """)
    Optional<VarianteProducto> findByIdForUpdate(
            @Param("id") Long id
    );
}