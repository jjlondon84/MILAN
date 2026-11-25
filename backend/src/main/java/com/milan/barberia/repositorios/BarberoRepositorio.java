package com.milan.barberia.repositorios;

import com.milan.barberia.entidades.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BarberoRepositorio extends JpaRepository<Barbero, Integer> {

    List<Barbero> findByEstadoOrderByNombres(String estado);

    List<Barbero> findByNombresContainingIgnoreCaseOrApellidosContainingIgnoreCase(String nombres, String apellidos);
}
