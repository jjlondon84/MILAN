package com.milan.barberia.repositorios;

import com.milan.barberia.entidades.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CitaServicioRepositorio extends JpaRepository<CitaServicio, CitaServicioId> {

    List<CitaServicio> findByIdCita(Integer idCita);
}
