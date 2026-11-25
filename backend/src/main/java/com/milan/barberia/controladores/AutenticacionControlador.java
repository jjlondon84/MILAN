package com.milan.barberia.controladores;

import com.milan.barberia.servicios.AutenticacionServicio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Inicio y cierre de sesión, recuperación y cambio de contraseña")
public class AutenticacionControlador {

    public record LoginRequest(@NotBlank String nombreUsuario, @NotBlank String contrasena) {}

    public record PrimerAdminRequest(@NotBlank String nombreUsuario,
                                     @NotBlank @Size(min = 6, message = "Mínimo 6 caracteres") String contrasena,
                                     String nombreCompleto,
                                     @Email String email) {}

    public record RegistroRequest(
            @NotBlank(message = "Escribe tu nombre completo") @Size(min = 3, max = 100, message = "Entre 3 y 100 caracteres") String nombreCompleto,
            @NotBlank(message = "Escribe un nombre de usuario") @Size(min = 3, max = 40, message = "Entre 3 y 40 caracteres")
            @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "Solo letras, números, punto, guion y guion bajo (sin espacios)") String nombreUsuario,
            @NotBlank(message = "El correo es obligatorio") @Email(message = "Correo inválido") String email,
            @NotBlank @Size(min = 6, message = "Mínimo 6 caracteres") String contrasena,
            @NotBlank(message = "Repite la contraseña") String confirmarContrasena) {}

    public record RecuperarRequest(@NotBlank @Email String email) {}

    public record RestablecerRequest(@NotBlank String token,
                                     @NotBlank @Size(min = 6, message = "Mínimo 6 caracteres") String nuevaContrasena) {}

    public record CambiarContrasenaRequest(@NotBlank String contrasenaActual,
                                           @NotBlank @Size(min = 6, message = "Mínimo 6 caracteres") String nuevaContrasena) {}

    private final AutenticacionServicio auth;

    @Operation(summary = "Iniciar sesión", description = "Devuelve el token que se envía en la cabecera Authorization: Bearer <token>")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesión iniciada"),
            @ApiResponse(responseCode = "401", description = "Usuario o contraseña incorrectos")
    })
    @SecurityRequirements
    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest r) {
        return auth.login(r.nombreUsuario(), r.contrasena());
    }

    @Operation(summary = "Crear cuenta (registro público)",
            description = "Crea un usuario con rol barbero. La contraseña se guarda como bcrypt(SHA-256) y el correo cifrado con AES")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cuenta creada"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos, contraseñas distintas o usuario/correo ya registrados")
    })
    @SecurityRequirements
    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public AutenticacionServicio.UsuarioResponse registro(@Valid @RequestBody RegistroRequest r) {
        return auth.registrarCuenta(r.nombreCompleto(), r.nombreUsuario(), r.email(), r.contrasena(), r.confirmarContrasena());
    }

    @Operation(summary = "Crear el primer administrador", description = "Solo funciona si la tabla usuario está vacía")
    @SecurityRequirements
    @PostMapping("/primer-admin")
    @ResponseStatus(HttpStatus.CREATED)
    public AutenticacionServicio.UsuarioResponse primerAdmin(@Valid @RequestBody PrimerAdminRequest r) {
        return auth.crearPrimerAdmin(r.nombreUsuario(), r.contrasena(), r.nombreCompleto(), r.email());
    }

    @Operation(summary = "Cerrar sesión")
    @PostMapping("/logout")
    public Map<String, String> logout(@Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String cabecera) {
        if (cabecera != null && cabecera.startsWith("Bearer ")) {
            auth.logout(cabecera.substring(7));
        }
        return Map.of("mensaje", "Sesión cerrada");
    }

    @Operation(summary = "Usuario de la sesión actual", description = "Nombre y rol para mostrar en el panel")
    @GetMapping("/me")
    public AutenticacionServicio.UsuarioResponse yo(@Parameter(hidden = true) @RequestAttribute("sesion") AutenticacionServicio.Sesion sesion) {
        return auth.usuarioActual(sesion);
    }

    @Operation(summary = "¿Olvidaste tu contraseña?",
            description = "Genera un token de recuperación de un solo uso. En desarrollo se devuelve en la respuesta (tokenRecuperacion)")
    @SecurityRequirements
    @PostMapping("/recuperar")
    public Map<String, Object> recuperar(@Valid @RequestBody RecuperarRequest r) {
        return auth.solicitarRecuperacion(r.email());
    }

    @Operation(summary = "Restablecer la contraseña con el token de recuperación")
    @SecurityRequirements
    @PostMapping("/restablecer")
    public Map<String, String> restablecer(@Valid @RequestBody RestablecerRequest r) {
        auth.restablecerContrasena(r.token(), r.nuevaContrasena());
        return Map.of("mensaje", "Contraseña actualizada. Ya puedes iniciar sesión");
    }

    @Operation(summary = "Cambiar mi contraseña")
    @PutMapping("/contrasena")
    public Map<String, String> cambiarContrasena(@Parameter(hidden = true) @RequestAttribute("sesion") AutenticacionServicio.Sesion sesion,
                                                 @Valid @RequestBody CambiarContrasenaRequest r) {
        auth.cambiarContrasena(sesion.idUsuario(), r.contrasenaActual(), r.nuevaContrasena());
        return Map.of("mensaje", "Contraseña actualizada");
    }
}
