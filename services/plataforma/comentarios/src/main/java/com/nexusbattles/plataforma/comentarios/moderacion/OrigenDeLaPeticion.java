package com.nexusbattles.plataforma.comentarios.moderacion;

import jakarta.servlet.http.HttpServletRequest;

/**
 * La IP de origen de una peticion, para el asiento de moderacion — B3, 7.3.3.
 *
 * <p>Detras del borde (nginx) la direccion remota del socket es la del propio
 * borde, asi que se toma el primer valor de {@code X-Forwarded-For}, que es
 * donde el borde deja la del cliente; sin esa cabecera (una llamada directa
 * al servicio, las pruebas), la remota.
 *
 * <p>Solo se aceptan caracteres de una IP ({@code 0-9 a-f A-F : .}) y como
 * mucho 45, el largo de la columna: la cabecera la puede escribir cualquiera,
 * y un valor que no parece una IP no se guarda como si lo fuera. En ese caso
 * se cae a la remota.
 *
 * <p>Ojo, y queda dicho en el resumen de B3: el borde de desarrollo AÑADE la
 * IP que ve a la cabecera que trae el cliente ({@code
 * $proxy_add_x_forwarded_for}), no la sustituye. El primer valor es por tanto
 * el que declaro el cliente. Para una auditoria que no se pueda falsear, el
 * borde tendria que sobrescribirla ({@code X-Forwarded-For $remote_addr}).
 */
public final class OrigenDeLaPeticion {

    /** Largo de la columna ip_origen (V5): la forma textual mas larga de una IPv6. */
    static final int LARGO_MAXIMO = 45;

    private OrigenDeLaPeticion() {
    }

    /** @return la IP de origen, o nula si ni la cabecera ni la remota lo parecen */
    public static String ipDe(HttpServletRequest peticion) {
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
