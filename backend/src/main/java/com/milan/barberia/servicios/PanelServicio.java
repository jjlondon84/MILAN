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
