package com.milan.barberia.servicios;

import com.milan.barberia.excepciones.ExcepcionNegocio;
import com.milan.barberia.entidades.*;
import com.milan.barberia.repositorios.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/** Programar, reprogramar y cancelar citas validando horario, descansos, ausencias y cruces. */
@Service
@RequiredArgsConstructor
public class AgendaServicio {

    public record CitaRequest(@NotNull Integer idCliente,
                              @NotNull Integer idBarbero,
                              @NotNull LocalDateTime fechaHoraInicio,
                              @NotEmpty List<Integer> idServicios,
                              String observaciones) {}

    public record ReprogramarRequest(@NotNull LocalDateTime fechaHoraInicio, Integer idBarbero, String observaciones) {}

    public record CitaServicioInfo(Integer idServicio, String nombre, BigDecimal precio, Integer duracionMin) {}

    public record DisponibilidadResponse(boolean disponible, String mensaje,
                                         LocalDateTime fechaHoraInicio, LocalDateTime fechaHoraFin) {}

    /** Estados en los que la cita aún no se ha atendido. */
    private static final Set<String> PENDIENTES = Set.of("programada", "confirmada");

    public record CitaResponse(Integer idCita, Integer idCliente, String cliente,
                               Integer idBarbero, String barbero,
                               LocalDateTime fechaHoraInicio, LocalDateTime fechaHoraFin,
                               String estado, String observaciones,
                               List<CitaServicioInfo> servicios, BigDecimal total,
                               LocalDateTime fechaHoraFinReal, BigDecimal propina, long minutosRetraso) {}

    /** Al pulsar "Terminé": propina que le dieron (opcional) y hora real de fin (por defecto, ahora). */
    public record TerminarRequest(@DecimalMin(value = "0", message = "La propina no puede ser negativa") BigDecimal propina,
                                  LocalDateTime fechaHoraFinReal) {}

    /** Una cita siguiente que se corre por el retraso. */
    public record CitaCorrida(Integer idCita, String cliente, LocalDateTime inicioAntes, LocalDateTime inicioDespues,
                              LocalDateTime finDespues, boolean fueraDeTurno) {}

    public record TerminarResponse(CitaResponse cita, long minutosRetraso, List<CitaCorrida> citasCorridas) {}

    /** Estados en los que la cita se puede dar por terminada. */
    private static final Set<String> TERMINABLES = Set.of("programada", "confirmada", "en_atencion");

    private final CitaRepositorio citaRepo;
    private final CitaServicioRepositorio citaServicioRepo;
    private final ServicioRepositorio servicioRepo;
    private final BarberoRepositorio barberoRepo;
    private final ClienteRepositorio clienteRepo;
    private final TurnoLaboralRepositorio turnoRepo;
    private final AusenciaRepositorio ausenciaRepo;

    @Transactional
    public CitaResponse crear(CitaRequest r) {
        if (!clienteRepo.existsById(r.idCliente())) {
            throw new ExcepcionNegocio("El cliente no existe");
        }
        validarBarberoActivo(r.idBarbero());

        List<Servicio> servicios = serviciosValidos(r.idServicios());
        int minutos = servicios.stream().mapToInt(Servicio::getDuracionMin).sum();
        LocalDateTime inicio = r.fechaHoraInicio();
        LocalDateTime fin = inicio.plusMinutes(minutos);

        if (inicio.isBefore(LocalDateTime.now())) {
            throw new ExcepcionNegocio("No se puede agendar una cita en el pasado");
        }
        validarDisponibilidad(r.idBarbero(), inicio, fin, 0);

        Cita cita = new Cita();
        cita.setIdCliente(r.idCliente());
        cita.setIdBarbero(r.idBarbero());
        cita.setFechaHoraInicio(inicio);
        cita.setFechaHoraFin(fin);
        cita.setObservaciones(r.observaciones());
        cita = citaRepo.save(cita);

        for (Servicio s : servicios) {
            CitaServicio cs = new CitaServicio();
            cs.setIdCita(cita.getIdCita());
            cs.setIdServicio(s.getIdServicio());
            cs.setPrecioAplicado(s.getPrecio());                       // precio de hoy
            cs.setPorcentajeComisionAplicado(s.getPorcentajeComision()); // comisión de hoy
            citaServicioRepo.save(cs);
        }
        return respuesta(cita);
    }

