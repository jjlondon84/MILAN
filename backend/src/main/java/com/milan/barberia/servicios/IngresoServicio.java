package com.milan.barberia.servicios;

import com.milan.barberia.excepciones.ExcepcionNegocio;
import com.milan.barberia.repositorios.ReporteRepositorio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Reporte de ingresos por día, semana o mes: totales, comparación con el periodo anterior,
 * la serie para la gráfica, medios de pago, ingresos por barbero y lo más vendido.
 * "Ingresos" = servicios + productos (lo que es de la barbería). Las propinas van aparte: son de los barberos.
 */
@Service
@RequiredArgsConstructor
public class IngresoServicio {

    private final ReporteRepositorio reportes;

    public Map<String, Object> reporte(LocalDate desde, LocalDate hasta, String agrupar) {
        if (desde == null || hasta == null) throw new ExcepcionNegocio("Indica las fechas desde y hasta");
        if (hasta.isBefore(desde)) throw new ExcepcionNegocio("La fecha final no puede ser anterior a la inicial");
        long dias = ChronoUnit.DAYS.between(desde, hasta) + 1;
        if (dias > 731) throw new ExcepcionNegocio("El periodo máximo es de 2 años");
        String grupo = agrupar == null || agrupar.isBlank()
                ? (dias <= 31 ? "dia" : dias <= 120 ? "semana" : "mes")
                : agrupar.trim().toLowerCase();
        if (!List.of("dia", "semana", "mes").contains(grupo)) throw new ExcepcionNegocio("agrupar debe ser dia, semana o mes");

        LocalDate fin = hasta.plusDays(1);
        List<Map<String, Object>> ventas = reportes.ventasDelPeriodo(desde, fin);

        // Periodo anterior de la misma duración, para comparar
        LocalDate antDesde = desde.minusDays(dias);
        Map<String, Object> anterior = totales(reportes.ventasDelPeriodo(antDesde, desde));
        anterior.put("desde", antDesde);
        anterior.put("hasta", desde.minusDays(1));

        Map<String, Object> tot = totales(ventas);
        BigDecimal enMano = reportes.propinasEnMano(desde, fin);
        tot.put("propinasEnMano", enMano);
        tot.put("propinasTotal", ((BigDecimal) tot.get("propinasCaja")).add(enMano));

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("desde", desde);
        r.put("hasta", hasta);
        r.put("agrupar", grupo);
        r.put("totales", tot);
        r.put("anterior", anterior);
        r.put("variacionPorcentaje", variacion((BigDecimal) tot.get("ingresos"), (BigDecimal) anterior.get("ingresos")));
        r.put("serie", serie(ventas, desde, hasta, grupo));
        r.put("mediosPago", reportes.ingresosPorMedioPago(desde, fin));
        r.put("porBarbero", porBarbero(ventas));
        r.put("topServicios", reportes.topServicios(desde, fin, 5));
        r.put("topProductos", reportes.topProductos(desde, fin, 5));
        return r;
    }

    private static BigDecimal num(Object o) {
        if (o == null) return BigDecimal.ZERO;
        return o instanceof BigDecimal b ? b : new BigDecimal(o.toString());
    }

    private static LocalDate fecha(Object o) {
        if (o instanceof Timestamp t) return t.toLocalDateTime().toLocalDate();
        if (o instanceof LocalDateTime l) return l.toLocalDate();
        return LocalDate.parse(o.toString().substring(0, 10));
    }

    private Map<String, Object> totales(List<Map<String, Object>> ventas) {
        BigDecimal ingresos = BigDecimal.ZERO, productos = BigDecimal.ZERO, propinas = BigDecimal.ZERO;
        for (Map<String, Object> v : ventas) {
            ingresos = ingresos.add(num(v.get("total")));
            productos = productos.add(num(v.get("productos")));
            propinas = propinas.add(num(v.get("propina")));
        }
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("ingresos", ingresos);
        t.put("servicios", ingresos.subtract(productos));
        t.put("productos", productos);
        t.put("propinasCaja", propinas);
        t.put("cobrado", ingresos.add(propinas));
        t.put("ventas", ventas.size());
        t.put("ticketPromedio", ventas.isEmpty() ? BigDecimal.ZERO
                : ingresos.divide(BigDecimal.valueOf(ventas.size()), 0, RoundingMode.HALF_UP));
        return t;
    }

