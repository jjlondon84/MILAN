package com.milan.barberia.repositorios;

import com.milan.barberia.entidades.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DetalleVentaRepositorio extends JpaRepository<DetalleVenta, Integer> {

    List<DetalleVenta> findByIdVenta(Integer idVenta);
}
