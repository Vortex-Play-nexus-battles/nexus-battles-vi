package com.nexusbattles.ms_identidad.auth.recuperacion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Base64;
import java.util.Locale;

/**
 * Como se compara una respuesta de seguridad.
 *
 * <p><b>Normalizada</b> (sin espacios en los extremos, minusculas, sin tildes
 * y con los espacios interiores colapsados): «  Bogotá » y «bogota» son la
 * misma respuesta. Quien configuro la pregunta hace meses no recuerda si
 * puso la tilde; exigirla solo bloquearia a la persona correcta.
 *
 * <p><b>Resumida antes de BCrypt</b> (SHA-256 en base64): BCrypt solo mira
 * los primeros 72 bytes y la version de Spring rechaza los mas largos, y el
 * contrato admite respuestas de 100 caracteres (mas bytes aun con letras no
 * ASCII). El resumen tiene siempre 44 caracteres. No debilita nada: el
 * secreto sigue protegido por BCrypt; SHA-256 solo le da un tamano fijo.
 */
public final class NormalizadorDeRespuestas {

    private NormalizadorDeRespuestas() {
    }

    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(texto.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /** Lo que se pasa a BCrypt: la respuesta normalizada, resumida a tamano fijo. */
    public static String paraResumir(String respuesta) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256")
                    .digest(normalizar(respuesta).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(resumen);
        } catch (NoSuchAlgorithmException imposible) {
            // SHA-256 es obligatorio en toda JVM (JCA); si faltara, no hay nada sensato que hacer.
            throw new IllegalStateException("La JVM no ofrece SHA-256", imposible);
        }
    }
}
