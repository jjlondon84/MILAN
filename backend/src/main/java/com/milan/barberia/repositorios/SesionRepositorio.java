package com.milan.barberia.repositorios;

import com.milan.barberia.entidades.Sesion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SesionRepositorio extends JpaRepository<Sesion, String> {

    Optional<Sesion> findByTokenHashAndFechaExpiracionAfter(String tokenHash, LocalDateTime ahora);

    @Modifying
    @Query("delete from Sesion s where s.fechaExpiracion < :ahora")
    int borrarVencidas(@Param("ahora") LocalDateTime ahora);

    @Modifying
    @Query("delete from Sesion s where s.idUsuario = :idUsuario")
    int borrarDelUsuario(@Param("idUsuario") Integer idUsuario);
}
