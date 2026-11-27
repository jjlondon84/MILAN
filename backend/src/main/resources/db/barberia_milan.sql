-- =====================================================================
--  BASE DE DATOS: BARBERÍA MILAN
--  Motor: MySQL 8.0 (MySQL Workbench)
--  Integrantes: Juan Jacobo Camacho Londoño y Wilmar Martínez Qitumbo
--
--  Módulos cubiertos:
--   1. Usuarios y acceso seguro (contraseña encriptada)
--   2. Barberos, turnos laborales, descansos y ausencias
--   3. Clientes (historial, preferencias, contacto)
--   4. Catálogo de servicios (precio, duración, % comisión)
--   5. Citas (programar / reprogramar / cancelar, sin solapamiento)
--   6. Inventario (venta e insumos, alerta de stock mínimo)
--   7. Caja, ventas, pagos múltiples y arqueo
--   8. Cálculo automático de comisiones
--   9. Sesiones persistentes y recuperación de contraseña
--
--  Seguridad (ver carpeta Documentacion):
--   - Contraseñas: HASH bcrypt (costo 12) sobre un pre-hash SHA-256.
--     Nunca se guardan en texto plano. Lo hace la aplicación.
--   - Datos sensibles (correos, referencias de pago): CIFRADO AES-256-GCM
--     hecho por la aplicación con la clave del archivo .env.
--   - Tokens de sesión y de recuperación: se guarda solo su SHA-256.
--
--  El primer administrador lo crea el backend al arrancar (usuario y
--  clave en el archivo .env: APP_ADMIN_USUARIO / APP_ADMIN_CONTRASENA).
--  Datos de ejemplo opcionales: ejecutar después datos_demo.sql
--
--  OJO: la primera instrucción borra la base "barberia_milan" si ya
--  existe (solo esa, no toca adso268 ni otras). Sirve para volver a
--  ejecutar el script desde cero.
-- =====================================================================

DROP DATABASE IF EXISTS barberia_milan;
CREATE DATABASE barberia_milan
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
USE barberia_milan;

-- =====================================================================
-- 1. USUARIOS Y ACCESO
-- =====================================================================

CREATE TABLE rol (
  id_rol   TINYINT UNSIGNED NOT NULL AUTO_INCREMENT,
  nombre   VARCHAR(30) NOT NULL,
  PRIMARY KEY (id_rol),
  UNIQUE KEY uq_rol_nombre (nombre)
) ENGINE=InnoDB;

CREATE TABLE usuario (
  id_usuario       INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_rol           TINYINT UNSIGNED NOT NULL,
  nombre_usuario   VARCHAR(40)  NOT NULL,
  nombre_completo  VARCHAR(100) NULL COMMENT 'Nombre que se muestra en el panel',
  email            VARCHAR(255) NULL COMMENT 'Cifrado con AES-256-GCM por la aplicación',
  email_hash       CHAR(64)     NULL COMMENT 'SHA-256 del correo en minúsculas, para buscarlo sin descifrar',
  contrasena_hash  VARCHAR(255) NOT NULL COMMENT 'bcrypt(SHA-256(contraseña)) generado por la aplicación, nunca texto plano',
  activo           TINYINT(1)   NOT NULL DEFAULT 1,
  fecha_registro   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id_usuario),
  UNIQUE KEY uq_usuario_nombre (nombre_usuario),
  UNIQUE KEY uq_usuario_email_hash (email_hash),
  CONSTRAINT fk_usuario_rol FOREIGN KEY (id_rol) REFERENCES rol (id_rol)
) ENGINE=InnoDB;

