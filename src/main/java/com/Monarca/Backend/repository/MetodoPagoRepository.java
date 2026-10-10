package com.Monarca.Backend.repository;

import com.Monarca.Backend.model.MetodoPago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MetodoPagoRepository
        extends JpaRepository<MetodoPago, Long> {

    Optional<MetodoPago> findByCodigo(String codigo);
}