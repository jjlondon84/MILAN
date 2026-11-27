package com.milan.barberia.configuracion;

import com.milan.barberia.servicios.AutenticacionServicio;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 1) Bloquea toda la API para quien no haya iniciado sesión (header Authorization: Bearer TOKEN).
 * 2) Divide las partes: el administrador usa todo; el barbero solo las rutas de "su parte".
 *    Que cada barbero vea solo LO SUYO (sus citas, sus comisiones, su horario) lo revisan los controladores.
 */
@Component
@RequiredArgsConstructor
public class InterceptorAutenticacion implements HandlerInterceptor {

    /** Rutas que un barbero puede usar: "MÉTODO patrón". */
    private static final List<String> PARTE_DEL_BARBERO = List.of(
            "* /api/auth/**",
            "GET /api/mi-panel",
            "GET /api/buscar",
            // Su agenda
            "GET /api/citas", "GET /api/citas/*", "GET /api/citas/cliente/*",
            "GET /api/citas/disponibilidad", "GET /api/citas/horarios-disponibles", "GET /api/citas/*/terminar",
            "POST /api/citas", "PUT /api/citas/*/reprogramar",
            "PATCH /api/citas/*/confirmar", "PATCH /api/citas/*/iniciar", "PATCH /api/citas/*/cancelar",
            "PATCH /api/citas/*/no-asistio", "POST /api/citas/*/terminar",
            // Clientes: ver y crear (no editar ni borrar)
            "GET /api/clientes", "GET /api/clientes/*", "POST /api/clientes",
            // Catálogo de servicios (solo ver) y su propia ficha / horario
            "GET /api/servicios", "GET /api/servicios/*",
            "GET /api/barberos/resumen", "GET /api/barberos/activos",
            "GET /api/turnos/barbero/*", "GET /api/ausencias/barbero/*",
            // Sus comisiones
            "GET /api/comisiones", "GET /api/comisiones/resumen");

    private static final AntPathMatcher RUTAS = new AntPathMatcher();

    private final AutenticacionServicio auth;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true; // petición previa del navegador (CORS)
        }
        String cabecera = request.getHeader("Authorization");
        String token = (cabecera != null && cabecera.startsWith("Bearer ")) ? cabecera.substring(7) : null;
        AutenticacionServicio.Sesion sesion = auth.buscarSesion(token);

        if (sesion == null) {
            return responder(response, HttpServletResponse.SC_UNAUTHORIZED, "Debes iniciar sesión");
        }
        if (!sesion.admin() && !esParteDelBarbero(request.getMethod(), request.getRequestURI())) {
            return responder(response, HttpServletResponse.SC_FORBIDDEN,
                    "Esta sección es solo para el administrador");
        }
        request.setAttribute("sesion", sesion);
        return true;
    }

    static boolean esParteDelBarbero(String metodo, String ruta) {
        for (String regla : PARTE_DEL_BARBERO) {
            String[] p = regla.split(" ", 2);
            if ((p[0].equals("*") || p[0].equalsIgnoreCase(metodo)) && RUTAS.match(p[1], ruta)) {
                return true;
            }
        }
        return false;
    }

    private boolean responder(HttpServletResponse response, int estado, String mensaje) throws java.io.IOException {
        response.setStatus(estado);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + mensaje + "\"}");
        return false;
    }
}