-- Sesiones abiertas (sobreviven a reinicios del backend).
-- Se guarda el SHA-256 del token, nunca el token.
CREATE TABLE sesion (
  token_hash        CHAR(64)     NOT NULL,
  id_usuario        INT UNSIGNED NOT NULL,
  fecha_creacion    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_expiracion  DATETIME     NOT NULL,
  PRIMARY KEY (token_hash),
  KEY ix_sesion_usuario (id_usuario),
  KEY ix_sesion_expira (fecha_expiracion),
  CONSTRAINT fk_sesion_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Solicitudes de "¿Olvidaste tu contraseña?" (un solo uso, con vencimiento)
CREATE TABLE recuperacion_contrasena (
  id_recuperacion   INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_usuario        INT UNSIGNED NOT NULL,
  token_hash        CHAR(64)     NOT NULL,
  fecha_creacion    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_expiracion  DATETIME     NOT NULL,
  usado             TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (id_recuperacion),
  UNIQUE KEY uq_recuperacion_token (token_hash),
  CONSTRAINT fk_recuperacion_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario) ON DELETE CASCADE
) ENGINE=InnoDB;

-- =====================================================================
-- 2. PERSONAL (BARBEROS), TURNOS Y AUSENCIAS
-- =====================================================================

CREATE TABLE barbero (
  id_barbero     INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_usuario     INT UNSIGNED NULL COMMENT 'Solo si el barbero tiene acceso al sistema',
  documento      VARCHAR(20)  NULL COMMENT 'Lo completa el administrador si la ficha se creó desde Crear cuenta',
  nombres        VARCHAR(60)  NOT NULL,
  apellidos      VARCHAR(60)  NOT NULL,
  telefono       VARCHAR(20)  NULL,
  fecha_ingreso  DATE         NOT NULL,
  estado         ENUM('activo','inactivo') NOT NULL DEFAULT 'activo',
  PRIMARY KEY (id_barbero),
  UNIQUE KEY uq_barbero_documento (documento),
  UNIQUE KEY uq_barbero_usuario (id_usuario),
  CONSTRAINT fk_barbero_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario)
) ENGINE=InnoDB;

-- Horario semanal fijo de cada barbero (con su hora de descanso)
CREATE TABLE turno_laboral (
  id_turno          INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_barbero        INT UNSIGNED NOT NULL,
  dia_semana        TINYINT UNSIGNED NOT NULL COMMENT '1=Lunes ... 7=Domingo',
  hora_inicio       TIME NOT NULL,
  hora_fin          TIME NOT NULL,
  descanso_inicio   TIME NULL,
  descanso_fin      TIME NULL,
  PRIMARY KEY (id_turno),
  UNIQUE KEY uq_turno_barbero_dia (id_barbero, dia_semana),
  CONSTRAINT fk_turno_barbero FOREIGN KEY (id_barbero) REFERENCES barbero (id_barbero),
  CONSTRAINT ck_turno_dia    CHECK (dia_semana BETWEEN 1 AND 7),
  CONSTRAINT ck_turno_horas  CHECK (hora_fin > hora_inicio),
  CONSTRAINT ck_turno_descanso CHECK (
        (descanso_inicio IS NULL AND descanso_fin IS NULL)
     OR (descanso_inicio IS NOT NULL AND descanso_fin IS NOT NULL
         AND descanso_fin > descanso_inicio
         AND descanso_inicio >= hora_inicio AND descanso_fin <= hora_fin))
) ENGINE=InnoDB;

-- Licencias, días libres, incapacidades y vacaciones
CREATE TABLE ausencia (
  id_ausencia   INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_barbero    INT UNSIGNED NOT NULL,
  tipo          ENUM('licencia','dia_libre','incapacidad','vacaciones') NOT NULL,
  fecha_inicio  DATE NOT NULL,
  fecha_fin     DATE NOT NULL,
  motivo        VARCHAR(200) NULL,
  PRIMARY KEY (id_ausencia),
  KEY ix_ausencia_barbero_fechas (id_barbero, fecha_inicio, fecha_fin),
  CONSTRAINT fk_ausencia_barbero FOREIGN KEY (id_barbero) REFERENCES barbero (id_barbero),
  CONSTRAINT ck_ausencia_fechas CHECK (fecha_fin >= fecha_inicio)
) ENGINE=InnoDB;

-- =====================================================================
-- 3. CLIENTES
-- =====================================================================

