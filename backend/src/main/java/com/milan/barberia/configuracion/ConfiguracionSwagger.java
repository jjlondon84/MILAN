package com.milan.barberia.configuracion;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI: http://localhost:8080/swagger-ui.html
 * 1) Ejecuta POST /api/auth/login, 2) copia el token, 3) pulsa "Authorize" y pégalo.
 */
@Configuration
public class ConfiguracionSwagger {

    @Bean
    public OpenAPI barberiaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("API Barbería MILAN")
                        .version("1.0.0")
                        .description("Gestión de citas, personal, clientes, servicios, inventario, caja y comisiones.")
                        .contact(new Contact().name("Juan Jacobo Camacho Londoño y Wilmar Martínez Qitumbo")))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")
                                .description("Token que devuelve POST /api/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