    @Transactional
    public CitaResponse reprogramar(Integer idCita, ReprogramarRequest r) {
        Cita cita = buscar(idCita);
        exigirEstado(cita, PENDIENTES, "reprogramar");

        Integer idBarbero = r.idBarbero() != null ? r.idBarbero() : cita.getIdBarbero();
        validarBarberoActivo(idBarbero);

        long minutos = Duration.between(cita.getFechaHoraInicio(), cita.getFechaHoraFin()).toMinutes();
        LocalDateTime inicio = r.fechaHoraInicio();
        LocalDateTime fin = inicio.plusMinutes(minutos);

        if (inicio.isBefore(LocalDateTime.now())) {
            throw new ExcepcionNegocio("No se puede reprogramar al pasado");
        }
        validarDisponibilidad(idBarbero, inicio, fin, cita.getIdCita());

        cita.setIdBarbero(idBarbero);
        cita.setFechaHoraInicio(inicio);
        cita.setFechaHoraFin(fin);
        if (r.observaciones() != null) {
            cita.setObservaciones(r.observaciones());
        }
        return respuesta(citaRepo.save(cita));
    }

    /** Pendiente -> Confirmada (el cliente confirmó que asiste). */
    @Transactional
    public CitaResponse confirmar(Integer idCita) {
        return cambiarEstado(idCita, Set.of("programada"), "confirmar", "confirmada");
    }

    /** Pendiente/Confirmada -> En atención (el cliente ya está en la silla). */
    @Transactional
    public CitaResponse iniciarAtencion(Integer idCita) {
        return cambiarEstado(idCita, PENDIENTES, "iniciar la atención de", "en_atencion");
    }

    @Transactional
    public CitaResponse cancelar(Integer idCita) {
        return cambiarEstado(idCita, PENDIENTES, "cancelar", "cancelada");
    }

    @Transactional
    public CitaResponse marcarNoAsistio(Integer idCita) {
        return cambiarEstado(idCita, PENDIENTES, "marcar como no asistió", "no_asistio");
    }

    /** Muestra qué pasaría al terminar la cita a esa hora (sin guardar nada). */
    public TerminarResponse previsualizarTerminar(Integer idCita, LocalDateTime finReal) {
        Cita cita = buscar(idCita);
        exigirEstado(cita, TERMINABLES, "terminar");
        LocalDateTime fin = horaFin(cita, finReal);
        List<Plan> plan = planCorrimiento(cita, fin);
        return new TerminarResponse(respuesta(cita), minutosRetraso(cita, fin), corridas(cita, plan));
    }

    /**
     * El barbero pulsó "Terminé": la cita queda terminada (lista para cobrar en caja), se guarda la propina
     * que le dieron y, si terminó tarde, se corren en cadena solo las citas siguientes que se cruzan.
     */
    @Transactional
    public TerminarResponse terminar(Integer idCita, TerminarRequest r) {
        Cita cita = buscar(idCita);
        exigirEstado(cita, TERMINABLES, "terminar");
        LocalDateTime fin = horaFin(cita, r.fechaHoraFinReal());
        List<Plan> plan = planCorrimiento(cita, fin);

        // Se corren de la última a la primera para que nunca se crucen entre ellas (lo vigila un trigger)
        for (int i = plan.size() - 1; i >= 0; i--) {
            Plan p = plan.get(i);
            p.cita().setFechaHoraInicio(p.inicio());
            p.cita().setFechaHoraFin(p.fin());
            citaRepo.saveAndFlush(p.cita());
        }
        long retraso = minutosRetraso(cita, fin);
        cita.setFechaHoraFinReal(fin);
        cita.setFechaHoraFin(fin);
        cita.setPropina(r.propina() != null ? r.propina() : BigDecimal.ZERO);
        cita.setEstado("terminada");
        citaRepo.saveAndFlush(cita);
        return new TerminarResponse(respuesta(cita), retraso, corridas(cita, plan));
    }

    private record Plan(Cita cita, LocalDateTime inicioAntes, LocalDateTime inicio, LocalDateTime fin) {}

