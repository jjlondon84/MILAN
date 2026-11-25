package com.milan.barberia.servicios;

import com.milan.barberia.entidades.Ausencia;
import com.milan.barberia.entidades.Barbero;
import com.milan.barberia.entidades.Cita;
import com.milan.barberia.entidades.TurnoLaboral;
import com.milan.barberia.repositorios.AusenciaRepositorio;
import com.milan.barberia.repositorios.BarberoRepositorio;
import com.milan.barberia.repositorios.CitaRepositorio;
import com.milan.barberia.repositorios.TurnoLaboralRepositorio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** Vista de "Gestión de personal": turno del día, descanso, ausencias y estado de cada barbero. */
@Service
@RequiredArgsConstructor
public class BarberoServicio {

    /**
     * disponibilidad: inactivo | ausente | sin_turno | en_turno | descanso | fuera_de_turno
     */
    public record BarberoResumen(Integer idBarbero, Integer idUsuario, String documento, String nombres,
                                 String apellidos, String telefono, LocalDate fechaIngreso, String estado,
                                 TurnoLaboral turnoDelDia, Ausencia ausenciaDelDia, String disponibilidad,
                                 long citasDelDia) {}

    private final BarberoRepositorio barberoRepo;
    private final TurnoLaboralRepositorio turnoRepo;
    private final AusenciaRepositorio ausenciaRepo;
    private final CitaRepositorio citaRepo;

    public List<BarberoResumen> resumen(LocalDate fecha) {
        LocalDate dia = fecha != null ? fecha : LocalDate.now();
        return barberoRepo.findAll().stream().map(b -> resumir(b, dia)).toList();
    }

    public BarberoResumen resumenDe(Barbero b, LocalDate dia) {
        return resumir(b, dia);
    }

    private BarberoResumen resumir(Barbero b, LocalDate dia) {
        TurnoLaboral turno = turnoRepo.findByIdBarberoAndDiaSemana(b.getIdBarbero(), dia.getDayOfWeek().getValue())
                .orElse(null);
        Ausencia ausencia = ausenciaRepo.findByIdBarbero(b.getIdBarbero()).stream()
                .filter(a -> !dia.isBefore(a.getFechaInicio()) && !dia.isAfter(a.getFechaFin()))
                .findFirst().orElse(null);
        long citas = citaRepo.findByIdBarberoAndFechaHoraInicioBetweenOrderByFechaHoraInicio(
                        b.getIdBarbero(), dia.atStartOfDay(), dia.plusDays(1).atStartOfDay()).stream()
                .map(Cita::getEstado)
                .filter(e -> !"cancelada".equals(e))
                .count();

        String disponibilidad;
        if (!"activo".equals(b.getEstado())) {
            disponibilidad = "inactivo";
        } else if (ausencia != null) {
            disponibilidad = "ausente";
        } else if (turno == null) {
            disponibilidad = "sin_turno";
        } else if (dia.equals(LocalDate.now())) {
            LocalTime ahora = LocalDateTime.now().toLocalTime();
            if (ahora.isBefore(turno.getHoraInicio()) || !ahora.isBefore(turno.getHoraFin())) {
                disponibilidad = "fuera_de_turno";
            } else if (turno.getDescansoInicio() != null && !ahora.isBefore(turno.getDescansoInicio())
                    && ahora.isBefore(turno.getDescansoFin())) {
                disponibilidad = "descanso";
            } else {
                disponibilidad = "en_turno";
            }
        } else {
            disponibilidad = "en_turno";
        }
        return new BarberoResumen(b.getIdBarbero(), b.getIdUsuario(), b.getDocumento(), b.getNombres(),
                b.getApellidos(), b.getTelefono(), b.getFechaIngreso(), b.getEstado(), turno, ausencia,
                disponibilidad, citas);
    }

    /** Trabaja ese día: activo, con turno y sin ausencia. */
    public static boolean trabaja(BarberoResumen r) {
        return !List.of("inactivo", "ausente", "sin_turno").contains(r.disponibilidad());
    }
}
