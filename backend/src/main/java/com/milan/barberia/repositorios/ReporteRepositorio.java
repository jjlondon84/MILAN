package com.milan.barberia.repositorios;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Patrón Repository para los reportes: encapsula las consultas SQL a las vistas y
 * procedimientos almacenados de MySQL, para que los servicios no escriban SQL.
 */
@Repository
public class ReporteRepositorio {

    private final JdbcTemplate jdbc;

    public ReporteRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Procedimiento sp_comisiones: comisión de cada barbero en el periodo. */
    public List<Map<String, Object>> comisiones(LocalDate desde, LocalDate hasta) {
        return jdbc.queryForList("CALL sp_comisiones(?, ?)", Date.valueOf(desde), Date.valueOf(hasta));
    }

    /** Vista v_arqueo_caja. */
    public List<Map<String, Object>> arqueo(Integer idCaja) {
        return jdbc.queryForList("SELECT * FROM v_arqueo_caja WHERE id_caja = ?", idCaja);
    }

    /** Vista v_productos_stock_bajo. */
    public List<Map<String, Object>> stockBajo() {
        return jdbc.queryForList("SELECT * FROM v_productos_stock_bajo ORDER BY (stock_actual / NULLIF(stock_minimo, 0)), nombre");
    }

    /** Vista v_clientes_resumen: visitas, última visita y próxima cita de cada cliente. */
    public List<Map<String, Object>> resumenClientes() {
        return jdbc.queryForList("SELECT * FROM v_clientes_resumen");
    }

    public List<Map<String, Object>> roles() {
        return jdbc.queryForList("SELECT id_rol AS idRol, nombre FROM rol ORDER BY id_rol");
    }

    /** Total vendido (ventas pagadas) en un día. */
    public BigDecimal ingresosDelDia(LocalDate dia) {
        BigDecimal total = jdbc.queryForObject(
                "SELECT IFNULL(SUM(total), 0) FROM venta WHERE estado = 'pagada' AND DATE(fecha_hora) = ?",
                BigDecimal.class, Date.valueOf(dia));
        return total == null ? BigDecimal.ZERO : total;
    }

    /** Ingresos por día en un rango (vista v_ingresos_diarios). */
    public List<Map<String, Object>> ingresosPorDia(LocalDate desde, LocalDate hasta) {
        return jdbc.queryForList("SELECT * FROM v_ingresos_diarios WHERE fecha BETWEEN ? AND ? ORDER BY fecha",
                Date.valueOf(desde), Date.valueOf(hasta));
    }

    /** Citas del día que no fueron canceladas. */
    public long citasDelDia(LocalDate dia) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM cita WHERE DATE(fecha_hora_inicio) = ? AND estado NOT IN ('cancelada')",
                Long.class, Date.valueOf(dia));
        return n == null ? 0 : n;
    }

    /** Citas pendientes o confirmadas que aún no empiezan. */
    public long citasProximas(LocalDateTime desde, LocalDateTime hasta) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM cita WHERE fecha_hora_inicio >= ? AND fecha_hora_inicio < ? "
                        + "AND estado IN ('programada','confirmada')",
                Long.class, Timestamp.valueOf(desde), Timestamp.valueOf(hasta));
        return n == null ? 0 : n;
    }
}
