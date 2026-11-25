package com.milan.barberia.configuracion;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class ConfiguracionWeb implements WebMvcConfigurer {

    private final InterceptorAutenticacion authInterceptor;

    /** Rutas públicas: login, crear cuenta, primer admin y recuperación de contraseña. Swagger está fuera de /api. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/auth/registro", "/api/auth/primer-admin",
                        "/api/auth/recuperar", "/api/auth/restablecer");
    }

    /**
     * Permite llamar a la API desde el frontend abierto con Live Server (puerto 5500),
     * otro puerto local o directamente como archivo. En producción limita los orígenes.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
