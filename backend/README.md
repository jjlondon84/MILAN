# Barbería MILAN — Backend (Spring Boot + MySQL) y conexión con el Frontend

Sistema de gestión de la barbería: agenda de citas sin cruces, personal (turnos, descansos y ausencias),
clientes, catálogo de servicios, inventario, caja con pagos mixtos y liquidación de comisiones.

```
MILAN/
├── Fronted/                 ← pantallas (HTML + CSS + JS) conectadas a la API
│   ├── index.html           login + recuperación de contraseña
│   ├── app.html             panel (Dashboard, Agenda, Personal, Servicios, Comisiones, Clientes, Inventario, Caja)
│   ├── js/api.js            conexión con el backend (token, errores, formatos)
│   ├── js/login.js · js/app.js
│   └── css/styles.css · css/app-extra.css
├── backend/
│   ├── .env                 credenciales (MySQL, clave AES, admin inicial) — NO subir a git
│   ├── .env.example
│   ├── postman/Barberia_MILAN.postman_collection.json
│   └── src/main/resources/db/
│       ├── barberia_milan.sql   ← base de datos completa (ejecutar en MySQL Workbench)
│       └── datos_demo.sql       ← datos de ejemplo (opcional)
└── Documentacion/           guías del curso que se aplicaron (ver sección 7)
```

### Organización del backend (`backend/src/main/java/com/milan/barberia/`)

| Carpeta | Qué contiene | Ejemplos |
|---|---|---|
| `BarberiaMilanAplicacion.java` | El programa principal: se ejecuta para arrancar el backend | — |
| `configuracion/` | Ajustes generales: CORS, Swagger, quién puede entrar y el administrador inicial | `ConfiguracionWeb`, `ConfiguracionSwagger`, `InterceptorAutenticacion`, `CreadorAdministradorInicial` |
| `controladores/` | Reciben las peticiones del frontend/Postman (las rutas `/api/...`) | `CitaControlador` → `/api/citas`, `ClienteControlador` → `/api/clientes`, `PanelControlador` → `/api/dashboard` |
| `servicios/` | Las reglas del negocio (validar cruces, cobrar, calcular comisiones…) | `AgendaServicio` (citas), `VentaServicio`, `CajaServicio`, `PanelServicio` (dashboard) |
| `repositorios/` | Leen y guardan en MySQL (patrón Repository) | `ClienteRepositorio`, `CitaRepositorio`, `ReporteRepositorio` (vistas y procedimientos) |
| `entidades/` | Una clase por cada tabla de la base de datos | `Cita`, `Cliente`, `Barbero`, `Venta` |
| `excepciones/` | Los errores propios y el que los convierte en mensajes `{"error": "..."}` | `ExcepcionNegocio`, `ManejadorErrores` |
| `seguridad/` | Hash de contraseñas y cifrado de datos | `ContrasenaServicio` (bcrypt), `CifradoServicio` (AES), `UtilidadHash` (SHA-256) |

Regla para ubicarse: cada tema tiene su **Controlador** (entrada), su **Servicio** (lógica) y su **Repositorio** (base de datos).
Ej.: Clientes → `ClienteControlador` → `ClienteServicio` → `ClienteRepositorio` → tabla `cliente` (`entidades/Cliente`).

---

## 1. Base de datos en MySQL Workbench (una sola vez)

1. Abre MySQL Workbench y conéctate a tu servidor local (MySQL 8.0.16 o superior).
2. **File → Open SQL Script…** → `backend/src/main/resources/db/barberia_milan.sql`.
3. Ejecuta todo con el rayo ⚡ (sin seleccionar nada). Crea la base `barberia_milan` con:
   - **17 tablas**: `rol`, `usuario`, `sesion`, `recuperacion_contrasena`, `barbero`, `turno_laboral`, `ausencia`,
     `cliente`, `servicio`, `cita`, `cita_servicio`, `producto`, `movimiento_inventario`, `caja`, `venta`,
     `detalle_venta`, `pago`
   - **5 triggers**: impiden cruces de citas, actualizan el stock con entradas/salidas/ventas y lo devuelven al anular.
   - **4 vistas**: `v_productos_stock_bajo`, `v_arqueo_caja`, `v_clientes_resumen`, `v_ingresos_diarios`.
   - **1 procedimiento**: `CALL sp_comisiones('2026-11-01','2026-11-30');`
   - Roles (`administrador`, `barbero`) y el catálogo de servicios del frontend.
