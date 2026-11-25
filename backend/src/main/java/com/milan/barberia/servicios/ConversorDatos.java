package com.milan.barberia.servicios;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/** Conversión de los valores que devuelve JDBC (vistas y procedimientos) a tipos Java. */
final class ConversorDatos {

    private ConversorDatos() {
    }

    static long aLong(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    static BigDecimal aDecimal(Object o) {
        if (o instanceof BigDecimal b) {
            return b;
        }
        return o instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : BigDecimal.ZERO;
    }

    static LocalDateTime aFechaHora(Object o) {
        if (o instanceof LocalDateTime l) {
            return l;
        }
        if (o instanceof Timestamp t) {
            return t.toLocalDateTime();
        }
        return null;
    }
}
