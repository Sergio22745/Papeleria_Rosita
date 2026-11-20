package com.papeleriarosita.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.HexFormat;

/**
 * Utilidades criptográficas de alta seguridad para Papelería Rosita.
 * Implementa PBKDF2WithHmacSHA256 con sal aleatoria criptográficamente segura,
 * número de iteraciones configurable y comparación segura contra ataques de tiempo.
 */
public class SeguridadUtil {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 600_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final String PREFIX = "PBKDF2";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private SeguridadUtil() {
    }

    /**
     * Genera un hash seguro utilizando PBKDF2WithHmacSHA256 con una sal aleatoria única de 16 bytes.
     * Formato resultante: "PBKDF2$<iteraciones>$<salt_hex>$<hash_hex>"
     * Longitud típica: 111 caracteres (almacenable con amplio margen en VARCHAR(255)).
     *
     * @param contrasena Contraseña en texto plano
     * @return Cadena formateada con algoritmo, iteraciones, sal y hash
     */
    public static String hashearContrasena(String contrasena) {
        if (contrasena == null || contrasena.isEmpty()) {
            throw new IllegalArgumentException("La contraseña no puede ser nula ni vacía.");
        }

        byte[] salt = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);

        byte[] hash = derivarHash(contrasena.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS);

        String saltHex = HexFormat.of().formatHex(salt);
        String hashHex = HexFormat.of().formatHex(hash);

        return PREFIX + "$" + ITERATIONS + "$" + saltHex + "$" + hashHex;
    }

    /**
     * Verifica de forma segura si la contraseña ingresada coincide con el hash almacenado en la BD.
     * Soporta:
     * 1. Formato PBKDF2WithHmacSHA256 ("PBKDF2$<iteraciones>$<salt_hex>$<hash_hex>").
     * 2. Formato legado protegido SHA-256 (64 caracteres hex) para migración segura.
     *
     * No permite la autenticación con contraseñas en texto plano sin protección.
     *
     * @param contrasenaIngresada Contraseña en texto plano ingresada en el inicio de sesión
     * @param valorAlmacenado     Hash almacenado en la columna 'contrasena' de la BD
     * @return true si coincide de forma válida y segura; false en cualquier otro caso
     */
    public static boolean verificarContrasena(String contrasenaIngresada, String valorAlmacenado) {
        if (contrasenaIngresada == null || contrasenaIngresada.isEmpty() || valorAlmacenado == null || valorAlmacenado.isEmpty()) {
            return false;
        }

        // 1. Verificación moderna PBKDF2WithHmacSHA256
        if (valorAlmacenado.startsWith(PREFIX + "$")) {
            String[] partes = valorAlmacenado.split("\\$");
            if (partes.length == 4) {
                try {
                    int iteraciones = Integer.parseInt(partes[1]);
                    byte[] salt = HexFormat.of().parseHex(partes[2]);
                    byte[] hashEsperado = HexFormat.of().parseHex(partes[3]);

                    byte[] hashCalculado = derivarHash(contrasenaIngresada.toCharArray(), salt, iteraciones, hashEsperado.length * 8);

                    return MessageDigest.isEqual(hashCalculado, hashEsperado);
                } catch (Exception e) {
                    System.err.println("[ADVERTENCIA SeguridadUtil] Formato de hash PBKDF2 inválido o no reconocido.");
                    return false;
                }
            }
        }

        // 2. Soporte para hashes legados SHA-256 (64 caracteres hexadecimales) durante migración
        if (valorAlmacenado.matches("^[a-fA-F0-9]{64}$")) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] hashCalculado = md.digest(contrasenaIngresada.getBytes(StandardCharsets.UTF_8));
                byte[] hashEsperado = HexFormat.of().parseHex(valorAlmacenado);
                return MessageDigest.isEqual(hashCalculado, hashEsperado);
            } catch (NoSuchAlgorithmException e) {
                return false;
            }
        }

        // 3. Contraseñas en texto plano sin protección no son aceptadas por seguridad
        return false;
    }

    /**
     * Determina si una contraseña almacenada requiere ser actualizada a PBKDF2WithHmacSHA256.
     * Permite identificar si la cuenta proviene de un formato anterior (ej. SHA-256)
     * para aplicar un procedimiento de migración controlado sin alterar la BD automáticamente.
     */
    public static boolean necesitaActualizacion(String valorAlmacenado) {
        if (valorAlmacenado == null || !valorAlmacenado.startsWith(PREFIX + "$")) {
            return true;
        }
        String[] partes = valorAlmacenado.split("\\$");
        if (partes.length == 4) {
            try {
                int iteraciones = Integer.parseInt(partes[1]);
                return iteraciones < ITERATIONS;
            } catch (NumberFormatException e) {
                return true;
            }
        }
        return true;
    }

    /**
     * Deriva la clave pseudoaleatoria mediante PBKDF2WithHmacSHA256.
     */
    private static byte[] derivarHash(char[] password, byte[] salt, int iterations, int keyLengthBits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLengthBits);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
            return skf.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Error al derivar clave PBKDF2: " + e.getMessage(), e);
        }
    }
}
