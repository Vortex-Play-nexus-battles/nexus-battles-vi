package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Los terminos activos, con cache.
 *
 * <p>La lista negra se consulta en cada mensaje de chat, en cada comentario y
 * en cada alta de apodo: no puede ir a PostgreSQL cada vez. La cache es Redis
 * ({@code spring.cache.type=redis}) con caducidad
 * ({@code spring.cache.redis.time-to-live}, 5 minutos por omision), y cada
 * escritura del panel la invalida ({@link #invalidar()}). La caducidad es la
 * red de seguridad de lo que la invalidacion no ve: un {@code INSERT} a mano o
 * una carrera entre una escritura y una lectura concurrente dejan la cache
 * desfasada como mucho ese tiempo. Antes no caducaba nunca.
 *
 * <p><b>Por que la invalidacion es explicita y no un {@code @CacheEvict}.</b>
 * {@code Cache.evict} y el vaciado del interceptor pueden ser diferidos (su
 * contrato lo permite) y con Redis lo son: medido en {@code ListaNegraIT}, la
 * entrada seguia ahi al responder el alta y desaparecia cientos de
 * milisegundos despues, asi que la verificacion inmediata aprobaba el termino
 * que se acababa de prohibir. {@link Cache#evictIfPresent} es inmediata por
 * contrato: la siguiente lectura ya no la ve.
 *
 * <p>Si Redis no responde, {@link ErroresDeCacheTolerados} registra el fallo y
 * la lectura sigue contra PostgreSQL: la moderacion no devuelve un 500 porque
 * la cache este caida. Una invalidacion fallida tampoco: queda la caducidad.
 *
 * <p>El nombre de la cache es nuevo a proposito ({@value #CACHE}): la anterior
 * ({@code terminosProhibidos}) guardaba una lista de cadenas sin caducidad, y
 * reutilizar el nombre haria que esta clase leyera un valor de otro tipo que
 * nunca expira.
 */
@Component
public class CatalogoDeTerminosActivos {

    public static final String CACHE = "listaNegraTerminosActivos";

    /** La unica entrada de la cache: toda la lista activa. */
    static final String CLAVE = "activos";

    private final TerminoProhibidoRepository repositorio;
    private final CacheManager caches;

    public CatalogoDeTerminosActivos(TerminoProhibidoRepository repositorio, CacheManager caches) {
        this.repositorio = Objects.requireNonNull(repositorio);
        this.caches = Objects.requireNonNull(caches);
    }

    @Cacheable(cacheNames = CACHE, key = "'" + CLAVE + "'")
    public List<TerminoActivo> activos() {
        return repositorio.findByActivoTrueOrderByNormalizadoAsc().stream().map(TerminoActivo::desde).toList();
    }

    /** Tras cada escritura ya confirmada: la siguiente verificacion lee la lista nueva. */
    public void invalidar() {
        Cache cache = caches.getCache(CACHE);
        if (cache == null) {
            return;
        }
        try {
            cache.evictIfPresent(CLAVE);
        } catch (RuntimeException caida) {
            new ErroresDeCacheTolerados().handleCacheEvictError(caida, cache, CLAVE);
        }
    }
}
