package com.milan.barberia.controladores;

import com.milan.barberia.servicios.AgendaServicio;
import com.milan.barberia.servicios.AgendaServicio.CitaRequest;
import com.milan.barberia.servicios.AgendaServicio.CitaResponse;
import com.milan.barberia.servicios.AgendaServicio.DisponibilidadResponse;
import com.milan.barberia.servicios.AgendaServicio.ReprogramarRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/citas")
@RequiredArgsConstructor
@Tag(name = "Citas", description = "Agenda: programar, confirmar, atender, reprogramar y cancelar citas sin cruces")
public class CitaControlador {

    private final AgendaServicio servicio;

    @Operation(summary = "Agenda", description = "Sin parámetros devuelve las citas de hoy. Ej: /api/citas?idBarbero=1&desde=2026-11-20&hasta=2026-11-22")
    @GetMapping
    public List<CitaResponse> agenda(
            @RequestParam(required = false) Integer idBarbero,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return servicio.agenda(idBarbero, desde, hasta);
    }

    @Operation(summary = "Consultar una cita")
    @GetMapping("/{id}")
    public CitaResponse obtener(@PathVariable Integer id) {
        return servicio.obtener(id);
    }

    @Operation(summary = "Historial de visitas de un cliente")
    @GetMapping("/cliente/{idCliente}")
    public List<CitaResponse> historial(@PathVariable Integer idCliente) {
        return servicio.historialCliente(idCliente);
    }

    @Operation(summary = "Verificar si un horario está libre",
            description = "Ej: /api/citas/disponibilidad?idBarbero=1&fechaHoraInicio=2026-11-20T10:30:00&idServicios=1,2")
    @GetMapping("/disponibilidad")
    public DisponibilidadResponse disponibilidad(
            @RequestParam Integer idBarbero,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaHoraInicio,
            @RequestParam List<Integer> idServicios,
            @Parameter(description = "Cita a ignorar (al reprogramar)") @RequestParam(required = false) Integer excluirCita) {
        return servicio.disponibilidad(idBarbero, fechaHoraInicio, idServicios, excluirCita);
    }

    @Operation(summary = "Horas libres de un barbero en un día", description = "Ej: /api/citas/horarios-disponibles?idBarbero=1&fecha=2026-11-20&idServicios=1")
    @GetMapping("/horarios-disponibles")
    public List<String> horariosDisponibles(
            @RequestParam Integer idBarbero,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam List<Integer> idServicios) {
        return servicio.horariosDisponibles(idBarbero, fecha, idServicios);
    }

    @Operation(summary = "Programar una cita", description = "Valida turno, descanso, ausencias y cruces. La hora de fin se calcula con la duración de los servicios")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CitaResponse crear(@Valid @RequestBody CitaRequest r) {
        return servicio.crear(r);
    }

    @Operation(summary = "Reprogramar una cita (cambia hora y opcionalmente barbero)")
    @PutMapping("/{id}/reprogramar")
    public CitaResponse reprogramar(@PathVariable Integer id, @Valid @RequestBody ReprogramarRequest r) {
        return servicio.reprogramar(id, r);
    }

    @Operation(summary = "Confirmar la cita (pendiente → confirmada)")
    @PatchMapping("/{id}/confirmar")
    public CitaResponse confirmar(@PathVariable Integer id) {
        return servicio.confirmar(id);
    }

    @Operation(summary = "Iniciar la atención (→ en atención)")
    @PatchMapping("/{id}/iniciar")
    public CitaResponse iniciar(@PathVariable Integer id) {
        return servicio.iniciarAtencion(id);
    }

    @Operation(summary = "Cancelar la cita")
    @PatchMapping("/{id}/cancelar")
    public CitaResponse cancelar(@PathVariable Integer id) {
        return servicio.cancelar(id);
    }

    @Operation(summary = "Marcar que el cliente no asistió")
    @PatchMapping("/{id}/no-asistio")
    public CitaResponse noAsistio(@PathVariable Integer id) {
        return servicio.marcarNoAsistio(id);
    }
}
