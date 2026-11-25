package com.milan.barberia.servicios;

import com.milan.barberia.excepciones.ExcepcionNegocio;
import com.milan.barberia.excepciones.ExcepcionNoAutorizado;
import com.milan.barberia.entidades.RecuperacionContrasena;
import com.milan.barberia.entidades.Rol;
import com.milan.barberia.entidades.Usuario;
import com.milan.barberia.repositorios.RecuperacionContrasenaRepositorio;
import com.milan.barberia.repositorios.RolRepositorio;
import com.milan.barberia.repositorios.SesionRepositorio;
import com.milan.barberia.repositorios.UsuarioRepositorio;
import com.milan.barberia.seguridad.UtilidadHash;
import com.milan.barberia.seguridad.ContrasenaServicio;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Login, usuarios, sesiones (guardadas en MySQL como SHA-256 del token) y recuperación de contraseña.
 * Las contraseñas se hashean con {@link ContrasenaServicio} (SHA-256 + bcrypt).
 */
@Service
public class AutenticacionServicio {

    public static final String ROL_ADMIN = "administrador";
    public static final String ROL_BARBERO = "barbero";

    /** Datos de la sesión actual que el interceptor deja en cada petición. */
    public record Sesion(Integer idUsuario, String nombreUsuario, boolean admin) {}

    public record UsuarioResponse(Integer idUsuario, String nombreUsuario, String nombreCompleto, String email,
                                  Integer idRol, String rol, Boolean activo, LocalDateTime fechaRegistro) {}

    private final UsuarioRepositorio usuarioRepo;
    private final RolRepositorio rolRepo;
    private final SesionRepositorio sesionRepo;
    private final RecuperacionContrasenaRepositorio recuperacionRepo;
    private final ContrasenaServicio passwords;
    private final int horasSesion;
    private final int minutosRecuperacion;
    private final boolean mostrarTokenRecuperacion;

    public AutenticacionServicio(UsuarioRepositorio usuarioRepo, RolRepositorio rolRepo, SesionRepositorio sesionRepo,
                       RecuperacionContrasenaRepositorio recuperacionRepo, ContrasenaServicio passwords,
                       @Value("${app.sesion.horas:12}") int horasSesion,
                       @Value("${app.recuperacion.minutos:30}") int minutosRecuperacion,
                       @Value("${app.recuperacion.mostrar-token:true}") boolean mostrarTokenRecuperacion) {
        this.usuarioRepo = usuarioRepo;
        this.rolRepo = rolRepo;
        this.sesionRepo = sesionRepo;
        this.recuperacionRepo = recuperacionRepo;
        this.passwords = passwords;
        this.horasSesion = horasSesion;
        this.minutosRecuperacion = minutosRecuperacion;
        this.mostrarTokenRecuperacion = mostrarTokenRecuperacion;
    }

    // ------------------------------------------------------------------ login / sesiones

