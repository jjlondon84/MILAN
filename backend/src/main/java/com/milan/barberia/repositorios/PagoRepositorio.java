package com.milan.barberia.repositorios;

import com.milan.barberia.entidades.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PagoRepositorio extends JpaRepository<Pago, Integer> {

    List<Pago> findByIdVenta(Integer idVenta);
}
