package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;

/**
 * Solo cambia el manejo de errores de la cache ({@link ErroresDeCacheTolerados});
 * el gestor sigue siendo el de Spring Boot sobre Redis, con la caducidad de
 * {@code spring.cache.redis.time-to-live}.
 */
@Configuration
public class ConfiguracionDeCache implements CachingConfigurer {

    @Override
    public CacheErrorHandler errorHandler() {
        return new ErroresDeCacheTolerados();
    }
}
