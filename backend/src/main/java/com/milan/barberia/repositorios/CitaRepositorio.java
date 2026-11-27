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
         + "and c.estado in ('programada','confirmada','en_atencion','terminada','completada') "
         + "and c.fechaHoraInicio < :fin and c.fechaHoraFin > :inicio "
         + "and c.idCita <> :excluir")
    long contarSolapadas(@Param("barbero") Integer barbero, @Param("inicio") LocalDateTime inicio,
                         @Param("fin") LocalDateTime fin, @Param("excluir") Integer excluir);

    List<Cita> findByIdClienteOrderByFechaHoraInicioDesc(Integer idCliente);

    /** Citas activas del barbero que empiezan desde una hora, en orden (para correrlas si hay retraso). */
    @Query("select c from Cita c where c.idBarbero = :barbero and c.idCita <> :excluir "
         + "and c.estado in ('programada','confirmada','en_atencion') "
         + "and c.fechaHoraInicio >= :desde and c.fechaHoraInicio < :hasta order by c.fechaHoraInicio")
    List<Cita> siguientesDelDia(@Param("barbero") Integer barbero, @Param("excluir") Integer excluir,
                                @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    List<Cita> findByFechaHoraInicioBetweenOrderByFechaHoraInicio(LocalDateTime desde, LocalDateTime hasta);

    List<Cita> findByIdBarberoAndFechaHoraInicioBetweenOrderByFechaHoraInicio(
            Integer idBarbero, LocalDateTime desde, LocalDateTime hasta);
}
