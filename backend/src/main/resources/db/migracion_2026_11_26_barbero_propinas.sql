-- =====================================================================
--  MIGRACIÓN 26-nov-2026 — Parte del barbero, "Terminé" y propinas
--  Para una base barberia_milan que YA existe (no borra datos).
--  Ejecutar UNA sola vez en MySQL Workbench (rayo ⚡ con todo el script).
--  Si creas la base desde cero con barberia_milan.sql, NO hace falta.
-- =====================================================================
USE barberia_milan;

-- 1) Fichas de barbero creadas desde "Crear cuenta": documento y teléfono los completa el admin
ALTER TABLE barbero
  MODIFY documento VARCHAR(20) NULL COMMENT 'Lo completa el administrador si la ficha se creó desde Crear cuenta',
  MODIFY telefono  VARCHAR(20) NULL;

-- 2) Citas: nuevo estado "terminada", hora real de fin y propina directa al barbero
ALTER TABLE cita
  MODIFY estado ENUM('programada','confirmada','en_atencion','terminada','completada','cancelada','no_asistio')
         NOT NULL DEFAULT 'programada',
  ADD COLUMN fecha_hora_fin_real DATETIME NULL COMMENT 'Cuando el barbero pulsó Terminé' AFTER observaciones,
  ADD COLUMN propina DECIMAL(10,2) NOT NULL DEFAULT 0
         COMMENT 'Propina que el cliente le dio directo al barbero (100% del barbero)' AFTER fecha_hora_fin_real;

-- 3) Ventas: propina cobrada en caja (los pagos suman total + propina)
ALTER TABLE venta
  ADD COLUMN propina DECIMAL(10,2) NOT NULL DEFAULT 0
         COMMENT 'Propina cobrada en caja; es 100% del barbero. Pagos = total + propina' AFTER total,
  ADD CONSTRAINT ck_venta_propina CHECK (propina >= 0);

-- 4) Triggers de cruce de citas (ahora cuentan también las citas "terminada")
DROP TRIGGER IF EXISTS trg_cita_no_solapar_insert;
DROP TRIGGER IF EXISTS trg_cita_no_solapar_update;
DELIMITER $$
CREATE TRIGGER trg_cita_no_solapar_insert
BEFORE INSERT ON cita
FOR EACH ROW
BEGIN
  IF NEW.estado IN ('programada','confirmada','en_atencion','terminada','completada') THEN
    IF EXISTS (SELECT 1 FROM cita c
               WHERE c.id_barbero = NEW.id_barbero
                 AND c.estado IN ('programada','confirmada','en_atencion','terminada','completada')
                 AND c.fecha_hora_inicio < NEW.fecha_hora_fin
                 AND c.fecha_hora_fin    > NEW.fecha_hora_inicio) THEN
      SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'El barbero ya tiene una cita en ese horario';
    END IF;
  END IF;
END$$

-- Misma regla al reprogramar (excluye la propia cita)
CREATE TRIGGER trg_cita_no_solapar_update
BEFORE UPDATE ON cita
FOR EACH ROW
BEGIN
  IF NEW.estado IN ('programada','confirmada','en_atencion','terminada','completada') THEN
    IF EXISTS (SELECT 1 FROM cita c
               WHERE c.id_barbero = NEW.id_barbero
                 AND c.id_cita <> OLD.id_cita
                 AND c.estado IN ('programada','confirmada','en_atencion','terminada','completada')
                 AND c.fecha_hora_inicio < NEW.fecha_hora_fin
                 AND c.fecha_hora_fin    > NEW.fecha_hora_inicio) THEN
      SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'El barbero ya tiene una cita en ese horario';
    END IF;
  END IF;
END$$

-- Entrada/salida manual de inventario actualiza el stock
-- (si una salida deja el stock negativo, el CHECK de producto la rechaza)

DELIMITER ;

-- 5) Arqueo de caja con propinas aparte
DROP VIEW IF EXISTS v_arqueo_caja;
CREATE VIEW v_arqueo_caja AS
SELECT
  c.id_caja,
  c.fecha_hora_apertura,
  c.fecha_hora_cierre,
  c.monto_apertura,
  IFNULL(pg.efectivo, 0)                                  AS total_efectivo,
  IFNULL(pg.transferencia, 0)                             AS total_transferencia,
  IFNULL(pg.tarjeta, 0)                                   AS total_tarjeta,
  IFNULL(pg.cobrado, 0) - IFNULL(vt.propinas, 0)          AS total_vendido,
  IFNULL(vt.propinas, 0)                                  AS total_propinas,
  IFNULL(pg.cobrado, 0)                                   AS total_cobrado,
  c.monto_apertura + IFNULL(pg.efectivo, 0)               AS efectivo_esperado,
  c.efectivo_contado,
  c.efectivo_contado - (c.monto_apertura + IFNULL(pg.efectivo, 0)) AS diferencia
