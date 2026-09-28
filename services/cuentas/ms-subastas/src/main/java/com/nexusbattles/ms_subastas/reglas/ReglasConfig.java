package com.nexusbattles.ms_subastas.reglas;

import com.nexusbattles.ms_subastas.pujas.service.ParametrosPuja;
import com.nexusbattles.plataforma.observabilidad.InterceptorDeTraza;
import com.nexusbattles.plataforma.resiliencia.parametros.LectorDeParametros;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;

/**
 * Cableado de las reglas vigentes contra admin-parametros (B8).
 *
 * <p>Con {@code PARAMETROS_URL} vacia —desarrollo local sin catalogo— el
 * lector no hace ni una peticion: los topes salen de las variables de entorno
 * y el incremento queda sin configurar, que es lo que publicar dira.
 *
 * <p>Tiempos cortos a proposito: la primera lectura tras caducar la cache puede
 * ocurrir con el candado de una subasta tomado, y un catalogo lento no puede
 * congelar las pujas. {@code PujaApplicationService} calienta la cache antes
 * del candado para que eso no pase en el camino normal.
 */
@Configuration
public class ReglasConfig {

    @Bean
    public LectorDeParametros lectorDeParametros(
            @Value("${app.parametros.url:}") String url,
            @Value("${app.parametros.cache-segundos:30}") long cacheSegundos,
            @Value("${app.parametros.timeout-ms:1500}") long timeoutMs,
            Clock clock) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofMillis(timeoutMs));
        fabrica.setReadTimeout(Duration.ofMillis(timeoutMs));
        RestClient http = RestClient.builder()
                .requestFactory(fabrica)
                // Regla 5: la traza del jugador viaja tambien a admin-parametros.
                .requestInterceptor(new InterceptorDeTraza())
                .build();
        return LectorDeParametros.desde(http, url, clock, Duration.ofSeconds(cacheSegundos));
    }

    @Bean
    public FuenteDeReglas fuenteDeReglas(
            LectorDeParametros lector,
            ParametrosPuja respaldo,
            @Value("${app.subastas.pendientes.al-vencer:ENTREGAR}") PoliticaAlVencer alVencerPorOmision) {
        return new ReglasDesdeParametros(lector, respaldo, alVencerPorOmision);
    }
}
