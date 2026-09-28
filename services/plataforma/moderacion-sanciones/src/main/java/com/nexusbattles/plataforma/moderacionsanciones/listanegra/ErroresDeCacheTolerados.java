package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * La cache es un atajo, no una dependencia: si Redis falla, se registra y la
 * operacion sigue como si no hubiera cache.
 *
 * <p>Antes no habia manejador: con Redis caido, {@code @Cacheable} lanzaba la
 * excepcion de conexion, la verificacion respondia 500 y ms-identidad, que
 * falla hacia el lado abierto, aprobaba el apodo sin mirar la lista. Con este
 * manejador una lectura fallida es un fallo de cache (se lee de PostgreSQL),
 * una escritura fallida se pierde sin mas, y una invalidacion fallida queda
 * cubierta por la caducidad de la entrada.
 */
public class ErroresDeCacheTolerados implements CacheErrorHandler {

    private static final Logger BITACORA = LoggerFactory.getLogger(ErroresDeCacheTolerados.class);

    @Override
    public void handleCacheGetError(RuntimeException error, Cache cache, Object clave) {
        avisar("leer", cache, error);
    }

    @Override
    public void handleCachePutError(RuntimeException error, Cache cache, Object clave, Object valor) {
        avisar("guardar", cache, error);
    }

    @Override
    public void handleCacheEvictError(RuntimeException error, Cache cache, Object clave) {
        avisar("invalidar", cache, error);
    }

    @Override
    public void handleCacheClearError(RuntimeException error, Cache cache) {
        avisar("vaciar", cache, error);
    }

    private static void avisar(String operacion, Cache cache, RuntimeException error) {
        BITACORA.warn("Cache {} no disponible al {}: se sigue sin cache ({})", cache.getName(), operacion,
                error.getClass().getSimpleName());
    }
}
