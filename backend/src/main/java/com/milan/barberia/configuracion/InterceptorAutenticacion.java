package com.milan.barberia.configuracion;

import com.milan.barberia.servicios.AutenticacionServicio;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Bloquea toda la API para quien no haya iniciado sesión (header Authorization: Bearer TOKEN). */
@Component
@RequiredArgsConstructor
public class InterceptorAutenticacion implements HandlerInterceptor {

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
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Debes iniciar sesión\"}");
            return false;
        }
        request.setAttribute("sesion", sesion);
        return true;
    }
}
