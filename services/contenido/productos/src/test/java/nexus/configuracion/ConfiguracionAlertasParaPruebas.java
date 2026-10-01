package nexus.configuracion;

import static org.mockito.Mockito.mock;

import nexus.alertas.AlertaCatalogoRepository;
import nexus.alertas.ConsultaAlertasJugadorRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
public class ConfiguracionAlertasParaPruebas {

    @Bean
    @Primary
    AlertaCatalogoRepository alertasCatalogoSimuladas() {
        return mock(AlertaCatalogoRepository.class);
    }

    @Bean
    @Primary
    ConsultaAlertasJugadorRepository consultasAlertasSimuladas() {
        return mock(ConsultaAlertasJugadorRepository.class);
    }
}
