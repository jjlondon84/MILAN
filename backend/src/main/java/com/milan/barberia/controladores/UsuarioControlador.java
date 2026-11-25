package com.milan.barberia.controladores;

import com.milan.barberia.excepciones.ExcepcionAccesoDenegado;
import com.milan.barberia.repositorios.ReporteRepositorio;
import com.milan.barberia.servicios.AutenticacionServicio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Gestión de usuarios y roles. Solo para el administrador. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "Usuarios del sistema y roles (solo administrador)")
public class UsuarioControlador {

    public record NuevoUsuario(@NotBlank String nombreUsuario,
                               @NotBlank @Size(min = 6, message = "Mínimo 6 caracteres") String contrasena,
                               @NotNull Integer idRol,
                               String nombreCompleto,
                               @Email String email) {}

    public record EditarUsuario(String nombreCompleto, @Email String email, Integer idRol, Boolean activo) {}

    private final AutenticacionServicio auth;
    private final ReporteRepositorio reportes;

    @Operation(summary = "Listar usuarios")
    @GetMapping("/usuarios")
    public List<AutenticacionServicio.UsuarioResponse> listar(@Parameter(hidden = true) @RequestAttribute("sesion") AutenticacionServicio.Sesion sesion) {
        soloAdmin(sesion);
        return auth.listarUsuarios();
    }

    @Operation(summary = "Crear usuario", description = "La contraseña se guarda como bcrypt(SHA-256) y el correo cifrado con AES")
    @PostMapping("/usuarios")
    @ResponseStatus(HttpStatus.CREATED)
    public AutenticacionServicio.UsuarioResponse crear(@Parameter(hidden = true) @RequestAttribute("sesion") AutenticacionServicio.Sesion sesion,
                                             @Valid @RequestBody NuevoUsuario r) {
        soloAdmin(sesion);
        return auth.crearUsuario(r.nombreUsuario(), r.contrasena(), r.idRol(), r.nombreCompleto(), r.email());
    }

    @Operation(summary = "Editar usuario (nombre, correo, rol o activar/desactivar)")
    @PutMapping("/usuarios/{id}")
    public AutenticacionServicio.UsuarioResponse editar(@Parameter(hidden = true) @RequestAttribute("sesion") AutenticacionServicio.Sesion sesion,
                                              @PathVariable Integer id, @Valid @RequestBody EditarUsuario r) {
        soloAdmin(sesion);
        if (id.equals(sesion.idUsuario()) && Boolean.FALSE.equals(r.activo())) {
            throw new ExcepcionAccesoDenegado("No puedes desactivar tu propio usuario");
        }
        return auth.actualizarUsuario(id, r.nombreCompleto(), r.email(), r.idRol(), r.activo());
    }

    @Operation(summary = "Listar roles")
    @GetMapping("/roles")
    public List<Map<String, Object>> roles() {
        return reportes.roles();
    }

    private void soloAdmin(AutenticacionServicio.Sesion sesion) {
        if (!sesion.admin()) {
            throw new ExcepcionAccesoDenegado("Solo el administrador puede gestionar usuarios");
        }
    }
}
