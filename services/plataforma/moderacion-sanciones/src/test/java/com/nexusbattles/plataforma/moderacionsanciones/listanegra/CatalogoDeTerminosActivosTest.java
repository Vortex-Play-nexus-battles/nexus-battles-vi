package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CatalogoDeTerminosActivos · lectura y invalidacion inmediata")
class CatalogoDeTerminosActivosTest {

    @Mock
    private TerminoProhibidoRepository repositorio;

    @Test
    @DisplayName("lee los activos como TerminoActivo e invalida su entrada al momento")
    void leeEInvalida() {
        when(repositorio.findByActivoTrueOrderByNormalizadoAsc()).thenReturn(List.of(new TerminoProhibido("spiderman",
                "spiderman", CategoriaDeTermino.MARCA, ModoDeCoincidencia.SUBCADENA, true, "semilla",
                OffsetDateTime.now(ZoneOffset.UTC))));
        ConcurrentMapCacheManager caches = new ConcurrentMapCacheManager(CatalogoDeTerminosActivos.CACHE);
        CatalogoDeTerminosActivos catalogo = new CatalogoDeTerminosActivos(repositorio, caches);
        Cache cache = caches.getCache(CatalogoDeTerminosActivos.CACHE);
        cache.put(CatalogoDeTerminosActivos.CLAVE, List.of());

        assertThat(catalogo.activos()).containsExactly(new TerminoActivo("spiderman", "spiderman",
                CategoriaDeTermino.MARCA, ModoDeCoincidencia.SUBCADENA));
        catalogo.invalidar();

        assertThat(cache.get(CatalogoDeTerminosActivos.CLAVE)).isNull();
    }

    @Test
    @DisplayName("con Redis caido la invalidacion no rompe la escritura; sin cache no hace nada")
    void tolerante(@Mock CacheManager caches, @Mock Cache cache) {
        when(caches.getCache(CatalogoDeTerminosActivos.CACHE)).thenReturn(cache);
        doThrow(new RedisConnectionFailureException("caido")).when(cache).evictIfPresent(CatalogoDeTerminosActivos.CLAVE);
        when(cache.getName()).thenReturn(CatalogoDeTerminosActivos.CACHE);

        assertThatCode(() -> new CatalogoDeTerminosActivos(repositorio, caches).invalidar()).doesNotThrowAnyException();

        when(caches.getCache(CatalogoDeTerminosActivos.CACHE)).thenReturn(null);
        assertThatCode(() -> new CatalogoDeTerminosActivos(repositorio, caches).invalidar()).doesNotThrowAnyException();
    }
}
