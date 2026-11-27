package com.milan.barberia.controladores;

import com.milan.barberia.excepciones.ExcepcionAccesoDenegado;
import com.milan.barberia.servicios.AgendaServicio;
import com.milan.barberia.servicios.AutenticacionServicio;
import com.milan.barberia.servicios.AgendaServicio.CitaRequest;
import com.milan.barberia.servicios.AgendaServicio.CitaResponse;
import com.milan.barberia.servicios.AgendaServicio.DisponibilidadResponse;
import com.milan.barberia.servicios.AgendaServicio.ReprogramarRequest;
import com.milan.barberia.servicios.AgendaServicio.TerminarRequest;
import com.milan.barberia.servicios.AgendaServicio.TerminarResponse;
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
            @Parameter(hidden = true) @RequestAttribute("sesion") AutenticacionServicio.Sesion sesion,
            @RequestParam(required = false) Integer idBarbero,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        // Un barbero solo ve su propia agenda
        return servicio.agenda(sesion.admin() ? idBarbero : sesion.barberoPropio(), desde, hasta);
    }

    @Operation(summary = "Consultar una cita")
    @GetMapping("/{id}")
    public CitaResponse obtener(@Parameter(hidden = true) @RequestAttribute("sesion") AutenticacionServicio.Sesion sesion, @PathVariable Integer id) {
        return propia(sesion, id);
    }

    @Operation(summary = "Historial de visitas de un cliente")
    @GetMapping("/cliente/{idCliente}")
    public List<CitaResponse> historial(@PathVariable Integer idCliente) {
        // El historial del cliente lo ven todos los barberos (preferencias y visitas anteriores)
        return servicio.historialCliente(idCliente);
    }

    @Operation(summary = "Verificar si un horario está libre",
            description = "Ej: /api/citas/disponibilidad?idBarbero=1&fechaHoraInicio=2026-11-20T10:30:00&idServicios=1,2")
    @GetMapping("/disponibilidad")
    public DisponibilidadResponse disponibilidad(
            @Parameter(hidden = true) @RequestAttribute("sesion") AutenticacionServicio.Sesion sesion,
            @RequestParam Integer idBarbero,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaHoraInicio,
            @RequestParam List<Integer> idServicios,
            @Parameter(description = "Cita a ignorar (al reprogramar)") @RequestParam(required = false) Integer excluirCita) {
        exigirBarbero(sesion, idBarbero);
        return servicio.disponibilidad(idBarbero, fechaHoraInicio, idServicios, excluirCita);
    }

    @Operation(summary = "Horas libres de un barbero en un día", description = "Ej: /api/citas/horarios-disponibles?idBarbero=1&fecha=2026-11-20&idServicios=1")
    @GetMapping("/horarios-disponibles")
    public List<String> horariosDisponibles(
            @Parameter(hidden = true) @RequestAttribute("sesion") AutenticacionServicio.Sesion sesion,
            @RequestParam Integer idBarbero,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam List<Integer> idServicios) {
        exigirBarbero(sesion, idBarbero);
        return servicio.horariosDisponibles(idBarbero, fecha, idServicios);
    }

    @Operation(summary = "Programar una cita", description = "Valida turno, descanso, ausencias y cruces. La hora de fin se calcula con la duración de los servicios")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CitaResponse crear(@Parameter(hidden = true) @RequestAttribute("sesion") AutenticacionServicio.Sesion sesion, @Valid @RequestBody CitaRequest r) {
        exigirBarbero(sesion, r.idBarbero());
        return servicio.crear(r);
    }

    @Operation(summary = "Reprogramar una cita (cambia hora y opcionalmente barbero)")
    @PutMapping("/{id}/reprogramar")
    public CitaResponse reprogramar(@Parameter(hidden = true) @RequestAttribute("sesion") AutenticacionServicio.Sesion sesion, @PathVariable Integer id,
                                    @Valid @RequestBody ReprogramarRequest r) {
        propia(sesion, id);
        if (r.idBarbero() != null) {
            exigirBarbero(sesion, r.idBarbero());
        }
        return servicio.reprogramar(id, r);
    }

    @Operation(summary = "Confirmar la cita (pendiente → confirmada)")
    @PatchMapping("/{id}/confirmar")
    public CitaResponse confirmar(@Parameter(hidden = true) @RequestAttribute("sesion") AutenticacionServicio.Sesion sesion, @PathVariable Integer id) {
        propia(sesion, id);
        return servicio.confirmar(id);
    }

    @Operation(summary = "Iniciar la atención (→ en atención)")
    @PatchMapping("/{id}/iniciar")
    public CitaResponse iniciar(@Parameter(hidden = true) @RequestAttribute("sesion") AutenticacionServicio.Sesion sesion, @PathVariable Integer id) {
        propia(sesion, id);
        return servicio.iniciarAtencion(id);
    }

    @Operation(summary = "Cancelar la cita")
    @PatchMapping("/{id}/cancelar")
    public CitaResponse cancelar(@Parameter(hidden = true) @RequestAttribute("sesion") AutenticacionServicio.Sesion sesion, @PathVariable Integer id) {
        propia(sesion, id);
        return servicio.cancelar(id);
    }

    @Operation(summary = "Marcar que el cliente no asistió")
    @PatchMapping("/{id}/no-asistio")
    public CitaResponse noAsistio(@Parameter(hidden = true) @RequestAttribute("sesion") AutenticacionServicio.Sesion sesion, @PathVariable Integer id) {
        propia(sesion, id);
        return servicio.marcarNoAsistio(id);
    }

    @Operation(summary = "Ver qué pasaría al terminar la cita",
            description = "Calcula el retraso y qué citas siguientes se correrían, sin guardar nada. Ej: /api/citas/5/terminar?fechaHoraFinReal=2026-11-27T15:13:00")
    @GetMapping("/{id}/terminar")
    public TerminarResponse previaTerminar(@Parameter(hidden = true) @RequestAttribute("sesion") AutenticacionServicio.Sesion sesion, @PathVariable Integer id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaHoraFinReal) {
        propia(sesion, id);
        return servicio.previsualizarTerminar(id, fechaHoraFinReal);
    }

    @Operation(summary = "Terminé: dar por terminada la cita",
            description = "Registra la propina que le dieron al barbero y la hora real de fin (por defecto, ahora). "
                    + "Si terminó tarde, corre en cadena solo las citas siguientes que se crucen. La cita queda lista para cobrar en caja")
    @PostMapping("/{id}/terminar")
    public TerminarResponse terminar(@Parameter(hidden = true) @RequestAttribute("sesion") AutenticacionServicio.Sesion sesion, @PathVariable Integer id,
                                     @Valid @RequestBody TerminarRequest r) {
        propia(sesion, id);
        return servicio.terminar(id, r);
    }

    /** La cita debe ser del barbero de la sesión (el administrador puede todas). */
    private CitaResponse propia(AutenticacionServicio.Sesion sesion, Integer idCita) {
        CitaResponse c = servicio.obtener(idCita);
        if (!sesion.puedeVerBarbero(c.idBarbero())) {
            throw new ExcepcionAccesoDenegado("Esa cita es de otro barbero");
        }
        return c;
    }

    private void exigirBarbero(AutenticacionServicio.Sesion sesion, Integer idBarbero) {
        if (!sesion.admin() && !sesion.barberoPropio().equals(idBarbero)) {
            throw new ExcepcionAccesoDenegado("Solo puedes manejar tu propia agenda");
        }
    }
}