FROM caja c
LEFT JOIN (
    SELECT v.id_caja,
           SUM(CASE WHEN p.medio_pago = 'efectivo'      THEN p.monto END) AS efectivo,
           SUM(CASE WHEN p.medio_pago = 'transferencia' THEN p.monto END) AS transferencia,
           SUM(CASE WHEN p.medio_pago = 'tarjeta'       THEN p.monto END) AS tarjeta,
           SUM(p.monto) AS cobrado
      FROM venta v JOIN pago p ON p.id_venta = v.id_venta
     WHERE v.estado = 'pagada'
     GROUP BY v.id_caja
) pg ON pg.id_caja = c.id_caja
LEFT JOIN (
    SELECT id_caja, SUM(propina) AS propinas
      FROM venta WHERE estado = 'pagada'
     GROUP BY id_caja
) vt ON vt.id_caja = c.id_caja;

-- 6) Comisiones con propinas y total a pagar
DROP PROCEDURE IF EXISTS sp_comisiones;
DELIMITER $$
CREATE PROCEDURE sp_comisiones (IN p_desde DATE, IN p_hasta DATE)
BEGIN
  SELECT
    b.id_barbero,
    CONCAT(b.nombres, ' ', b.apellidos)              AS barbero,
    IFNULL(cs.cantidad_servicios, 0)                 AS cantidad_servicios,
    IFNULL(cs.total_servicios, 0)                    AS total_servicios,
    IFNULL(cs.comision, 0)                           AS comision_servicios,
    IFNULL(cp.cantidad_productos, 0)                 AS cantidad_productos,
    IFNULL(cp.total_productos, 0)                    AS total_productos,
    IFNULL(cs.total_servicios, 0) + IFNULL(cp.total_productos, 0) AS ventas_generadas,
    IFNULL(cp.comision, 0)                           AS comision_productos,
    IFNULL(cs.comision, 0) + IFNULL(cp.comision, 0)  AS comision_total,
    IFNULL(pr.cantidad_propinas, 0)                  AS cantidad_propinas,
    IFNULL(pr.propinas, 0)                           AS propinas,
    IFNULL(cs.comision, 0) + IFNULL(cp.comision, 0) + IFNULL(pr.propinas, 0) AS total_a_pagar
  FROM barbero b
  LEFT JOIN (
      SELECT c.id_barbero,
             COUNT(*)               AS cantidad_servicios,
             SUM(x.precio_aplicado) AS total_servicios,
             SUM(x.precio_aplicado * x.porcentaje_comision_aplicado / 100) AS comision
        FROM cita c
        JOIN cita_servicio x ON x.id_cita = c.id_cita
       WHERE c.estado = 'completada'
         AND DATE(c.fecha_hora_inicio) BETWEEN p_desde AND p_hasta
       GROUP BY c.id_barbero
  ) cs ON cs.id_barbero = b.id_barbero
  LEFT JOIN (
      SELECT v.id_barbero,
             SUM(d.cantidad) AS cantidad_productos,
             SUM(d.subtotal) AS total_productos,
             SUM(d.subtotal * d.porcentaje_comision_aplicado / 100) AS comision
        FROM venta v
        JOIN detalle_venta d ON d.id_venta = v.id_venta
       WHERE v.estado = 'pagada'
         AND v.id_barbero IS NOT NULL
         AND DATE(v.fecha_hora) BETWEEN p_desde AND p_hasta
       GROUP BY v.id_barbero
  ) cp ON cp.id_barbero = b.id_barbero
  LEFT JOIN (
      -- Propinas: las que el barbero registró al terminar la cita + las cobradas en caja
      SELECT t.id_barbero, COUNT(*) AS cantidad_propinas, SUM(t.valor) AS propinas
        FROM (
          SELECT c.id_barbero, c.propina AS valor
            FROM cita c
           WHERE c.propina > 0
             AND c.estado IN ('terminada','completada')
             AND DATE(c.fecha_hora_inicio) BETWEEN p_desde AND p_hasta
          UNION ALL
          SELECT v.id_barbero, v.propina
            FROM venta v
           WHERE v.propina > 0
             AND v.estado = 'pagada'
             AND v.id_barbero IS NOT NULL
             AND DATE(v.fecha_hora) BETWEEN p_desde AND p_hasta
        ) t
       GROUP BY t.id_barbero
  ) pr ON pr.id_barbero = b.id_barbero
  ORDER BY total_a_pagar DESC;
END$$
DELIMITER ;

-- 7) Cuentas de barbero creadas antes de esta versión: se les crea su ficha y se vinculan
INSERT INTO barbero (id_usuario, nombres, apellidos, fecha_ingreso, estado)
SELECT u.id_usuario,
       SUBSTRING_INDEX(COALESCE(NULLIF(TRIM(u.nombre_completo), ''), u.nombre_usuario), ' ', 1),
       COALESCE(NULLIF(TRIM(SUBSTRING(TRIM(u.nombre_completo), LENGTH(SUBSTRING_INDEX(TRIM(u.nombre_completo), ' ', 1)) + 1)), ''), '-'),
       CURDATE(), 'activo'
  FROM usuario u
  JOIN rol r ON r.id_rol = u.id_rol AND r.nombre = 'barbero'
 WHERE NOT EXISTS (SELECT 1 FROM barbero b WHERE b.id_usuario = u.id_usuario);

-- Comprobación:
-- SHOW COLUMNS FROM cita;   SELECT * FROM v_arqueo_caja;   CALL sp_comisiones('2026-11-01','2026-11-30');
