# 💈 Barbería MILAN

Sistema web de gestión para una barbería: agenda de citas sin cruces de horario, personal (turnos, descansos y ausencias),
clientes, catálogo de servicios, inventario, caja con pagos mixtos y liquidación de comisiones.

**Integrantes:** Juan Jacobo Camacho Londoño · Wilmar Martínez Qitumbo

## Tecnologías

| Parte | Tecnología |
|---|---|
| Frontend | HTML, CSS y JavaScript (carpeta `Fronted/`) |
| Backend | Java 17 · Spring Boot 3 · Spring Data JPA · Maven (carpeta `backend/`) |
| Base de datos | MySQL 8 (script para MySQL Workbench) |
| Documentación de la API | Swagger (springdoc-openapi) y colección de Postman |
| Seguridad | Contraseñas con SHA-256 + bcrypt · datos sensibles cifrados con AES-256-GCM · credenciales en `.env` |

## Estructura

```
MILAN/
├── Fronted/          pantallas: login, crear cuenta y panel de la barbería
├── backend/          API REST en Spring Boot
│   ├── src/main/java/com/milan/barberia/
│   │   ├── controladores/   reciben las peticiones (/api/...)
│   │   ├── servicios/       reglas del negocio
│   │   ├── repositorios/    acceso a MySQL (patrón Repository)
│   │   ├── entidades/       una clase por tabla
│   │   ├── configuracion/   CORS, Swagger, sesión, admin inicial
│   │   ├── excepciones/     errores y mensajes
│   │   └── seguridad/       hash de contraseñas y cifrado
│   ├── src/main/resources/db/   barberia_milan.sql y datos_demo.sql
│   ├── postman/              colección de Postman
│   └── .env.example          plantilla de credenciales
└── Documentacion/    guías del curso aplicadas en el proyecto
```

## Cómo ejecutarlo

1. **Base de datos:** en MySQL Workbench ejecuta `backend/src/main/resources/db/barberia_milan.sql` (y opcionalmente `datos_demo.sql`).
2. **Credenciales:** copia `backend/.env.example` como `backend/.env` y pon tu usuario y contraseña de MySQL y una clave AES.
3. **Backend:** abre `backend/` en IntelliJ IDEA y ejecuta `BarberiaMilanAplicacion`.
4. **Aplicación:** abre <http://localhost:8080>.
5. **Swagger:** <http://localhost:8080/swagger-ui.html> · **Postman:** importa `backend/postman/Barberia_MILAN.postman_collection.json`.

La guía completa (rutas de la API, reglas de negocio y seguridad) está en [`backend/README.md`](backend/README.md).
