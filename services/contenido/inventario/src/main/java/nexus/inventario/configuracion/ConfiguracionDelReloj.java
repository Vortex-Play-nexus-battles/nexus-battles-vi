package nexus.inventario.configuracion;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * El reloj del servicio, inyectable — B4. Las entregas guardan cuando se
 * registraron y cuando se completaron; con el reloj inyectado las pruebas
 * fijan el momento en vez de depender de la hora de la maquina.
 */
@Configuration
public class ConfiguracionDelReloj {

    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }
}
