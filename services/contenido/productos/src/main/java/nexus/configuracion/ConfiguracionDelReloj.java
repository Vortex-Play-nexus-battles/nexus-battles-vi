package nexus.configuracion;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * El reloj del servicio — B4.
 *
 * <p>La vigencia de una promocion la evalua el servidor, no el cliente; se
 * inyecta el reloj en vez de leer la hora del sistema para poder probarla sin
 * esperar de verdad. Declararlo aqui hace que la autoconfiguracion de
 * plataforma-observabilidad (que pone el suyo con {@code @ConditionalOnMissingBean})
 * use este mismo: un solo {@link Clock} en el contexto.
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionDelReloj {

        @Bean
        public Clock reloj() {
                return Clock.systemUTC();
        }
}
