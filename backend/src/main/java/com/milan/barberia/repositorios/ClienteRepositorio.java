package com.milan.barberia.repositorios;

import com.milan.barberia.entidades.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClienteRepositorio extends JpaRepository<Cliente, Integer> {

    List<Cliente> findByNombresContainingIgnoreCaseOrApellidosContainingIgnoreCaseOrTelefonoContaining(
            String nombres, String apellidos, String telefono);
}