4. (Opcional) Abre y ejecuta `datos_demo.sql` para tener barberos, horarios, clientes, productos y citas de hoy.
5. Pulsa el botón de refrescar en el panel **SCHEMAS** para ver `barberia_milan`.

> ⚠️ El script empieza con `DROP DATABASE IF EXISTS barberia_milan;` — si lo vuelves a ejecutar, borra los datos.

### Actualizar una base que ya existe (sin borrar datos)

Si ya tenías `barberia_milan` creada antes de la división barbero/administrador, **no** vuelvas a correr el script completo.
Abre y ejecuta **una sola vez** `backend/src/main/resources/db/migracion_2026_11_26_barbero_propinas.sql`. Agrega:
- el estado `terminada` y las columnas `fecha_hora_fin_real` y `propina` en `cita`;
- la columna `propina` en `venta`;
- la vista `v_arqueo_caja` y el procedimiento `sp_comisiones` con propinas;
- una ficha de barbero para cada cuenta con rol barbero que no tenga una.

## 2. Configurar credenciales (`backend/.env`)

En la carpeta `backend` está **`env.txt`**: renómbralo a **`.env`** (en IntelliJ: clic derecho → *Refactor → Rename*;
en el Explorador de Windows escribe `.env.` con punto final y Windows lo deja como `.env`).
Sin ese archivo el backend no arranca (falta la clave AES). Revisa que el usuario y la clave sean los de tu MySQL:

```properties
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/barberia_milan?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Bogota&characterEncoding=UTF-8
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=1234
APP_AES_KEY=...            # clave AES-256 (no la cambies después de guardar datos)
APP_ADMIN_USUARIO=admin
APP_ADMIN_CONTRASENA=Milan2026*
```

Spring Boot lo lee con `spring.config.import=optional:file:.env[.properties]` (ver `application.properties`).
Para tildes en el `.env` usa escapes: `é` = é, `ñ` = ñ.

## 3. Ejecutar el backend (IntelliJ IDEA)

1. **File → Open** → carpeta `MILAN/backend` (o su `pom.xml` → *Open as Project*).
2. Espera a que Maven descargue las librerías. Si aparece el aviso de Lombok: **Enable annotation processing**.
3. **Build → Rebuild Project** (para limpiar la carpeta `target` antigua).
4. Ejecuta `BarberiaMilanAplicacion` (triángulo verde). Debe terminar en `Started BarberiaMilanAplicacion`.
5. La primera vez se crea automáticamente el administrador del `.env` → **usuario `admin` / contraseña `Milan2026*`**
   (cámbiala desde el panel: clic en tu nombre → *Mi cuenta*).

> La *Working directory* de la configuración de ejecución debe ser la carpeta `backend` (es lo normal en IntelliJ)
> para que encuentre el `.env` y la carpeta `../Fronted`.

## 4. Abrir el frontend

- **Opción A (recomendada):** con el backend corriendo abre **http://localhost:8080** — el backend sirve la carpeta `Fronted`.
- **Opción B:** abre `Fronted/index.html` con Live Server (VS Code) u otro servidor; el frontend detecta que no está
  en el puerto 8080 y llama a `http://localhost:8080/api` (CORS habilitado).

Flujo recomendado la primera vez: **Personal** (crear barbero → *Horario*) → **Clientes** → **Agenda / Nueva cita**
→ **Caja** (abrir) → cobrar la cita → **Comisiones**.

## 5. Swagger (documentación interactiva)

**http://localhost:8080/swagger-ui.html**

1. Ejecuta `POST /api/auth/login` con `{"nombreUsuario":"admin","contrasena":"Milan2026*"}`.
2. Copia el `token` de la respuesta, pulsa **Authorize** y pégalo.
3. Ya puedes probar todos los endpoints (agrupados con `@Tag`, descritos con `@Operation`).

## 6. Postman

1. **Import** → `backend/postman/Barberia_MILAN.postman_collection.json`.
2. Ejecuta **1. Autenticación → Iniciar sesión**: el script guarda el token en la variable `{{token}}`
   y la colección lo envía en todas las peticiones (`Authorization: Bearer {{token}}`).
