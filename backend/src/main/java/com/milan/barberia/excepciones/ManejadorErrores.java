package com.milan.barberia.excepciones;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/** Convierte cualquier error en una respuesta JSON clara: {"error": "mensaje"}. */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(ExcepcionNegocio.class)
    public ResponseEntity<Map<String, Object>> negocio(ExcepcionNegocio e) {
        return respuesta(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(ExcepcionNoAutorizado.class)
    public ResponseEntity<Map<String, Object>> noAutorizado(ExcepcionNoAutorizado e) {
        return respuesta(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(ExcepcionAccesoDenegado.class)
    public ResponseEntity<Map<String, Object>> prohibido(ExcepcionAccesoDenegado e) {
        return respuesta(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> noEncontrado(NoSuchElementException e) {
        return respuesta(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> rutaInexistente(NoResourceFoundException e) {
        return respuesta(HttpStatus.NOT_FOUND, "La ruta /" + e.getResourcePath() + " no existe");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException e) {
        Map<String, String> campos = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(f -> campos.put(f.getField(), f.getDefaultMessage()));
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("error", "Hay datos inválidos o faltantes");
        cuerpo.put("campos", campos);
        return ResponseEntity.badRequest().body(cuerpo);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, Object>> validacionParametros(HandlerMethodValidationException e) {
        return respuesta(HttpStatus.BAD_REQUEST, "Hay datos inválidos o faltantes");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> jsonInvalido(HttpMessageNotReadableException e) {
        return respuesta(HttpStatus.BAD_REQUEST,
                "El cuerpo de la petición no es un JSON válido o tiene un valor mal escrito (fechas: 2026-11-20, horas: 09:00, fecha y hora: 2026-11-20T10:00:00)");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> faltaParametro(MissingServletRequestParameterException e) {
        return respuesta(HttpStatus.BAD_REQUEST, "Falta el parámetro '" + e.getParameterName() + "'");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> faltaCabecera(MissingRequestHeaderException e) {
        return respuesta(HttpStatus.BAD_REQUEST, "Falta la cabecera '" + e.getHeaderName() + "'");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> tipoInvalido(MethodArgumentTypeMismatchException e) {
        return respuesta(HttpStatus.BAD_REQUEST, "El valor '" + e.getValue() + "' no es válido para '" + e.getName() + "'");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> metodoInvalido(HttpRequestMethodNotSupportedException e) {
        return respuesta(HttpStatus.METHOD_NOT_ALLOWED, "Método " + e.getMethod() + " no permitido en esta ruta");
    }

    /** Llaves duplicadas, registros en uso, CHECK y mensajes de los triggers de MySQL. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> integridad(DataIntegrityViolationException e) {
        String detalle = String.valueOf(NestedExceptionUtils.getMostSpecificCause(e).getMessage());
        String mensaje = detalle;
        if (detalle.contains("Duplicate entry")) {
            mensaje = "Ya existe un registro con ese valor (" + detalle.replaceAll(".*Duplicate entry '([^']*)'.*", "$1") + ")";
        } else if (detalle.contains("foreign key constraint fails") && detalle.contains("Cannot delete or update a parent row")) {
            mensaje = "No se puede eliminar: el registro está siendo usado (citas, ventas, horarios...). Puedes desactivarlo";
        } else if (detalle.contains("foreign key constraint fails")) {
            mensaje = "Alguno de los registros relacionados no existe (revisa los id enviados)";
        } else if (detalle.contains("Check constraint")) {
            mensaje = "Un valor no cumple las reglas de la base de datos: " + detalle;
        } else if (detalle.contains("cannot be null")) {
            mensaje = "Falta un dato obligatorio: " + detalle;
        }
        return respuesta(HttpStatus.CONFLICT, mensaje);
    }

    /** Otros errores de MySQL (por ejemplo SIGNAL de los triggers: cruce de citas). */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> baseDeDatos(DataAccessException e) {
        String detalle = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
        return respuesta(HttpStatus.CONFLICT, detalle);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> inesperado(Exception e) {
        log.error("Error inesperado", e);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor: " + e.getMessage());
    }

    private ResponseEntity<Map<String, Object>> respuesta(HttpStatus estado, String mensaje) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("error", mensaje);
        return ResponseEntity.status(estado).body(cuerpo);
    }
}
