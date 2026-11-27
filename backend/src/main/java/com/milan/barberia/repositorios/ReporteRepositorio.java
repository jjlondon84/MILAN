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

    /** Propinas cobradas en caja para un barbero en un día. */
    public BigDecimal propinasCajaDelDia(Integer idBarbero, LocalDate dia) {
        BigDecimal total = jdbc.queryForObject(
                "SELECT IFNULL(SUM(propina), 0) FROM venta WHERE estado = 'pagada' AND id_barbero = ? AND DATE(fecha_hora) = ?",
                BigDecimal.class, idBarbero, Date.valueOf(dia));
        return total == null ? BigDecimal.ZERO : total;
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

    // ------------------------------------------------------------------ reporte de ingresos
    /** Cada venta pagada del rango [desde, hasta) con lo que fue de productos y el barbero que la generó. */
    public List<Map<String, Object>> ventasDelPeriodo(LocalDate desde, LocalDate hastaExclusivo) {
        return jdbc.queryForList("""
                SELECT v.id_venta, v.fecha_hora, v.total, v.propina,
                       IFNULL(d.productos, 0) AS productos,
                       b.id_barbero, CONCAT(b.nombres, ' ', b.apellidos) AS barbero
                FROM venta v
                LEFT JOIN (SELECT id_venta, SUM(subtotal) AS productos FROM detalle_venta GROUP BY id_venta) d
                       ON d.id_venta = v.id_venta
                LEFT JOIN cita c ON c.id_cita = v.id_cita
                LEFT JOIN barbero b ON b.id_barbero = COALESCE(c.id_barbero, v.id_barbero)
                WHERE v.estado = 'pagada' AND v.fecha_hora >= ? AND v.fecha_hora < ?
                ORDER BY v.fecha_hora""", Date.valueOf(desde), Date.valueOf(hastaExclusivo));
    }

    /** Lo recibido por medio de pago (incluye la propina cobrada en caja). */
    public List<Map<String, Object>> ingresosPorMedioPago(LocalDate desde, LocalDate hastaExclusivo) {
        return jdbc.queryForList("""
                SELECT p.medio_pago AS medio, SUM(p.monto) AS total, COUNT(DISTINCT p.id_venta) AS ventas
                FROM pago p JOIN venta v ON v.id_venta = p.id_venta
                WHERE v.estado = 'pagada' AND v.fecha_hora >= ? AND v.fecha_hora < ?
                GROUP BY p.medio_pago ORDER BY total DESC""", Date.valueOf(desde), Date.valueOf(hastaExclusivo));
    }

    /** Servicios más vendidos (cobrados) en el periodo. */
    public List<Map<String, Object>> topServicios(LocalDate desde, LocalDate hastaExclusivo, int limite) {
        return jdbc.queryForList("""
                SELECT s.nombre, COUNT(*) AS cantidad, SUM(cs.precio_aplicado) AS total
                FROM venta v
                JOIN cita_servicio cs ON cs.id_cita = v.id_cita
                JOIN servicio s ON s.id_servicio = cs.id_servicio
                WHERE v.estado = 'pagada' AND v.fecha_hora >= ? AND v.fecha_hora < ?
                GROUP BY s.id_servicio, s.nombre ORDER BY total DESC LIMIT ?""",
                Date.valueOf(desde), Date.valueOf(hastaExclusivo), limite);
    }

    /** Productos más vendidos en el periodo. */
    public List<Map<String, Object>> topProductos(LocalDate desde, LocalDate hastaExclusivo, int limite) {
        return jdbc.queryForList("""
                SELECT p.nombre, SUM(d.cantidad) AS cantidad, SUM(d.subtotal) AS total
                FROM venta v
                JOIN detalle_venta d ON d.id_venta = v.id_venta
                JOIN producto p ON p.id_producto = d.id_producto
                WHERE v.estado = 'pagada' AND v.fecha_hora >= ? AND v.fecha_hora < ?
                GROUP BY p.id_producto, p.nombre ORDER BY total DESC LIMIT ?""",
                Date.valueOf(desde), Date.valueOf(hastaExclusivo), limite);
    }

    /** Propinas que los barberos anotaron "en mano" al pulsar Terminé (no pasan por caja). */
    public BigDecimal propinasEnMano(LocalDate desde, LocalDate hastaExclusivo) {
        BigDecimal t = jdbc.queryForObject("""
                SELECT IFNULL(SUM(propina), 0) FROM cita
                WHERE estado IN ('terminada', 'completada')
                  AND COALESCE(fecha_hora_fin_real, fecha_hora_inicio) >= ?
                  AND COALESCE(fecha_hora_fin_real, fecha_hora_inicio) < ?""",
                BigDecimal.class, Date.valueOf(desde), Date.valueOf(hastaExclusivo));
        return t == null ? BigDecimal.ZERO : t;
    }
}