    private LocalDateTime horaFin(Cita cita, LocalDateTime pedida) {
        LocalDateTime fin = (pedida != null ? pedida : LocalDateTime.now()).withSecond(0).withNano(0);
        if (!fin.isAfter(cita.getFechaHoraInicio())) {
            throw new ExcepcionNegocio("La hora de fin debe ser después de la hora de inicio de la cita ("
                    + cita.getFechaHoraInicio().toLocalTime() + ")");
        }
        if (!fin.toLocalDate().equals(cita.getFechaHoraInicio().toLocalDate())) {
            throw new ExcepcionNegocio("La hora de fin debe ser del mismo día de la cita");
        }
        return fin;
    }

    /** Minutos de más respecto a la duración de sus servicios. */
    private long minutosRetraso(Cita cita, LocalDateTime finReal) {
        LocalDateTime previsto = cita.getFechaHoraInicio().plusMinutes(duracionMinutos(cita));
        return Math.max(0, Duration.between(previsto, finReal).toMinutes());
    }

    private int duracionMinutos(Cita cita) {
        List<CitaServicio> items = citaServicioRepo.findByIdCita(cita.getIdCita());
        int total = servicioRepo.findAllById(items.stream().map(CitaServicio::getIdServicio).toList()).stream()
                .mapToInt(Servicio::getDuracionMin).sum();
        return total > 0 ? total : (int) Duration.between(cita.getFechaHoraInicio(), cita.getFechaHoraFin()).toMinutes();
    }

    /** Corrimiento en cadena: cada cita siguiente que choca empieza cuando termina la anterior; un hueco lo absorbe. */
    private List<Plan> planCorrimiento(Cita cita, LocalDateTime finReal) {
        LocalDate dia = cita.getFechaHoraInicio().toLocalDate();
        List<Cita> siguientes = citaRepo.siguientesDelDia(cita.getIdBarbero(), cita.getIdCita(),
                cita.getFechaHoraInicio(), dia.plusDays(1).atStartOfDay());
        List<Plan> plan = new java.util.ArrayList<>();
        LocalDateTime finAnterior = finReal;
        for (Cita n : siguientes) {
            if (!n.getFechaHoraInicio().isBefore(finAnterior)) {
                break; // hay un hueco: el retraso ya se absorbió
            }
            long duracion = Duration.between(n.getFechaHoraInicio(), n.getFechaHoraFin()).toMinutes();
            LocalDateTime nuevoFin = finAnterior.plusMinutes(duracion);
            if (!nuevoFin.toLocalDate().equals(dia)) {
                throw new ExcepcionNegocio("No se puede correr la cita de las " + n.getFechaHoraInicio().toLocalTime()
                        + ": pasaría al día siguiente. Reprográmala o cancélala primero");
            }
            plan.add(new Plan(n, n.getFechaHoraInicio(), finAnterior, nuevoFin));
            finAnterior = nuevoFin;
        }
        return plan;
    }

    private List<CitaCorrida> corridas(Cita cita, List<Plan> plan) {
        TurnoLaboral turno = turnoRepo.findByIdBarberoAndDiaSemana(cita.getIdBarbero(),
                cita.getFechaHoraInicio().getDayOfWeek().getValue()).orElse(null);
        return plan.stream().map(p -> {
            boolean fuera = turno != null && (p.fin().toLocalTime().isAfter(turno.getHoraFin())
                    || (turno.getDescansoInicio() != null
                        && p.inicio().toLocalTime().isBefore(turno.getDescansoFin())
                        && p.fin().toLocalTime().isAfter(turno.getDescansoInicio())));
            String cliente = clienteRepo.findById(p.cita().getIdCliente())
                    .map(x -> x.getNombres() + " " + x.getApellidos()).orElse("");
            return new CitaCorrida(p.cita().getIdCita(), cliente, p.inicioAntes(), p.inicio(), p.fin(), fuera);
        }).toList();
    }

    /** Revisa si el barbero puede atender en esa hora los servicios indicados (lo usa el formulario de nueva cita). */
    public DisponibilidadResponse disponibilidad(Integer idBarbero, LocalDateTime inicio, List<Integer> idServicios,
                                                 Integer excluirCita) {
        LocalDateTime fin = inicio;
        try {
            int minutos = serviciosValidos(idServicios).stream().mapToInt(Servicio::getDuracionMin).sum();
            fin = inicio.plusMinutes(minutos);
            validarBarberoActivo(idBarbero);
            if (inicio.isBefore(LocalDateTime.now())) {
                throw new ExcepcionNegocio("Esa hora ya pasó");
            }
            validarDisponibilidad(idBarbero, inicio, fin, excluirCita == null ? 0 : excluirCita);
            return new DisponibilidadResponse(true, "Horario disponible. No se detectaron cruces.", inicio, fin);
        } catch (ExcepcionNegocio e) {
            return new DisponibilidadResponse(false, e.getMessage(), inicio, fin);
        }
    }

