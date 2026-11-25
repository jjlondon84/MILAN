package com.milan.barberia.repositorios;

import com.milan.barberia.entidades.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CajaRepositorio extends JpaRepository<Caja, Integer> {

    Optional<Caja> findFirstByEstado(String estado);

    List<Caja> findAllByOrderByFechaHoraAperturaDesc();
}