    @Transactional
    public Map<String, Object> login(String nombreUsuario, String contrasena) {
        Usuario u = usuarioRepo.findByNombreUsuario(nombreUsuario.trim())
                .orElseThrow(() -> new ExcepcionNoAutorizado("Usuario o contraseña incorrectos"));
        if (!passwords.coincide(contrasena, u.getContrasenaHash())) {
            throw new ExcepcionNoAutorizado("Usuario o contraseña incorrectos");
        }
        if (!Boolean.TRUE.equals(u.getActivo())) {
            throw new ExcepcionNoAutorizado("El usuario está inactivo. Habla con el administrador");
        }

        sesionRepo.borrarVencidas(LocalDateTime.now());
        String token = UtilidadHash.tokenAleatorio();
        com.milan.barberia.entidades.Sesion s = new com.milan.barberia.entidades.Sesion();
        s.setTokenHash(UtilidadHash.sha256Hex(token));
        s.setIdUsuario(u.getIdUsuario());
        s.setFechaExpiracion(LocalDateTime.now().plusHours(horasSesion));
        sesionRepo.save(s);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("token", token);
        r.put("tipo", "Bearer");
        r.put("expira", s.getFechaExpiracion());
        r.put("usuario", aRespuesta(u));
        return r;
    }

    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sesionRepo.deleteById(UtilidadHash.sha256Hex(token));
        }
    }

    /** Devuelve la sesión del token si existe, no venció y el usuario sigue activo; si no, null. */
    @Transactional(readOnly = true)
    public Sesion buscarSesion(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return sesionRepo.findByTokenHashAndFechaExpiracionAfter(UtilidadHash.sha256Hex(token), LocalDateTime.now())
                .flatMap(s -> usuarioRepo.findById(s.getIdUsuario()))
                .filter(u -> Boolean.TRUE.equals(u.getActivo()))
                .map(u -> new Sesion(u.getIdUsuario(), u.getNombreUsuario(), ROL_ADMIN.equals(nombreRol(u.getIdRol()))))
                .orElse(null);
    }

    public UsuarioResponse usuarioActual(Sesion sesion) {
        return aRespuesta(buscarUsuario(sesion.idUsuario()));
    }

    // ------------------------------------------------------------------ usuarios

    /** Crea el primer administrador. Solo funciona mientras no exista ningún usuario. */
    @Transactional
    public UsuarioResponse crearPrimerAdmin(String nombreUsuario, String contrasena, String nombreCompleto, String email) {
        if (usuarioRepo.count() > 0) {
            throw new ExcepcionNegocio("Ya existe un administrador; inicia sesión");
        }
        Rol admin = rolRepo.findByNombre(ROL_ADMIN)
                .orElseThrow(() -> new ExcepcionNegocio("Falta el rol 'administrador'. Ejecuta el script SQL completo"));
        return crearUsuario(nombreUsuario, contrasena, admin.getIdRol(), nombreCompleto, email);
    }

    /**
     * "Crear cuenta" desde la pantalla de inicio. Las cuentas nuevas siempre quedan con rol barbero;
     * el administrador se define en el backend (.env) y solo él puede cambiar roles.
     */
    @Transactional
    public UsuarioResponse registrarCuenta(String nombreCompleto, String nombreUsuario, String email,
                                           String contrasena, String confirmacion) {
        if (nombreCompleto == null || nombreCompleto.trim().length() < 3) {
            throw new ExcepcionNegocio("Escribe tu nombre completo");
        }
        if (email == null || email.isBlank()) {
            throw new ExcepcionNegocio("El correo es obligatorio");
        }
        if (contrasena == null || !contrasena.equals(confirmacion)) {
            throw new ExcepcionNegocio("Las contraseñas no coinciden");
        }
        Rol barbero = rolRepo.findByNombre(ROL_BARBERO)
                .orElseThrow(() -> new ExcepcionNegocio("Falta el rol 'barbero'. Ejecuta el script SQL completo"));
        return crearUsuario(nombreUsuario, contrasena, barbero.getIdRol(),
                nombreCompleto.trim().replaceAll("\\s+", " "), email);
    }

    @Transactional
    public UsuarioResponse crearUsuario(String nombreUsuario, String contrasena, Integer idRol,
                                        String nombreCompleto, String email) {
        if (!rolRepo.existsById(idRol)) {
            throw new ExcepcionNegocio("El rol indicado no existe");
        }
        if (usuarioRepo.existsByNombreUsuario(nombreUsuario.trim())) {
            throw new ExcepcionNegocio("Ese nombre de usuario ya está en uso");
        }
        validarContrasena(contrasena);
        Usuario u = new Usuario();
        u.setNombreUsuario(nombreUsuario.trim());
        u.setNombreCompleto(nombreCompleto);
        u.setContrasenaHash(passwords.hashear(contrasena));
        u.setIdRol(idRol);
        asignarEmail(u, email);
        return aRespuesta(usuarioRepo.save(u));
    }

    @Transactional
    public UsuarioResponse actualizarUsuario(Integer idUsuario, String nombreCompleto, String email,
                                             Integer idRol, Boolean activo) {
        Usuario u = buscarUsuario(idUsuario);
        if (idRol != null) {
            if (!rolRepo.existsById(idRol)) {
                throw new ExcepcionNegocio("El rol indicado no existe");
            }
            u.setIdRol(idRol);
        }
        if (nombreCompleto != null) {
            u.setNombreCompleto(nombreCompleto);
        }
        if (email != null) {
            asignarEmail(u, email);
        }
        if (activo != null) {
            u.setActivo(activo);
            if (!activo) {
                sesionRepo.borrarDelUsuario(idUsuario);
            }
        }
        return aRespuesta(usuarioRepo.save(u));
    }

    @Transactional
    public void cambiarContrasena(Integer idUsuario, String actual, String nueva) {
        Usuario u = buscarUsuario(idUsuario);
        if (!passwords.coincide(actual, u.getContrasenaHash())) {
            throw new ExcepcionNegocio("La contraseña actual no es correcta");
        }
        validarContrasena(nueva);
        u.setContrasenaHash(passwords.hashear(nueva));
        usuarioRepo.save(u);
    }

    public List<UsuarioResponse> listarUsuarios() {
        return usuarioRepo.findAll().stream().map(this::aRespuesta).toList();
    }

    // ------------------------------------------------------------------ recuperación

    /**
     * "¿Olvidaste tu contraseña?": genera un token de un solo uso (se guarda su SHA-256).
     * Siempre responde el mismo mensaje para no revelar qué correos existen.
     * Como no hay servidor de correo, en desarrollo (app.recuperacion.mostrar-token=true)
     * el token se devuelve en la respuesta para probar en Postman.
     */
    @Transactional
    public Map<String, Object> solicitarRecuperacion(String email) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("mensaje", "Si el correo está registrado, se envió un enlace para restablecer la contraseña");
        usuarioRepo.findByEmailHash(hashEmail(email))
                .filter(u -> Boolean.TRUE.equals(u.getActivo()))
                .ifPresent(u -> {
                    String token = UtilidadHash.tokenAleatorio();
                    RecuperacionContrasena rc = new RecuperacionContrasena();
                    rc.setIdUsuario(u.getIdUsuario());
                    rc.setTokenHash(UtilidadHash.sha256Hex(token));
                    rc.setFechaExpiracion(LocalDateTime.now().plusMinutes(minutosRecuperacion));
                    recuperacionRepo.save(rc);
                    if (mostrarTokenRecuperacion) {
                        r.put("tokenRecuperacion", token);
                        r.put("expira", rc.getFechaExpiracion());
                    }
                });
        r.put("correoEnmascarado", enmascarar(email));
        return r;
    }

    @Transactional
    public void restablecerContrasena(String token, String nueva) {
        RecuperacionContrasena rc = recuperacionRepo.findByTokenHash(UtilidadHash.sha256Hex(token))
                .orElseThrow(() -> new ExcepcionNegocio("El enlace de recuperación no es válido"));
        if (Boolean.TRUE.equals(rc.getUsado())) {
            throw new ExcepcionNegocio("Ese enlace de recuperación ya fue usado");
        }
        if (rc.getFechaExpiracion().isBefore(LocalDateTime.now())) {
            throw new ExcepcionNegocio("El enlace de recuperación venció; solicita uno nuevo");
        }
        validarContrasena(nueva);
        Usuario u = buscarUsuario(rc.getIdUsuario());
        u.setContrasenaHash(passwords.hashear(nueva));
        usuarioRepo.save(u);
        rc.setUsado(true);
        recuperacionRepo.save(rc);
        sesionRepo.borrarDelUsuario(u.getIdUsuario()); // cierra las sesiones abiertas
    }

    // ------------------------------------------------------------------ apoyo

    private void validarContrasena(String c) {
        if (c == null || c.length() < 6) {
            throw new ExcepcionNegocio("La contraseña debe tener mínimo 6 caracteres");
        }
    }

    private void asignarEmail(Usuario u, String email) {
        if (email == null || email.isBlank()) {
            u.setEmail(null);
            u.setEmailHash(null);
            return;
        }
        String limpio = email.trim().toLowerCase(Locale.ROOT);
        if (!limpio.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ExcepcionNegocio("El correo no es válido");
        }
        String hash = hashEmail(limpio);
        usuarioRepo.findByEmailHash(hash)
                .filter(otro -> !otro.getIdUsuario().equals(u.getIdUsuario()))
                .ifPresent(otro -> {
                    throw new ExcepcionNegocio("Ese correo ya está registrado en otro usuario");
                });
        u.setEmail(limpio);      // se cifra al guardar (ConvertidorCampoCifrado)
        u.setEmailHash(hash);    // permite buscarlo sin descifrar
    }

    private static String hashEmail(String email) {
        return UtilidadHash.sha256Hex(email == null ? "" : email.trim().toLowerCase(Locale.ROOT));
    }

    private static String enmascarar(String email) {
        if (email == null || !email.contains("@")) {
            return "";
        }
        String[] p = email.trim().split("@", 2);
        return (p[0].isEmpty() ? "" : p[0].charAt(0)) + "••••••••@" + p[1];
    }

    private Usuario buscarUsuario(Integer id) {
        return usuarioRepo.findById(id).orElseThrow(() -> new NoSuchElementException("No existe el usuario " + id));
    }

    private UsuarioResponse aRespuesta(Usuario u) {
        return new UsuarioResponse(u.getIdUsuario(), u.getNombreUsuario(), u.getNombreCompleto(), u.getEmail(),
                u.getIdRol(), nombreRol(u.getIdRol()), u.getActivo(), u.getFechaRegistro());
    }

    private String nombreRol(Integer idRol) {
        return idRol == null ? "" : rolRepo.findById(idRol).map(Rol::getNombre).orElse("");
    }
}
