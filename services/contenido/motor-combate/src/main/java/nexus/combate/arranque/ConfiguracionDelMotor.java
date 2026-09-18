package nexus.combate.arranque;

import nexus.combate.ClienteHeroes;
import nexus.combate.ClienteHeroesHttp;
import nexus.combate.api.ResolverAtaque;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

/**
 * Cableado del servicio.
 *
 * <p>El dominio y el caso de uso son Java corriente, sin anotaciones: se
 * instancian aqui a mano. Asi se prueban sin levantar contexto y la prueba que
 * vigila que {@code nexus.combate} no se llene de beans sigue valiendo.
 *
 * <p><b>Esto ademas conecta un cable que estaba suelto.</b> La propiedad
 * {@code motor.heroes.url} llevaba declarada en {@code application.yml} desde el
 * principio y <i>no la leia nadie</i>: no habia ningun {@code @Value} ni ningun
 * bean de {@link ClienteHeroesHttp}, asi que el adaptador HTTP solo existia
 * dentro de su prueba de integracion. Configurar el servicio no cambiaba nada.
 */
@Configuration
public class ConfiguracionDelMotor {

    /**
     * Adaptador hacia el catalogo de heroes.
     *
     * @param urlDeHeroes {@code motor.heroes.url}; en contenedores apunta a
     *                    {@code http://srv-heroes:8080}
     */
    @Bean
    public ClienteHeroes clienteHeroes(@Value("${motor.heroes.url}") String urlDeHeroes) {
        return new ClienteHeroesHttp(URI.create(urlDeHeroes));
    }

    @Bean
    public ResolverAtaque resolverAtaque(ClienteHeroes heroes) {
        return new ResolverAtaque(heroes);
    }
}
