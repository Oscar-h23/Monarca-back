package com.Monarca.Backend.repository;

import com.Monarca.Backend.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PagoRepository
        extends JpaRepository<Pago, Long> {
    Optional<Pago> findFirstByPedido_IdPedidoOrderByIdPagoDesc(Long idPedido);
    List<Pago> findByPedido_IdPedidoIn(Collection<Long> pedidos);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pago p where p.pedido.idPedido = :idPedido")
    Optional<Pago> bloquearPorPedido(@Param("idPedido") Long idPedido);
}