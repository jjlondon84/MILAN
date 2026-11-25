package com.milan.barberia.seguridad;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Hash de contraseñas según la "Guía Integral: Hashing y Seguridad de Contraseñas":
 * <pre>
 *   contraseña (cualquier longitud)
 *     -> 1. pre-hash SHA-256 (32 bytes, 44 caracteres Base64)  [evita el límite de 72 bytes de bcrypt]
 *     -> 2. bcrypt con sal aleatoria y factor de costo 12      [lento a propósito]
 *     -> hash de 60 caracteres que se guarda en usuario.contrasena_hash
 * </pre>
 * El login vuelve a calcular el pre-hash y lo compara con {@code matches} (no se "desencripta").
 */
@Service
public class ContrasenaServicio {

    private final BCryptPasswordEncoder bcrypt;

    public ContrasenaServicio(@Value("${app.seguridad.bcrypt-costo:12}") int costo) {
        this.bcrypt = new BCryptPasswordEncoder(costo);
    }

    public String hashear(String contrasena) {
        return bcrypt.encode(UtilidadHash.sha256Base64(contrasena));
    }

    public boolean coincide(String contrasena, String hashGuardado) {
        if (contrasena == null || hashGuardado == null) {
            return false;
        }
        return bcrypt.matches(UtilidadHash.sha256Base64(contrasena), hashGuardado);
    }
}
