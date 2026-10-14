package nexus.configuracion;

import static org.mockito.Mockito.mock;

import nexus.auditoria.OperacionAdministrativaRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * SA-18 — las pruebas que levantan el contexto sin MongoDB usan un doble del
 * repositorio de operaciones administrativas, como el de las alertas
 * ({@link ConfiguracionAlertasParaPruebas}): sin él, cada alta, edición o
 * retiro de una prueba de API esperaría al servidor de MongoDB hasta su tiempo
 * de espera. La consulta contra MongoDB de verdad la fija
 * {@code OperacionesAdministrativasMongoTest}.
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionOperacionesParaPruebas {

    @Bean
    @Primary
    OperacionAdministrativaRepository operacionesAdministrativasSimuladas() {
        return mock(OperacionAdministrativaRepository.class);
    }
}
