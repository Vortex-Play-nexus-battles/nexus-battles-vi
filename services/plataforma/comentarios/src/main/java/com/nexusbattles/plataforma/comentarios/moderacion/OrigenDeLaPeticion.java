package com.nexusbattles.plataforma.comentarios.moderacion;

import jakarta.servlet.http.HttpServletRequest;

/**
 * La IP de origen de una peticion, para el asiento de moderacion — B3, 7.3.3,
 * y la bitacora central — §7.3.5 (SA-5).
 *
 * <p>Primero {@code X-Real-IP}: el borde (nginx) la escribe en toda peticion
 * con {@code $remote_addr} y sustituye lo que trajera el cliente, asi que es la
 * unica que no se puede falsear desde fuera. Despues, el primer valor de
 * {@code X-Forwarded-For} (el borde de desarrollo AÑADE la IP que ve a lo que
 * declara el cliente, asi que ese primer valor es el declarado) y, sin
 * cabeceras (una llamada directa al servicio, las pruebas), la remota.
 *
 * <p>Solo se aceptan caracteres de una IP ({@code 0-9 a-f A-F : .}) y como
 * mucho 45, el largo de la columna: un valor que no parece una IP no se guarda
 * como si lo fuera, y se pasa al siguiente.
 */
public final class OrigenDeLaPeticion {

    /** Largo de la columna ip_origen (V5): la forma textual mas larga de una IPv6. */
    static final int LARGO_MAXIMO = 45;

    private OrigenDeLaPeticion() {
    }

    /** @return la IP de origen, o nula si ni la cabecera ni la remota lo parecen */
    public static String ipDe(HttpServletRequest peticion) {
        String delBorde = peticion.getHeader("X-Real-IP");
        if (delBorde != null && pareceUnaIp(delBorde.strip())) {
            return delBorde.strip();
        }
        String reenviada = peticion.getHeader("X-Forwarded-For");
        if (reenviada != null) {
            String primera = reenviada.split(",", 2)[0].strip();
            if (pareceUnaIp(primera)) {
                return primera;
            }
        }
        String remota = peticion.getRemoteAddr();
        return pareceUnaIp(remota) ? remota : null;
    }

    /**
     * ASCII a mano y no {@code Character.digit}: ese acepta tambien digitos de
     * otros alfabetos y letras de ancho completo, que no son parte de ninguna IP.
     */
    static boolean pareceUnaIp(String valor) {
        return valor != null
                && !valor.isEmpty()
                && valor.length() <= LARGO_MAXIMO
                && valor.chars().allMatch(c -> (c >= '0' && c <= '9')
                        || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
                        || c == ':' || c == '.');
    }
}