3. Recorre las carpetas en orden (4 → 10): cada *Crear* guarda su id (`{{idBarbero}}`, `{{idCliente}}`, `{{idCita}}`…).
   La cita de ejemplo se agenda para mañana y el turno del barbero se crea para ese mismo día de la semana.

Todos los errores llegan como `{"error": "mensaje claro"}` con su código HTTP (400, 401, 403, 404, 409).

## 7. Lo que se aplicó de la carpeta *Documentacion*

| Documento | Cómo se aplicó |
|---|---|
| **Guía de hashing de contraseñas** | `ContrasenaServicio`: **pre-hash SHA-256 → bcrypt (costo 12)**. Evita el límite de 72 bytes de bcrypt; la sal va dentro del hash de 60 caracteres. El login compara con `matches`, nunca se "desencripta". |
| **Encriptado de datos** | **Cifrado simétrico AES-256-GCM** (`CifradoServicio` + `ConvertidorCampoCifrado`) para datos personales/financieros que hay que volver a leer: correo de clientes y usuarios, referencias de pago. **Hashing SHA-256** para lo que solo se compara: tokens de sesión y de recuperación, y `email_hash` para buscar correos sin descifrarlos. El **cifrado asimétrico** (RSA/ECC) es el que usa HTTPS/TLS para proteger la comunicación cuando la app se publique. |
| **SpringBoot – encriptación** | Credenciales en archivo **`.env`**, `PasswordEncoder` de Spring Security Crypto, login que responde "usuario o contraseña incorrectos". |
| **Spring y Swagger** | Dependencia `springdoc-openapi-starter-webmvc-ui 2.6.0`, anotaciones `@Tag`, `@Operation`, `@ApiResponses`, `@Parameter`, esquema de seguridad Bearer. |
| **Patrón Repository** | Capas separadas: `controladores` → `servicios` (reglas de negocio) → `repositorios` (acceso a datos). Las consultas SQL a vistas y procedimientos quedaron en `ReporteRepositorio`; ningún controlador escribe SQL. Validaciones con Bean Validation y excepciones propias (`ExcepcionNegocio`, `ExcepcionNoAutorizado`, `ExcepcionAccesoDenegado`). |

## 8. Rutas de la API (`/api`)

- **Autenticación (públicas):** `POST /auth/login`, `POST /auth/registro` (crear cuenta, rol barbero), `POST /auth/primer-admin`, `POST /auth/recuperar`, `POST /auth/restablecer`
- **Sesión:** `GET /auth/me`, `PUT /auth/perfil` (cambiar mi nombre), `PUT /auth/contrasena`, `POST /auth/logout`
- **Usuarios (admin):** `GET/POST /usuarios`, `PUT /usuarios/{id}` (incluye `idBarbero` para vincular la cuenta con su ficha; `0` desvincula), `GET /roles`
- **Mi día (barbero):** `GET /mi-panel`
- **Dashboard:** `GET /dashboard?fecha=`, `GET /buscar?q=`
- **CRUD** (GET, GET /{id}, POST, PUT /{id}, DELETE /{id}): `/servicios`, `/barberos`, `/clientes`, `/productos`, `/turnos`, `/ausencias`
- **Extras:** `/barberos/resumen`, `/barberos/activos`, `/clientes/resumen`, `/clientes/buscar?q=`, `/productos/buscar?q=`,
  `/turnos/barbero/{id}`, `/ausencias/barbero/{id}`
- **Citas:** `GET /citas` (hoy; filtros `idBarbero`, `desde`, `hasta`), `GET /citas/{id}`, `GET /citas/cliente/{id}`,
  `GET /citas/disponibilidad`, `GET /citas/horarios-disponibles`, `POST /citas`, `PUT /citas/{id}/reprogramar`,
  `PATCH /citas/{id}/confirmar | iniciar | cancelar | no-asistio`,
  `GET /citas/{id}/terminar?fechaHoraFinReal=` (vista previa), `POST /citas/{id}/terminar` `{propina, fechaHoraFinReal}`
