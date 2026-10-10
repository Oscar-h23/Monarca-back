package com.Monarca.Backend.repository;

import com.Monarca.Backend.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PedidoRepository
        extends JpaRepository<Pedido, Long> {

    List<Pedido> findByUsuario_IdUsuario(
            Long idUsuario
    );
}