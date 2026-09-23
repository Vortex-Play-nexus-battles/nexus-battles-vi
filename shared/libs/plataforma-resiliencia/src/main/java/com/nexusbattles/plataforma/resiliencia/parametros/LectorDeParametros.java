package com.nexusbattles.plataforma.resiliencia.parametros;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lee un parametro del catalogo de {@code admin-parametros} (HU-ADM-001) sin
 * que una caida de ese servicio pueda tumbar al que lo consulta.
 *
 * <p><b>Por que existe.</b> El primer consumidor por API —los limites de
 * sancion de {@code moderacion-sanciones}— resolvio bien el problema: cache
 * corta, respaldo por variable de entorno y captura del fallo del cliente
 * HTTP. Pero eran cien lineas dentro de un servicio. El segundo consumidor las
 * habria copiado, el tercero habria copiado una variante, y en dos sprints
 * habria tres politicas distintas de «que hacer cuando el catalogo no
 * responde». Esta clase es esa politica, escrita una vez.
 *
 * <p><b>La regla, que no se negocia:</b> leer un parametro <b>nunca</b> lanza
 * hacia arriba. El orden es siempre el mismo:
 *
 * <ol>
 *   <li>valor cacheado, si la cache sigue vigente;</li>
 *   <li>valor del catalogo, si responde y el Product Owner lo ha fijado;</li>
 *   <li>respaldo que pasa quien llama —su variable de entorno—, dejando
 *       constancia en la bitacora de que se uso.</li>
 * </ol>
 *
 * <p>Un parametro que no responde degrada a su respaldo; nunca a una
 * excepcion, ni a un valor inventado. Es la misma idea de HU-DIS-003 que
 * motiva {@link com.nexusbattles.plataforma.resiliencia.CortaCircuitos}, y por
 * eso vive en esta biblioteca y no en {@code plataforma-comun}: hace falta
 * donde hay una llamada saliente, no en los veinte modulos.
 *
 * <p><b>La cache guarda tambien el fallo.</b> Si el catalogo no responde, lo
 * que se cachea durante {@code vigenciaDeCache} es «no hay valor», no una
 * ausencia de entrada. Sin eso, cada peticion volveria a intentarlo y el
 * camino caliente pagaria un tiempo de espera completo por llamada, que es
 * precisamente la cascada que se quiere evitar.
 *
 * <p><b>Sin catalogo configurado</b> ({@code PARAMETROS_URL} vacia, que es el
 * caso de desarrollo local y el de cualquier entorno donde admin-parametros no
 * este desplegado) el lector no hace ni una peticion: todo sale del respaldo.
 * Se construye con {@link #soloRespaldo()} o, mas comodo, con
 * {@link #desde(RestClient, String, Clock, Duration)}, que decide por la URL.
 *
 * <p>Es seguro para varios hilos: la cache es un {@link ConcurrentHashMap} y
 * todo lo demas es inmutable. Dos hilos que fallen la cache a la vez haran dos
 * peticiones y la ultima gana; no hace falta mas, porque las dos leen lo mismo.
 */
public final class LectorDeParametros {

    private static final Logger BITACORA = LoggerFactory.getLogger(LectorDeParametros.class);

    /** Null cuando no hay catalogo configurado: entonces todo sale del respaldo. */
    private final RestClient http;
    private final String base;
    private final Clock reloj;
    private final Duration vigenciaDeCache;
    private final Map<String, Cacheado> cache = new ConcurrentHashMap<>();

    private LectorDeParametros(RestClient http, String base, Clock reloj, Duration vigenciaDeCache) {
        this.http = http;
        this.base = base;
        this.reloj = reloj;
        this.vigenciaDeCache = vigenciaDeCache;
    }

    /**
     * Lector contra un catalogo real.
     *
     * @param http             cliente ya construido (con su traza y sus tiempos de espera)
     * @param urlDelCatalogo   base de admin-parametros, con el {@code /api/v1} incluido
     * @param reloj            el mismo reloj del servicio, para que las pruebas puedan moverlo
     * @param vigenciaDeCache  cuanto vale una lectura antes de volver a preguntar
     */
    public static LectorDeParametros sobre(RestClient http, String urlDelCatalogo, Clock reloj,
                                           Duration vigenciaDeCache) {
        Objects.requireNonNull(http, "hace falta el cliente HTTP");
        Objects.requireNonNull(reloj, "hace falta el reloj");
        Objects.requireNonNull(vigenciaDeCache, "hace falta la vigencia de la cache");
        if (urlDelCatalogo == null || urlDelCatalogo.isBlank()) {
            throw new IllegalArgumentException(
                    "sin URL del catalogo no hay lector: usa soloRespaldo() o desde(...)");
        }
        return new LectorDeParametros(http, urlDelCatalogo.replaceAll("/+$", ""), reloj, vigenciaDeCache);
    }

    /** Lector sin catalogo: no hace ni una peticion y todo valor sale de su respaldo. */
    public static LectorDeParametros soloRespaldo() {
        BITACORA.info("Sin catalogo de parametros configurado (PARAMETROS_URL vacia): "
                + "todos los parametros saldran de su respaldo de variable de entorno");
        return new LectorDeParametros(null, "", Clock.systemUTC(), Duration.ZERO);
    }

    /**
     * El constructor que usan los servicios: si la URL viene vacia, lector sin
     * catalogo; si no, lector contra el. Asi el cableado no repite el
     * {@code if} en cada servicio, que es donde se cuela la diferencia de
     * comportamiento entre uno y otro.
     */
    public static LectorDeParametros desde(RestClient http, String urlDelCatalogo, Clock reloj,
                                           Duration vigenciaDeCache) {
        return urlDelCatalogo == null || urlDelCatalogo.isBlank()
                ? soloRespaldo()
                : sobre(http, urlDelCatalogo, reloj, vigenciaDeCache);
    }

    /** Si este lector tiene catalogo detras. Util para la bitacora y el diagnostico. */
    public boolean tieneCatalogo() {
        return http != null;
    }

    /**
     * El valor vigente de un parametro, tal cual lo publica el catalogo.
     *
     * @return vacio si no hay catalogo, si no responde, o si el Product Owner
     *         no ha fijado el valor (que en el catalogo es un valor nulo)
     */
    public Optional<String> texto(String clave) {
        Objects.requireNonNull(clave, "el parametro necesita su clave");
        if (http == null) {
            return Optional.empty();
        }
        Instant ahora = reloj.instant();
        Cacheado cacheado = cache.get(clave);
        if (cacheado != null && cacheado.vence().isAfter(ahora)) {
            return cacheado.valor();
        }
        Optional<String> valor = leer(clave);
        cache.put(clave, new Cacheado(valor, ahora.plus(vigenciaDeCache)));
        return valor;
    }

    /** El valor del catalogo, o {@code respaldo} si no hay. */
    public String texto(String clave, String respaldo) {
        return texto(clave).orElse(respaldo);
    }

    /**
     * El valor del catalogo como entero, o {@code respaldo} si no hay o si lo
     * que hay no es un entero. Un catalogo mal escrito degrada igual que un
     * catalogo caido: al respaldo, no a una excepcion.
     */
    public long entero(String clave, long respaldo) {
        Optional<String> valor = texto(clave);
        if (valor.isEmpty()) {
            return respaldo;
        }
        try {
            return Long.parseLong(valor.get());
        } catch (NumberFormatException noEsUnEntero) {
            BITACORA.warn("El parametro {} vale '{}', que no es un entero; se usa el respaldo {}",
                    clave, valor.get(), respaldo);
            return respaldo;
        }
    }

    /**
     * El valor del catalogo como una de las opciones de un enumerado, o
     * {@code respaldo}. La comparacion no distingue mayusculas: el catalogo
     * guarda texto libre validado contra su lista de opciones, y una diferencia
     * de caja no deberia cambiar el comportamiento del servicio.
     */
    public <E extends Enum<E>> E opcion(String clave, Class<E> tipo, E respaldo) {
        Objects.requireNonNull(tipo, "hace falta el tipo de la opcion");
        Optional<String> valor = texto(clave);
        if (valor.isEmpty()) {
            return respaldo;
        }
        try {
            return Enum.valueOf(tipo, valor.get().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException noEsUnaOpcion) {
            BITACORA.warn("El parametro {} vale '{}', que no es una opcion de {}; se usa el respaldo {}",
                    clave, valor.get(), tipo.getSimpleName(), respaldo);
            return respaldo;
        }
    }

    private Optional<String> leer(String clave) {
        try {
            Map<?, ?> respuesta = http.get()
                    .uri(base + "/parametros/{clave}/valor", clave)
                    .retrieve()
                    .body(Map.class);
            Object crudo = respuesta == null ? null : respuesta.get("valor");
            String texto = crudo == null ? "" : String.valueOf(crudo).strip();
            if (texto.isEmpty()) {
                BITACORA.info("El parametro {} no tiene valor en el catalogo; se usa el respaldo", clave);
                return Optional.empty();
            }
            return Optional.of(texto);
        } catch (RestClientException noResponde) {
            BITACORA.warn("No se pudo leer {} del catalogo de parametros ({}); se usa el respaldo",
                    clave, noResponde.getMessage());
            return Optional.empty();
        } catch (RuntimeException cualquierOtroFallo) {
            // A proposito mas ancho que RestClientException. Lo que hay detras
            // de esta llamada es un cliente HTTP con conversores de mensaje
            // dentro: una respuesta con el cuerpo cortado o con un tipo de
            // contenido inesperado no siempre sale como RestClientException.
            // Un parametro de configuracion no puede tumbar la peticion de un
            // jugador por ningun motivo, asi que aqui se cierra la puerta
            // entera en vez de enumerar excepciones.
            BITACORA.warn("Fallo inesperado al leer {} del catalogo de parametros ({}); se usa el respaldo",
                    clave, cualquierOtroFallo.toString());
            return Optional.empty();
        }
    }

    /** Lo leido y hasta cuando vale. El vacio tambien se cachea: ver la nota de la clase. */
    private record Cacheado(Optional<String> valor, Instant vence) {
    }
}
