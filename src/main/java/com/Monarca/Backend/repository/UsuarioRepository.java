package com.Monarca.Backend.repository;

import com.Monarca.Backend.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByCorreoIgnoreCase(String correo);

    boolean existsByCorreoIgnoreCase(String correo);
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("""
        UPDATE Usuario u SET u.password = :nueva, u.fechaActualizacion = :ahora
        WHERE u.idUsuario = :id AND u.password = :anterior AND u.activo = true
        """)
    int restablecerPassword(@org.springframework.data.repository.query.Param("id") Long id,
                           @org.springframework.data.repository.query.Param("anterior") String anterior,
                           @org.springframework.data.repository.query.Param("nueva") String nueva,
                           @org.springframework.data.repository.query.Param("ahora") java.time.OffsetDateTime ahora);
}
