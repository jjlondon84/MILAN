package com.milan.barberia.excepciones;

/** Error de regla de negocio (datos inválidos, horario ocupado, stock insuficiente...). Responde 400. */
public class ExcepcionNegocio extends RuntimeException {
    public ExcepcionNegocio(String mensaje) {
        super(mensaje);
    }
}