CREATE TABLE cliente (
  id_cliente           INT UNSIGNED NOT NULL AUTO_INCREMENT,
  nombres              VARCHAR(60)  NOT NULL,
  apellidos            VARCHAR(60)  NOT NULL,
  telefono             VARCHAR(20)  NOT NULL,
  email                VARCHAR(255) NULL COMMENT 'Cifrado con AES-256-GCM por la aplicación',
  preferencias_corte   VARCHAR(300) NULL COMMENT 'Estilo, número de máquina, observaciones técnicas',
  fecha_registro       DATE NOT NULL DEFAULT (CURRENT_DATE),
  PRIMARY KEY (id_cliente),
  UNIQUE KEY uq_cliente_telefono (telefono)
) ENGINE=InnoDB;

-- =====================================================================
-- 4. CATÁLOGO DE SERVICIOS
-- =====================================================================

CREATE TABLE servicio (
  id_servicio          INT UNSIGNED NOT NULL AUTO_INCREMENT,
  nombre               VARCHAR(80) NOT NULL,
  categoria            ENUM('corte','barba','combo','estetico') NOT NULL,
  precio               DECIMAL(10,2) NOT NULL,
  duracion_min         SMALLINT UNSIGNED NOT NULL COMMENT 'Tiempo estimado de atención en minutos',
  porcentaje_comision  DECIMAL(5,2) NOT NULL DEFAULT 0,
  activo               TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (id_servicio),
  UNIQUE KEY uq_servicio_nombre (nombre),
  CONSTRAINT ck_servicio_precio   CHECK (precio >= 0),
  CONSTRAINT ck_servicio_duracion CHECK (duracion_min > 0),
  CONSTRAINT ck_servicio_comision CHECK (porcentaje_comision BETWEEN 0 AND 100)
) ENGINE=InnoDB;

-- =====================================================================
-- 5. CITAS
--    Programar  = INSERT
--    Reprogramar = UPDATE de fecha_hora_inicio / fecha_hora_fin
--    Cancelar   = UPDATE estado = 'cancelada'
--    Flujo de estados: programada (pendiente) -> confirmada -> en_atencion
--                      -> terminada (el barbero pulsó Terminé) -> completada (al cobrar en caja)
--                      |  cancelada  |  no_asistio
-- =====================================================================

CREATE TABLE cita (
  id_cita            INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_cliente         INT UNSIGNED NOT NULL,
  id_barbero         INT UNSIGNED NOT NULL,
  fecha_hora_inicio  DATETIME NOT NULL,
  fecha_hora_fin     DATETIME NOT NULL COMMENT 'Inicio + suma de la duración de los servicios',
  estado             ENUM('programada','confirmada','en_atencion','terminada','completada','cancelada','no_asistio') NOT NULL DEFAULT 'programada',
  observaciones      VARCHAR(200) NULL,
  fecha_hora_fin_real DATETIME NULL COMMENT 'Cuando el barbero pulsó Terminé',
  propina            DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT 'Propina que el cliente le dio directo al barbero (100% del barbero)',
  PRIMARY KEY (id_cita),
  KEY ix_cita_barbero_fecha (id_barbero, fecha_hora_inicio),
  KEY ix_cita_cliente (id_cliente),
  CONSTRAINT fk_cita_cliente FOREIGN KEY (id_cliente) REFERENCES cliente (id_cliente),
  CONSTRAINT fk_cita_barbero FOREIGN KEY (id_barbero) REFERENCES barbero (id_barbero),
  CONSTRAINT ck_cita_horas   CHECK (fecha_hora_fin > fecha_hora_inicio)
) ENGINE=InnoDB;

-- Servicios de cada cita (se guarda el precio y la comisión vigentes ese día)
CREATE TABLE cita_servicio (
  id_cita                      INT UNSIGNED NOT NULL,
  id_servicio                  INT UNSIGNED NOT NULL,
  precio_aplicado              DECIMAL(10,2) NOT NULL,
  porcentaje_comision_aplicado DECIMAL(5,2)  NOT NULL,
  PRIMARY KEY (id_cita, id_servicio),
  CONSTRAINT fk_citaserv_cita     FOREIGN KEY (id_cita)     REFERENCES cita (id_cita) ON DELETE CASCADE,
  CONSTRAINT fk_citaserv_servicio FOREIGN KEY (id_servicio) REFERENCES servicio (id_servicio),
  CONSTRAINT ck_citaserv_precio   CHECK (precio_aplicado >= 0),
  CONSTRAINT ck_citaserv_comision CHECK (porcentaje_comision_aplicado BETWEEN 0 AND 100)
) ENGINE=InnoDB;

