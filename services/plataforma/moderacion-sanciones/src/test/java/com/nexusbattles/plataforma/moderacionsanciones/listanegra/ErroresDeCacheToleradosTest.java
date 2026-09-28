package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.data.redis.RedisConnectionFailureException;

import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("ErroresDeCacheTolerados · Redis caido no es un 500")
class ErroresDeCacheToleradosTest {

    private final ErroresDeCacheTolerados manejador = new ErroresDeCacheTolerados();
    private final ConcurrentMapCache cache = new ConcurrentMapCache(CatalogoDeTerminosActivos.CACHE);
    private final RuntimeException caida = new RedisConnectionFailureException("Unable to connect to Redis");

    @Test
    @DisplayName("leer, guardar, invalidar y vaciar: se registra y se sigue, sin relanzar")
    void noRelanza() {
        assertThatCode(() -> {
            manejador.handleCacheGetError(caida, cache, "activos");
            manejador.handleCachePutError(caida, cache, "activos", java.util.List.of());
            manejador.handleCacheEvictError(caida, cache, "activos");
            manejador.handleCacheClearError(caida, cache);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("la configuracion de cache instala este manejador")
    void configuracion() {
        org.assertj.core.api.Assertions.assertThat(new ConfiguracionDeCache().errorHandler())
                .isInstanceOf(ErroresDeCacheTolerados.class);
    }
}
