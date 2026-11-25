package com.milan.barberia.configuracion;

import com.milan.barberia.repositorios.UsuarioRepositorio;
import com.milan.barberia.servicios.AutenticacionServicio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Al arrancar, si la tabla usuario está vacía crea el administrador definido en .env
 * (APP_ADMIN_USUARIO / APP_ADMIN_CONTRASENA). Así la contraseña queda hasheada por la
 * aplicación y no hay que escribir hashes a mano en el script SQL.
 */
@Component
public class CreadorAdministradorInicial implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CreadorAdministradorInicial.class);

    private final UsuarioRepositorio usuarioRepo;
    private final AutenticacionServicio auth;
    private final String usuario;
    private final String contrasena;
    private final String nombre;
    private final String email;

    public CreadorAdministradorInicial(UsuarioRepositorio usuarioRepo, AutenticacionServicio auth,
                           @Value("${app.admin.usuario:admin}") String usuario,
                           @Value("${app.admin.contrasena:Milan2026*}") String contrasena,
                           @Value("${app.admin.nombre:Administrador MILAN}") String nombre,
                           @Value("${app.admin.email:}") String email) {
        this.usuarioRepo = usuarioRepo;
        this.auth = auth;
        this.usuario = usuario;
        this.contrasena = contrasena;
        this.nombre = nombre;
        this.email = email;
    }

    @Override
    public void run(String... args) {
        try {
            if (usuarioRepo.count() == 0) {
                auth.crearPrimerAdmin(usuario, contrasena, nombre, email);
                log.info("Administrador inicial creado: usuario '{}' (cambia la contraseña después de entrar)", usuario);
            }
        } catch (Exception e) {
            log.warn("No se pudo crear el administrador inicial: {}. ¿Ejecutaste db/barberia_milan.sql en MySQL Workbench?",
                    e.getMessage());
        }
    }
}
