package com.milan.barberia.seguridad;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifrado SIMÉTRICO AES-256-GCM (documento "Encriptado de datos").
 * A diferencia del hash, es reversible con la clave: se usa para datos personales o
 * financieros que la app necesita volver a leer (correos, referencias de pago).
 * La clave viene del archivo .env (APP_AES_KEY) y nunca se guarda en la base de datos.
 * Formato guardado: "ENC:" + Base64( IV de 12 bytes | texto cifrado + etiqueta GCM ).
 */
@Service
public class CifradoServicio {

    private static final String PREFIJO = "ENC:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Acceso estático para el convertidor JPA {@link ConvertidorCampoCifrado}. */
    private static CifradoServicio instancia;

    private final String claveBase64;
    private SecretKey clave;

    public CifradoServicio(@Value("${app.seguridad.clave-aes:}") String claveBase64) {
        this.claveBase64 = claveBase64;
    }

    @PostConstruct
    void iniciar() {
        if (claveBase64 == null || claveBase64.isBlank()) {
            throw new IllegalStateException(
                    "Falta APP_AES_KEY en el archivo .env (clave AES de 32 bytes en Base64). Revisa backend/.env.example");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(claveBase64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("APP_AES_KEY no es Base64 válido", e);
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("APP_AES_KEY debe tener 32 bytes (AES-256); tiene " + bytes.length);
        }
        clave = new SecretKeySpec(bytes, "AES");
        instancia = this;
    }

    static CifradoServicio instancia() {
        if (instancia == null) {
            throw new IllegalStateException("CifradoServicio aún no está inicializado");
        }
        return instancia;
    }

    public String cifrar(String textoPlano) {
        if (textoPlano == null || textoPlano.isEmpty()) {
            return textoPlano;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cifrado = cipher.doFinal(textoPlano.getBytes(StandardCharsets.UTF_8));
            byte[] salida = ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array();
            return PREFIJO + Base64.getEncoder().encodeToString(salida);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cifrar el dato", e);
        }
    }

    public String descifrar(String guardado) {
        if (guardado == null || !guardado.startsWith(PREFIJO)) {
            return guardado; // dato antiguo sin cifrar: se devuelve tal cual
        }
        try {
            byte[] entrada = Base64.getDecoder().decode(guardado.substring(PREFIJO.length()));
            ByteBuffer buffer = ByteBuffer.wrap(entrada);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] cifrado = new byte[buffer.remaining()];
            buffer.get(cifrado);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cifrado), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo descifrar un dato: ¿cambió APP_AES_KEY?", e);
        }
    }
}