-- =====================================================================
-- 6. INVENTARIO
-- =====================================================================

CREATE TABLE producto (
  id_producto          INT UNSIGNED NOT NULL AUTO_INCREMENT,
  nombre               VARCHAR(80) NOT NULL,
  tipo                 ENUM('venta','insumo') NOT NULL COMMENT 'venta = retail al cliente; insumo = uso interno',
  precio_venta         DECIMAL(10,2) NULL COMMENT 'Solo para productos de venta',
  porcentaje_comision  DECIMAL(5,2) NOT NULL DEFAULT 0,
  stock_actual         INT NOT NULL DEFAULT 0,
  stock_minimo         INT NOT NULL DEFAULT 0,
  activo               TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (id_producto),
  UNIQUE KEY uq_producto_nombre (nombre),
  CONSTRAINT ck_producto_stock    CHECK (stock_actual >= 0 AND stock_minimo >= 0),
  CONSTRAINT ck_producto_precio   CHECK (precio_venta IS NULL OR precio_venta >= 0),
  CONSTRAINT ck_producto_comision CHECK (porcentaje_comision BETWEEN 0 AND 100),
  CONSTRAINT ck_producto_venta    CHECK (tipo = 'insumo' OR precio_venta IS NOT NULL)
) ENGINE=InnoDB;

-- Entradas (compras) y salidas (consumo de insumos, mermas, ajustes).
-- Las ventas de productos descuentan stock solas (ver trigger más abajo).
CREATE TABLE movimiento_inventario (
  id_movimiento  INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_producto    INT UNSIGNED NOT NULL,
  id_usuario     INT UNSIGNED NOT NULL,
  tipo           ENUM('entrada','salida') NOT NULL,
  cantidad       INT UNSIGNED NOT NULL,
  motivo         VARCHAR(100) NULL,
  fecha_hora     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id_movimiento),
  KEY ix_mov_producto (id_producto),
  CONSTRAINT fk_mov_producto FOREIGN KEY (id_producto) REFERENCES producto (id_producto),
  CONSTRAINT fk_mov_usuario  FOREIGN KEY (id_usuario)  REFERENCES usuario (id_usuario),
  CONSTRAINT ck_mov_cantidad CHECK (cantidad > 0)
) ENGINE=InnoDB;

-- =====================================================================
-- 7. CAJA, VENTAS Y PAGOS
-- =====================================================================

-- Una caja = una jornada (apertura y cierre con arqueo)
CREATE TABLE caja (
  id_caja              INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_usuario           INT UNSIGNED NOT NULL COMMENT 'Quien abre la caja',
  fecha_hora_apertura  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  monto_apertura       DECIMAL(12,2) NOT NULL DEFAULT 0,
  fecha_hora_cierre    DATETIME NULL,
  efectivo_contado     DECIMAL(12,2) NULL COMMENT 'Efectivo físico contado al cierre',
  estado               ENUM('abierta','cerrada') NOT NULL DEFAULT 'abierta',
  observaciones        VARCHAR(200) NULL,
  PRIMARY KEY (id_caja),
  CONSTRAINT fk_caja_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario),
  CONSTRAINT ck_caja_montos  CHECK (monto_apertura >= 0 AND (efectivo_contado IS NULL OR efectivo_contado >= 0))
) ENGINE=InnoDB;