    /** Horas libres (cada 15 min) del barbero en una fecha para los servicios indicados. */
    public List<String> horariosDisponibles(Integer idBarbero, LocalDate fecha, List<Integer> idServicios) {
        int minutos = serviciosValidos(idServicios).stream().mapToInt(Servicio::getDuracionMin).sum();
        validarBarberoActivo(idBarbero);
        TurnoLaboral turno = turnoRepo.findByIdBarberoAndDiaSemana(idBarbero, fecha.getDayOfWeek().getValue())
                .orElse(null);
        List<String> libres = new java.util.ArrayList<>();
        if (turno == null) {
            return libres;
        }
        LocalDateTime ahora = LocalDateTime.now();
        for (LocalTime h = turno.getHoraInicio(); !h.plusMinutes(minutos).isAfter(turno.getHoraFin()); h = h.plusMinutes(15)) {
            LocalDateTime inicio = fecha.atTime(h);
            if (inicio.isBefore(ahora)) {
                continue;
            }
            try {
                validarDisponibilidad(idBarbero, inicio, inicio.plusMinutes(minutos), 0);
                libres.add(h.toString());
            } catch (ExcepcionNegocio ignorada) {
                // hora ocupada o en descanso
            }
            if (h.plusMinutes(15).isBefore(h)) {
                break; // pasó la medianoche
            }
        }
        return libres;
    }

    public CitaResponse obtener(Integer idCita) {
        return respuesta(buscar(idCita));
    }

    /** Agenda: por defecto las citas de hoy. Se puede filtrar por barbero y rango de fechas. */
    public List<CitaResponse> agenda(Integer idBarbero, LocalDate desde, LocalDate hasta) {
        LocalDate d = desde != null ? desde : LocalDate.now();
        LocalDate h = hasta != null ? hasta : d;
        LocalDateTime ini = d.atStartOfDay();
        LocalDateTime fin = h.plusDays(1).atStartOfDay();

        List<Cita> citas = idBarbero != null
                ? citaRepo.findByIdBarberoAndFechaHoraInicioBetweenOrderByFechaHoraInicio(idBarbero, ini, fin)
                : citaRepo.findByFechaHoraInicioBetweenOrderByFechaHoraInicio(ini, fin);
        return citas.stream().map(this::respuesta).toList();
    }

    /** Historial de visitas de un cliente. */
    public List<CitaResponse> historialCliente(Integer idCliente) {
        return citaRepo.findByIdClienteOrderByFechaHoraInicioDesc(idCliente).stream().map(this::respuesta).toList();
    }

    // ------------------------------------------------------------------ reglas

    private void validarBarberoActivo(Integer idBarbero) {
        Barbero b = barberoRepo.findById(idBarbero)
                .orElseThrow(() -> new ExcepcionNegocio("El barbero no existe"));
        if (!"activo".equals(b.getEstado())) {
            throw new ExcepcionNegocio("El barbero está inactivo");
        }
    }

