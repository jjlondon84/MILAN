package com.milan.barberia.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Funciones de hash rápidas (SHA-256).
 * Se usan para: pre-hash de contraseñas antes de bcrypt, guardar tokens de sesión /
 * recuperación sin exponerlos, y buscar correos cifrados sin descifrarlos.
 * OJO: SHA-256 solo NO sirve para guardar contraseñas (ver guía de hashing);
 * para eso está {@link ContrasenaServicio} (bcrypt).
 */
public final class UtilidadHash {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UtilidadHash() {
    }

    public static byte[] sha256(String texto) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    public static String sha256Hex(String texto) {
        return HexFormat.of().formatHex(sha256(texto));
    }

    public static String sha256Base64(String texto) {
        return Base64.getEncoder().encodeToString(sha256(texto));
    }

    /** Token aleatorio seguro (256 bits) apto para URL. */
    public static String tokenAleatorio() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
