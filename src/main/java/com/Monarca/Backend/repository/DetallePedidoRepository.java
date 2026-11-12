package com.Monarca.Backend.repository;

import com.Monarca.Backend.model.DetallePedido;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface DetallePedidoRepository
        extends JpaRepository<DetallePedido, Long> {
    // Consulta por lote, evitando una consulta por cada pedido.
    List<DetallePedido> findByPedido_IdPedidoInOrderByIdDetalleAsc(Collection<Long> idPedidos);
}