package com.milan.barberia.excepciones;

/** El usuario no inició sesión o sus credenciales son incorrectas. Responde 401. */
public class ExcepcionNoAutorizado extends RuntimeException {
    public ExcepcionNoAutorizado(String mensaje) {
        super(mensaje);
    }
}