- **Inventario:** `POST /inventario/movimientos`, `GET /inventario/movimientos/{idProducto}`, `GET /inventario/stock-bajo`
- **Caja y ventas:** `POST /caja/abrir`, `GET /caja/estado`, `GET /caja/actual`, `GET /caja`, `POST /caja/{id}/cerrar`,
  `GET /caja/{id}/arqueo`, `POST /ventas`, `GET /ventas?idCaja=`, `GET /ventas/{id}`, `PATCH /ventas/{id}/anular`
- **Comisiones:** `GET /comisiones?desde=&hasta=`, `GET /comisiones/resumen?desde=&hasta=`
- **Ingresos (admin):** `GET /ingresos?desde=&hasta=&agrupar=dia|semana|mes` — totales, comparación con el periodo anterior, serie para la gráfica, medios de pago, ingresos por barbero y lo más vendido

Estados de una cita: `programada` (pendiente) → `confirmada` → `en_atencion` → `terminada` (el barbero pulsó «Terminé») → `completada` (al cobrar) · `cancelada` · `no_asistio`.

### Qué puede hacer cada rol

| | Administrador | Barbero |
|---|---|---|
| Dashboard, Personal, Servicios, Inventario, Caja, Usuarios | ✅ | ❌ (403) |
| Mi día (`/mi-panel`) | — | ✅ |
| Agenda | Todos los barberos | Solo sus citas; puede agendarse citas a sí mismo |
| Terminé + propina | ✅ | ✅ (sus citas) |
| Cobrar (con propina en caja) | ✅ | ❌ |
| Comisiones | Todos | Solo las suyas (comisión + propinas) |
| Horario y ausencias | Ver y editar | Solo ver el suyo |
| Clientes | Ver, crear, editar, eliminar | Ver y crear |

**Propinas.** Son 100% del barbero y no cuentan como venta ni generan comisión. Se registran de dos formas:
1. El barbero, al pulsar **Terminé**, anota la propina que le dieron en mano (`cita.propina`).
2. Caja, al cobrar, puede sumar una propina que el cliente paga junto con el servicio (`venta.propina`); los pagos deben sumar total + propina.

En `sp_comisiones` salen aparte (`propinas`) y `total_a_pagar = comision_total + propinas`. El arqueo separa `total_vendido` de `total_propinas`.

**Terminé y retraso.** Si el barbero termina después de la hora programada, las citas siguientes del mismo día que se crucen se corren en cascada (solo las que se cruzan; un hueco detiene la cascada). La vista previa avisa si alguna queda fuera del turno o en el descanso.

## 9. Reglas que valida la aplicación

Cliente y barbero existen · barbero activo · cita dentro de su turno y fuera de su descanso · sin licencia/día libre ·
sin cruce con otra cita (también lo impide un trigger) · no en el pasado · el barbero solo ve y toca sus citas · hora de fin = suma de la duración de los
servicios · precio y comisión se congelan al agendar · los pagos suman exactamente el total · solo se cobra con caja
abierta · stock suficiente · anular una venta devuelve el stock · una cita no se cobra dos veces.

## 10. Correcciones realizadas

- El backend apuntaba a la base `milan_barberia` pero el script crea `barberia_milan` → corregido.
- `ddl-auto=update` podía alterar el esquema creado por el script → ahora `none` (el script es la fuente del esquema).
- Contraseña de MySQL escrita en `application.properties` → movida a `.env`.
- Sesiones en memoria (se perdían al reiniciar) → tabla `sesion` con el SHA-256 del token y vencimiento.
- El frontend era una maqueta con datos fijos y el login entraba sin validar → ahora todo lee y guarda en la API.
- Categoría *Combo* y estados *Confirmada / En atención* que mostraba el frontend no existían en la base → agregados.
- Faltaban la recuperación de contraseña, el nombre del usuario en el panel y los indicadores del dashboard → agregados.
- Editar un cliente reiniciaba su fecha de registro → se conserva.
- Errores de MySQL (duplicados, registros en uso) llegaban como texto técnico → mensajes claros.
- Errores no controlados (parámetros faltantes, rutas inexistentes) respondían 500 genérico → ahora 400/404/405 con mensaje.
- `Fronted/login.js` y `Fronted/styles.css` (en la raíz de Fronted) son copias antiguas que ninguna página usa;
  las pantallas usan `js/` y `css/`. Se pueden borrar.
