package com.milan.barberia.servicios;

import com.milan.barberia.repositorios.ReporteRepositorio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Indicadores del Dashboard (citas, ingresos, stock bajo y barberos en turno). */
@Service
@RequiredArgsConstructor
public class PanelServicio {

    private final ReporteRepositorio reportes;
    private final AgendaServicio citas;
    private final BarberoServicio barberos;
    private final CajaServicio caja;

    /**
     * "Mi día" del barbero: sus citas de hoy, la que está atendiendo, lo generado, sus propinas y su retraso.
     */
    public Map<String, Object> miPanel(Integer idBarbero) {
        LocalDate hoy = LocalDate.now();
        List<AgendaServicio.CitaResponse> agenda = citas.agenda(idBarbero, hoy, hoy).stream()
                .filter(c -> !List.of("cancelada", "no_asistio").contains(c.estado()))
                .toList();
        List<AgendaServicio.CitaResponse> atendidas = agenda.stream()
                .filter(c -> List.of("terminada", "completada").contains(c.estado())).toList();
        BigDecimal generado = atendidas.stream().map(AgendaServicio.CitaResponse::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal propinas = atendidas.stream().map(AgendaServicio.CitaResponse::propina)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(reportes.propinasCajaDelDia(idBarbero, hoy));
        long retraso = atendidas.stream().mapToLong(AgendaServicio.CitaResponse::minutosRetraso).sum();
        AgendaServicio.CitaResponse enAtencion = agenda.stream()
                .filter(c -> "en_atencion".equals(c.estado())).findFirst().orElse(null);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("fecha", hoy);
        r.put("barbero", barberos.resumen(hoy).stream()
                .filter(b -> b.idBarbero().equals(idBarbero)).findFirst().orElse(null));
        r.put("citasDelDia", agenda.size());
        r.put("atendidas", atendidas.size());
        r.put("generadoHoy", generado);
        r.put("propinasHoy", propinas);
        r.put("minutosRetrasoHoy", retraso);
        r.put("enAtencion", enAtencion);
        r.put("citas", agenda);
        return r;
    }

    public Map<String, Object> resumen(LocalDate fecha) {
        LocalDate dia = fecha != null ? fecha : LocalDate.now();
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime desdeProximas = dia.equals(LocalDate.now()) ? ahora : dia.atStartOfDay();

        BigDecimal hoy = reportes.ingresosDelDia(dia);
        BigDecimal ayer = reportes.ingresosDelDia(dia.minusDays(1));
        BigDecimal variacion = ayer.signum() == 0 ? null
                : hoy.subtract(ayer).multiply(BigDecimal.valueOf(100)).divide(ayer, 1, RoundingMode.HALF_UP);

        List<Map<String, Object>> stockBajo = reportes.stockBajo();
        List<BarberoServicio.BarberoResumen> equipo = barberos.resumen(dia);
        long activos = equipo.stream().filter(b -> "activo".equals(b.estado())).count();
        long enTurno = equipo.stream().filter(BarberoServicio::trabaja).count();

        List<AgendaServicio.CitaResponse> agenda = citas.agenda(null, dia, dia).stream()
                .filter(c -> !List.of("cancelada", "no_asistio").contains(c.estado()))
                .toList();

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("fecha", dia);
        r.put("citasDelDia", reportes.citasDelDia(dia));
        r.put("citasProximas", reportes.citasProximas(desdeProximas, dia.plusDays(1).atStartOfDay()));
        r.put("ingresosDelDia", hoy);
        r.put("ingresosDiaAnterior", ayer);
        r.put("variacionIngresosPorcentaje", variacion);
        r.put("stockBajo", stockBajo.size());
        r.put("productosStockBajo", stockBajo);
        r.put("barberosEnTurno", enTurno);
        r.put("barberosActivos", activos);
        r.put("citas", agenda);
        r.put("cajaAbierta", caja.actualOpcional());
        return r;
    }
}
