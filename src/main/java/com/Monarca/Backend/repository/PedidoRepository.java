package com.Monarca.Backend.repository;

import com.Monarca.Backend.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

import java.util.List;

public interface PedidoRepository
        extends JpaRepository<Pedido, Long> {

    List<Pedido> findByUsuario_IdUsuario(Long idUsuario);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pedido p where p.idPedido = :idPedido")
    Optional<Pedido> bloquearPorId(@Param("idPedido") Long idPedido);

    // Orden más reciente primero, siempre acotado al propietario autenticado.
    List<Pedido> findByUsuario_IdUsuarioOrderByFechaPedidoDescIdPedidoDesc(Long idUsuario);
}