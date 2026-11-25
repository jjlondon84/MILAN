package com.milan.barberia.repositorios;

import com.milan.barberia.entidades.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CitaRepositorio extends JpaRepository<Cita, Integer> {

    /** Cuenta las citas activas del barbero que se cruzan con la franja indicada. */
    @Query("select count(c) from Cita c where c.idBarbero = :barbero "
         + "and c.estado in ('programada','confirmada','en_atencion','completada') "
         + "and c.fechaHoraInicio < :fin and c.fechaHoraFin > :inicio "
         + "and c.idCita <> :excluir")
    long contarSolapadas(@Param("barbero") Integer barbero, @Param("inicio") LocalDateTime inicio,
                         @Param("fin") LocalDateTime fin, @Param("excluir") Integer excluir);

    List<Cita> findByIdClienteOrderByFechaHoraInicioDesc(Integer idCliente);

    List<Cita> findByFechaHoraInicioBetweenOrderByFechaHoraInicio(LocalDateTime desde, LocalDateTime hasta);

    List<Cita> findByIdBarberoAndFechaHoraInicioBetweenOrderByFechaHoraInicio(
            Integer idBarbero, LocalDateTime desde, LocalDateTime hasta);
}
