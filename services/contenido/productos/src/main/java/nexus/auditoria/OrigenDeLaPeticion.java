package nexus.auditoria;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * La dirección IP desde donde se hizo una operación — SA-18, §7.3.5.
 *
 * <p>El mismo criterio que comentarios (no se importa: cada servicio es suyo,
 * regla de microservicios). Primero {@code X-Real-IP}, que el borde escribe en
 * toda petición con la dirección que ve y que el cliente no puede falsear;
 * después el primer valor de {@code X-Forwarded-For}; sin cabeceras (una
 * llamada directa al servicio, las pruebas), la remota.
 *
 * <p>Solo se aceptan caracteres de una IP ({@code 0-9 a-f A-F : .}) y como
 * mucho 45, el largo de la columna de la bitácora central: un valor que no
 * parece una IP no se guarda como si lo fuera.
 */
public final class OrigenDeLaPeticion {

        /** La forma textual más larga de una IPv6. */
        static final int LARGO_MAXIMO = 45;

        private OrigenDeLaPeticion() {
        }

        /** La IP de la petición en curso, o nula si no hay petición (o no parece una IP). */
        public static String ipActual() {
                RequestAttributes atributos = RequestContextHolder.getRequestAttributes();
                if (atributos instanceof ServletRequestAttributes servlet) {
                        return ipDe(servlet.getRequest());
                }
                return null;
        }

        /** @return la IP de origen, o nula si ni las cabeceras ni la remota lo parecen */
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

        /** ASCII a mano: {@code Character.digit} acepta dígitos de otros alfabetos. */
        static boolean pareceUnaIp(String valor) {
                return valor != null
                                && !valor.isEmpty()
                                && valor.length() <= LARGO_MAXIMO
                                && valor.chars().allMatch(c -> (c >= '0' && c <= '9')
                                                || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
                                                || c == ':' || c == '.');
        }
}
