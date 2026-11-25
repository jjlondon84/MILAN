package com.milan.barberia.excepciones;

/** El usuario no tiene permiso para esta acción. Responde 403. */
public class ExcepcionAccesoDenegado extends RuntimeException {
    public ExcepcionAccesoDenegado(String mensaje) {
        super(mensaje);
    }
}