    /** El barbero debe trabajar ese día, dentro de su turno, fuera de su descanso, sin ausencia y sin otra cita. */
    private void validarDisponibilidad(Integer idBarbero, LocalDateTime inicio, LocalDateTime fin, Integer excluirCita) {
        if (!inicio.toLocalDate().equals(fin.toLocalDate())) {
            throw new ExcepcionNegocio("La cita debe empezar y terminar el mismo día");
        }
        LocalDate dia = inicio.toLocalDate();
        LocalTime desde = inicio.toLocalTime();
        LocalTime hasta = fin.toLocalTime();

        TurnoLaboral turno = turnoRepo.findByIdBarberoAndDiaSemana(idBarbero, dia.getDayOfWeek().getValue())
                .orElseThrow(() -> new ExcepcionNegocio("El barbero no trabaja ese día"));

        if (desde.isBefore(turno.getHoraInicio()) || hasta.isAfter(turno.getHoraFin())) {
            throw new ExcepcionNegocio("La cita queda fuera del turno del barbero ("
                    + turno.getHoraInicio() + " a " + turno.getHoraFin() + ")");
        }
        if (turno.getDescansoInicio() != null
                && desde.isBefore(turno.getDescansoFin()) && hasta.isAfter(turno.getDescansoInicio())) {
            throw new ExcepcionNegocio("La cita se cruza con el descanso del barbero ("
                    + turno.getDescansoInicio() + " a " + turno.getDescansoFin() + ")");
        }
        if (ausenciaRepo.existsByIdBarberoAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(idBarbero, dia, dia)) {
            throw new ExcepcionNegocio("El barbero tiene una licencia o día libre en esa fecha");
        }
        if (citaRepo.contarSolapadas(idBarbero, inicio, fin, excluirCita) > 0) {
            throw new ExcepcionNegocio("El barbero ya tiene una cita en ese horario");
        }
    }

    private Cita buscar(Integer id) {
        return citaRepo.findById(id).orElseThrow(() -> new NoSuchElementException("No existe la cita " + id));
    }

    private CitaResponse cambiarEstado(Integer idCita, Set<String> permitidos, String accion, String nuevo) {
        Cita cita = buscar(idCita);
        exigirEstado(cita, permitidos, accion);
        cita.setEstado(nuevo);
        return respuesta(citaRepo.save(cita));
    }

    private void exigirEstado(Cita c, Set<String> permitidos, String accion) {
        if (!permitidos.contains(c.getEstado())) {
            throw new ExcepcionNegocio("No se puede " + accion + " una cita en estado '" + c.getEstado() + "'");
        }
    }

    private List<Servicio> serviciosValidos(List<Integer> idServicios) {
        if (idServicios == null || idServicios.isEmpty()) {
            throw new ExcepcionNegocio("Selecciona al menos un servicio");
        }
        Set<Integer> ids = new LinkedHashSet<>(idServicios);
        List<Servicio> servicios = servicioRepo.findAllById(ids);
        if (servicios.size() != ids.size()) {
            throw new ExcepcionNegocio("Alguno de los servicios no existe");
        }
        if (servicios.stream().anyMatch(s -> !Boolean.TRUE.equals(s.getActivo()))) {
            throw new ExcepcionNegocio("Alguno de los servicios está inactivo");
        }
        return servicios;
    }

    private CitaResponse respuesta(Cita c) {
        String cliente = clienteRepo.findById(c.getIdCliente())
                .map(x -> x.getNombres() + " " + x.getApellidos()).orElse("");
        String barbero = barberoRepo.findById(c.getIdBarbero())
                .map(x -> x.getNombres() + " " + x.getApellidos()).orElse("");

        List<CitaServicio> items = citaServicioRepo.findByIdCita(c.getIdCita());
        Map<Integer, Servicio> catalogo = servicioRepo
                .findAllById(items.stream().map(CitaServicio::getIdServicio).toList()).stream()
                .collect(Collectors.toMap(Servicio::getIdServicio, s -> s));

        List<CitaServicioInfo> servicios = items.stream()
                .map(x -> {
                    Servicio s = catalogo.get(x.getIdServicio());
                    return new CitaServicioInfo(x.getIdServicio(), s != null ? s.getNombre() : "",
                            x.getPrecioAplicado(), s != null ? s.getDuracionMin() : null);
                })
                .toList();
        BigDecimal total = servicios.stream().map(CitaServicioInfo::precio).reduce(BigDecimal.ZERO, BigDecimal::add);

        long retraso = 0;
        if (c.getFechaHoraFinReal() != null) {
            int duracion = servicios.stream().map(CitaServicioInfo::duracionMin)
                    .filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).sum();
            retraso = Math.max(0, Duration.between(c.getFechaHoraInicio().plusMinutes(duracion), c.getFechaHoraFinReal()).toMinutes());
        }
        return new CitaResponse(c.getIdCita(), c.getIdCliente(), cliente, c.getIdBarbero(), barbero,
                c.getFechaHoraInicio(), c.getFechaHoraFin(), c.getEstado(), c.getObservaciones(), servicios, total,
                c.getFechaHoraFinReal(), c.getPropina() != null ? c.getPropina() : BigDecimal.ZERO, retraso);
    }
}
