package com.Monarca.Backend.repository;

import com.Monarca.Backend.model.DetallePedido;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetallePedidoRepository
        extends JpaRepository<DetallePedido, Long> {
}