    private static BigDecimal variacion(BigDecimal actual, BigDecimal antes) {
        if (antes.signum() == 0) return null;
        return actual.subtract(antes).multiply(BigDecimal.valueOf(100)).divide(antes, 1, RoundingMode.HALF_UP);
    }

    private static LocalDate inicioGrupo(LocalDate d, String grupo) {
        return switch (grupo) {
            case "semana" -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "mes" -> d.withDayOfMonth(1);
            default -> d;
        };
    }

    private List<Map<String, Object>> serie(List<Map<String, Object>> ventas, LocalDate desde, LocalDate hasta, String grupo) {
        // Todos los tramos del periodo, también los que no tienen ventas (van en cero)
        TreeMap<LocalDate, Map<String, Object>> tramos = new TreeMap<>();
        for (LocalDate d = inicioGrupo(desde, grupo); !d.isAfter(hasta);
             d = switch (grupo) { case "semana" -> d.plusWeeks(1); case "mes" -> d.plusMonths(1); default -> d.plusDays(1); }) {
            LocalDate finTramo = switch (grupo) {
                case "semana" -> d.plusDays(6);
                case "mes" -> d.with(TemporalAdjusters.lastDayOfMonth());
                default -> d;
            };
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("inicio", d.isBefore(desde) ? desde : d);
            t.put("fin", finTramo.isAfter(hasta) ? hasta : finTramo);
            t.put("ingresos", BigDecimal.ZERO);
            t.put("servicios", BigDecimal.ZERO);
            t.put("productos", BigDecimal.ZERO);
            t.put("propinas", BigDecimal.ZERO);
            t.put("ventas", 0);
            tramos.put(d, t);
        }
        for (Map<String, Object> v : ventas) {
            Map<String, Object> t = tramos.get(inicioGrupo(fecha(v.get("fecha_hora")), grupo));
            if (t == null) continue;
            BigDecimal total = num(v.get("total")), prod = num(v.get("productos"));
            t.put("ingresos", num(t.get("ingresos")).add(total));
            t.put("productos", num(t.get("productos")).add(prod));
            t.put("servicios", num(t.get("servicios")).add(total.subtract(prod)));
            t.put("propinas", num(t.get("propinas")).add(num(v.get("propina"))));
            t.put("ventas", (Integer) t.get("ventas") + 1);
        }
        return new ArrayList<>(tramos.values());
    }

    private List<Map<String, Object>> porBarbero(List<Map<String, Object>> ventas) {
        Map<String, Map<String, Object>> m = new LinkedHashMap<>();
        for (Map<String, Object> v : ventas) {
            String nombre = v.get("barbero") == null ? "Sin barbero (venta de mostrador)" : v.get("barbero").toString();
            Map<String, Object> b = m.computeIfAbsent(nombre, k -> {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("barbero", k);
                x.put("ventas", 0);
                x.put("ingresos", BigDecimal.ZERO);
                x.put("propinas", BigDecimal.ZERO);
                return x;
            });
            b.put("ventas", (Integer) b.get("ventas") + 1);
            b.put("ingresos", num(b.get("ingresos")).add(num(v.get("total"))));
            b.put("propinas", num(b.get("propinas")).add(num(v.get("propina"))));
        }
        List<Map<String, Object>> lista = new ArrayList<>(m.values());
        lista.sort((a, b) -> num(b.get("ingresos")).compareTo(num(a.get("ingresos"))));
        return lista;
    }
}
