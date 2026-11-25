-- =====================================================================
--  DATOS DE EJEMPLO — BARBERÍA MILAN (opcional)
--  Ejecutar DESPUÉS de barberia_milan.sql, en MySQL Workbench.
--  Carga barberos, horarios, clientes, productos y algunas citas para
--  que el panel muestre información desde el primer momento.
--  (Los correos de clientes se dejan vacíos: los cifra la aplicación
--   cuando se registran desde el frontend o Postman.)
-- =====================================================================
USE barberia_milan;

-- Barberos
INSERT INTO barbero (documento, nombres, apellidos, telefono, fecha_ingreso, estado) VALUES
  ('1010101010', 'Mateo',  'Vélez', '3001112233', '2024-02-01', 'activo'),
  ('1020202020', 'Andrés', 'Ortiz', '3002223344', '2024-05-15', 'activo'),
  ('1030303030', 'Camilo', 'Ruiz',  '3003334455', '2025-01-10', 'activo'),
  ('1040404040', 'Julián', 'Pérez', '3004445566', '2025-08-20', 'activo');

-- Horarios: los 4 barberos trabajan todos los días (1=lunes ... 7=domingo)
INSERT INTO turno_laboral (id_barbero, dia_semana, hora_inicio, hora_fin, descanso_inicio, descanso_fin)
SELECT b.id_barbero, d.dia,
       CASE b.id_barbero WHEN 1 THEN '08:00' WHEN 2 THEN '09:00' WHEN 3 THEN '10:00' ELSE '08:00' END,
       CASE b.id_barbero WHEN 1 THEN '17:00' WHEN 2 THEN '18:00' WHEN 3 THEN '19:00' ELSE '17:00' END,
       CASE b.id_barbero WHEN 1 THEN '12:30' WHEN 2 THEN '13:00' WHEN 3 THEN '14:00' ELSE '12:00' END,
       CASE b.id_barbero WHEN 1 THEN '13:30' WHEN 2 THEN '14:00' WHEN 3 THEN '15:00' ELSE '13:00' END
FROM barbero b
CROSS JOIN (SELECT 1 AS dia UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
            UNION SELECT 5 UNION SELECT 6 UNION SELECT 7) d;

-- Julián tiene hoy día libre
INSERT INTO ausencia (id_barbero, tipo, fecha_inicio, fecha_fin, motivo)
VALUES (4, 'dia_libre', CURDATE(), CURDATE(), 'Día libre programado');

-- Clientes
INSERT INTO cliente (nombres, apellidos, telefono, preferencias_corte) VALUES
  ('Santiago',  'Rojas',   '3004821190', 'Degradado bajo'),
  ('Daniel',    'Muñoz',   '3157409231', 'Barba corta perfilada'),
  ('Felipe',    'Castro',  '3015520874', 'Corte clásico tijera'),
  ('Nicolás',   'Gómez',   '3204786625', 'Fade medio'),
  ('Valentina', 'Díaz',    '3109876543', 'Tratamiento hidratante');

-- Productos (venta = retail, insumo = uso interno)
INSERT INTO producto (nombre, tipo, precio_venta, porcentaje_comision, stock_actual, stock_minimo) VALUES
  ('Cera mate MILAN',   'venta',  35000, 10,  2,  5),
  ('Hojas de afeitar',  'insumo', NULL,   0,  8, 20),
  ('Aceite para barba', 'venta',  42000, 10, 14,  6),
  ('Talco profesional', 'insumo', NULL,   0,  9,  5),
  ('Shampoo anticaída', 'venta',  48000, 10,  3,  4),
  ('Gel fijador',       'venta',  22000, 10,  1,  5);

-- Citas de hoy (precio y comisión se copian del catálogo, como hace la app)
INSERT INTO cita (id_cliente, id_barbero, fecha_hora_inicio, fecha_hora_fin, estado) VALUES
  (1, 1, TIMESTAMP(CURDATE(), '09:00'), TIMESTAMP(CURDATE(), '09:45'), 'confirmada'),
  (2, 2, TIMESTAMP(CURDATE(), '10:30'), TIMESTAMP(CURDATE(), '11:30'), 'en_atencion'),
  (3, 1, TIMESTAMP(CURDATE(), '14:00'), TIMESTAMP(CURDATE(), '14:30'), 'confirmada'),
  (4, 2, TIMESTAMP(CURDATE(), '14:30'), TIMESTAMP(CURDATE(), '15:15'), 'programada'),
  (5, 3, TIMESTAMP(CURDATE(), '10:00'), TIMESTAMP(CURDATE(), '10:50'), 'programada');

INSERT INTO cita_servicio (id_cita, id_servicio, precio_aplicado, porcentaje_comision_aplicado)
SELECT x.id_cita, s.id_servicio, s.precio, s.porcentaje_comision
FROM (SELECT 1 AS id_cita, 'Corte clásico' AS servicio
      UNION ALL SELECT 2, 'Corte + barba'
      UNION ALL SELECT 3, 'Perfilado de barba'
      UNION ALL SELECT 4, 'Corte premium'
      UNION ALL SELECT 5, 'Tratamiento capilar') x
JOIN servicio s ON s.nombre = x.servicio;

-- Comprobación:
-- SELECT * FROM v_productos_stock_bajo;
-- SELECT * FROM v_clientes_resumen;