-- Una venta = el cobro de una atención (cita) y/o de productos de retail
CREATE TABLE venta (
  id_venta     INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_caja      INT UNSIGNED NOT NULL,
  id_cita      INT UNSIGNED NULL COMMENT 'Null si es solo venta de productos',
  id_cliente   INT UNSIGNED NULL COMMENT 'Null si es un cliente ocasional',
  id_barbero   INT UNSIGNED NULL COMMENT 'Barbero que vendió los productos (para su comisión)',
  fecha_hora   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  total        DECIMAL(12,2) NOT NULL COMMENT 'Servicios de la cita + productos (sin propina)',
  propina      DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT 'Propina cobrada en caja; es 100% del barbero. Pagos = total + propina',
  estado       ENUM('pagada','anulada') NOT NULL DEFAULT 'pagada',
  PRIMARY KEY (id_venta),
  KEY ix_venta_cita (id_cita),
  KEY ix_venta_caja (id_caja),
  KEY ix_venta_barbero_fecha (id_barbero, fecha_hora),
  CONSTRAINT fk_venta_caja    FOREIGN KEY (id_caja)    REFERENCES caja (id_caja),
  CONSTRAINT fk_venta_cita    FOREIGN KEY (id_cita)    REFERENCES cita (id_cita),
  CONSTRAINT fk_venta_cliente FOREIGN KEY (id_cliente) REFERENCES cliente (id_cliente),
  CONSTRAINT fk_venta_barbero FOREIGN KEY (id_barbero) REFERENCES barbero (id_barbero),
  CONSTRAINT ck_venta_total   CHECK (total >= 0),
  CONSTRAINT ck_venta_propina CHECK (propina >= 0)
) ENGINE=InnoDB;

-- Productos vendidos dentro de una venta
CREATE TABLE detalle_venta (
  id_detalle                    INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_venta                      INT UNSIGNED NOT NULL,
  id_producto                   INT UNSIGNED NOT NULL,
  cantidad                      INT UNSIGNED NOT NULL,
  precio_unitario               DECIMAL(10,2) NOT NULL,
  porcentaje_comision_aplicado  DECIMAL(5,2)  NOT NULL DEFAULT 0,
  subtotal                      DECIMAL(12,2) GENERATED ALWAYS AS (cantidad * precio_unitario) STORED,
  PRIMARY KEY (id_detalle),
  KEY ix_detalle_venta (id_venta),
  CONSTRAINT fk_detalle_venta    FOREIGN KEY (id_venta)    REFERENCES venta (id_venta) ON DELETE CASCADE,
  CONSTRAINT fk_detalle_producto FOREIGN KEY (id_producto) REFERENCES producto (id_producto),
  CONSTRAINT ck_detalle_cantidad CHECK (cantidad > 0),
  CONSTRAINT ck_detalle_precio   CHECK (precio_unitario >= 0),
  CONSTRAINT ck_detalle_comision CHECK (porcentaje_comision_aplicado BETWEEN 0 AND 100)
) ENGINE=InnoDB;

-- Una venta puede pagarse con varios medios (ej. mitad efectivo, mitad tarjeta)
CREATE TABLE pago (
  id_pago      INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_venta     INT UNSIGNED NOT NULL,
  medio_pago   ENUM('efectivo','transferencia','tarjeta') NOT NULL,
  monto        DECIMAL(12,2) NOT NULL,
  referencia   VARCHAR(255) NULL COMMENT 'N° de transferencia o voucher. Cifrado AES-256-GCM por la aplicación',
  PRIMARY KEY (id_pago),
  KEY ix_pago_venta (id_venta),
  CONSTRAINT fk_pago_venta FOREIGN KEY (id_venta) REFERENCES venta (id_venta),
  CONSTRAINT ck_pago_monto CHECK (monto > 0)
) ENGINE=InnoDB;

-- =====================================================================
-- TRIGGERS (reglas que la base hace cumplir sola)
-- =====================================================================

DELIMITER $$

-- Regla: un barbero no puede tener dos citas activas en la misma franja
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
CREATE TRIGGER trg_movimiento_actualiza_stock
AFTER INSERT ON movimiento_inventario
FOR EACH ROW
BEGIN
  IF NEW.tipo = 'entrada' THEN
    UPDATE producto SET stock_actual = stock_actual + NEW.cantidad
     WHERE id_producto = NEW.id_producto;
  ELSE
    UPDATE producto SET stock_actual = stock_actual - NEW.cantidad
     WHERE id_producto = NEW.id_producto;
  END IF;
