package com.milan.barberia.repositorios;

import com.milan.barberia.entidades.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AusenciaRepositorio extends JpaRepository<Ausencia, Integer> {

    boolean existsByIdBarberoAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
            Integer idBarbero, LocalDate hasta, LocalDate desde);

    List<Ausencia> findByIdBarbero(Integer idBarbero);
}
