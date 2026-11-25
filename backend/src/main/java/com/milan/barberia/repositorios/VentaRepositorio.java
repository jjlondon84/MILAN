package com.milan.barberia.repositorios;

import com.milan.barberia.entidades.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VentaRepositorio extends JpaRepository<Venta, Integer> {

    boolean existsByIdCitaAndEstado(Integer idCita, String estado);

    List<Venta> findByIdCajaOrderByFechaHoraDesc(Integer idCaja);
}