END$$

-- Vender un producto descuenta el stock
CREATE TRIGGER trg_detalle_venta_descuenta_stock
AFTER INSERT ON detalle_venta
FOR EACH ROW
BEGIN
  UPDATE producto SET stock_actual = stock_actual - NEW.cantidad
   WHERE id_producto = NEW.id_producto;
END$$

-- Anular una venta devuelve los productos al stock
CREATE TRIGGER trg_venta_anulada_devuelve_stock
AFTER UPDATE ON venta
FOR EACH ROW
BEGIN
  IF OLD.estado = 'pagada' AND NEW.estado = 'anulada' THEN
    UPDATE producto p
      JOIN detalle_venta d ON d.id_producto = p.id_producto
       SET p.stock_actual = p.stock_actual + d.cantidad
     WHERE d.id_venta = NEW.id_venta;
  END IF;
END$$

-- =====================================================================
-- VISTAS (reportes listos para usar)
-- =====================================================================

DELIMITER ;

-- Alerta visual de stock bajo
CREATE VIEW v_productos_stock_bajo AS
SELECT id_producto, nombre, tipo, stock_actual, stock_minimo
FROM producto
WHERE activo = 1
  AND stock_actual <= stock_minimo;

-- Arqueo de caja: lo vendido por medio de pago y la diferencia de efectivo
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

-- Ficha resumida de clientes: visitas atendidas y última visita
CREATE VIEW v_clientes_resumen AS
SELECT
  cl.id_cliente,
  COUNT(CASE WHEN ci.estado = 'completada' THEN 1 END) AS visitas,
  MAX(CASE WHEN ci.estado IN ('completada','en_atencion') THEN ci.fecha_hora_inicio END) AS ultima_visita,
  MIN(CASE WHEN ci.estado IN ('programada','confirmada') AND ci.fecha_hora_inicio >= NOW()
           THEN ci.fecha_hora_inicio END) AS proxima_cita
FROM cliente cl
LEFT JOIN cita ci ON ci.id_cliente = cl.id_cliente
GROUP BY cl.id_cliente;

-- Ingresos (ventas pagadas) por día
CREATE VIEW v_ingresos_diarios AS
SELECT DATE(v.fecha_hora) AS fecha,
       COUNT(*)           AS numero_ventas,
       SUM(v.total)       AS total
FROM venta v
WHERE v.estado = 'pagada'
GROUP BY DATE(v.fecha_hora);

-- =====================================================================
-- PROCEDIMIENTO: comisiones por barbero en un periodo
--   Uso:  CALL sp_comisiones('2026-11-01', '2026-11-15');
-- =====================================================================

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

-- =====================================================================
-- DATOS INICIALES MÍNIMOS
-- =====================================================================

INSERT INTO rol (nombre) VALUES ('administrador'), ('barbero');

-- Ejemplos de catálogo (edítalos desde la aplicación o aquí mismo)
-- (los mismos que muestra el frontend)
INSERT INTO servicio (nombre, categoria, precio, duracion_min, porcentaje_comision) VALUES
  ('Corte clásico',        'corte',    38000, 45, 40),
  ('Corte + barba',        'combo',    58000, 60, 45),
  ('Perfilado de barba',   'barba',    28000, 30, 40),
  ('Tratamiento capilar',  'estetico', 65000, 50, 35),
  ('Corte premium',        'corte',    45000, 45, 45);

-- El primer usuario administrador lo crea el backend al arrancar
-- (la contraseña debe guardarse ya hasheada, por eso no se inserta aquí).

-- Usuario de MySQL para la aplicación (opcional; si lo usas, ponlo en .env)
-- CREATE USER IF NOT EXISTS 'milan_app'@'localhost' IDENTIFIED BY 'una_clave_segura';
-- GRANT ALL PRIVILEGES ON barberia_milan.* TO 'milan_app'@'localhost';

-- Comprobación rápida:
-- SHOW TABLES;
