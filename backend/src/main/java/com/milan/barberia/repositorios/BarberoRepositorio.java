package com.milan.barberia.repositorios;

import com.milan.barberia.entidades.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BarberoRepositorio extends JpaRepository<Barbero, Integer> {

    List<Barbero> findByEstadoOrderByNombres(String estado);

    Optional<Barbero> findByIdUsuario(Integer idUsuario);

    List<Barbero> findByNombresContainingIgnoreCaseOrApellidosContainingIgnoreCase(String nombres, String apellidos);
}
