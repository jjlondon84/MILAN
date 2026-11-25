package com.milan.barberia.repositorios;

import com.milan.barberia.entidades.RecuperacionContrasena;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecuperacionContrasenaRepositorio extends JpaRepository<RecuperacionContrasena, Integer> {

    Optional<RecuperacionContrasena> findByTokenHash(String tokenHash);
}
