package com.milan.barberia.repositorios;

import com.milan.barberia.entidades.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TurnoLaboralRepositorio extends JpaRepository<TurnoLaboral, Integer> {

    Optional<TurnoLaboral> findByIdBarberoAndDiaSemana(Integer idBarbero, Integer diaSemana);

    List<TurnoLaboral> findByIdBarbero(Integer idBarbero);
}
