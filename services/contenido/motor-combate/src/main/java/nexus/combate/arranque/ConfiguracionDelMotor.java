package nexus.combate.arranque;

import nexus.combate.CatalogoDeCombateHttp;
import nexus.combate.ClienteHeroes;
import nexus.combate.ClienteHeroesHttp;
import nexus.combate.IndiceNormal;
import nexus.combate.api.ResolverAtaque;
import nexus.combate.api.ServicioDeCombate;
import nexus.combate.reglas.CatalogoDeCombate;
import nexus.combate.reglas.DificultadDeLaMaquina;
import nexus.combate.reglas.MotorDeAcciones;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.time.Duration;

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

    /**
     * El indice de la tabla de 8.000 filas (§6.1.4). El documento pide una
     * normal y no fija ni su media ni su desviacion: son parametros (D-B7-01),
     * con la tabla centrada y cubierta a tres desviaciones por omision.
     */
    @Bean
    public IndiceNormal indiceNormal(
            @Value("${motor.indice.media:" + IndiceNormal.MEDIA_POR_OMISION + "}") double media,
            @Value("${motor.indice.desviacion:" + IndiceNormal.DESVIACION_POR_OMISION + "}") double desviacion) {
        return new IndiceNormal(media, desviacion);
    }

    @Bean
    public ResolverAtaque resolverAtaque(ClienteHeroes heroes, IndiceNormal indice) {
        return new ResolverAtaque(heroes, indice);
    }

    /**
     * Las fichas de combate del catalogo de heroes (B7), con cache.
     *
     * @param segundos {@code motor.heroes.cache-segundos}; 0 desactiva la cache
     */
    @Bean
    public CatalogoDeCombate catalogoDeCombate(@Value("${motor.heroes.url}") String urlDeHeroes,
                                               @Value("${motor.heroes.cache-segundos:300}") long segundos) {
        return new CatalogoDeCombateHttp(URI.create(urlDeHeroes), Duration.ofSeconds(Math.max(0, segundos)));
    }

    /**
     * El motor de las acciones, con la dificultad de la IA (D-41).
     *
     * @param dificultad {@code motor.ia.dificultad} ({@code MOTOR_IA_DIFICULTAD}):
     *                   FACIL, NORMAL o DIFICIL; una mal escrita impide arrancar
     */
    @Bean
    public MotorDeAcciones motorDeAcciones(CatalogoDeCombate catalogo, IndiceNormal indice,
                                           @Value("${motor.ia.dificultad:NORMAL}") String dificultad) {
        return new MotorDeAcciones(catalogo, indice, DificultadDeLaMaquina.desde(dificultad));
    }

    @Bean
    public ServicioDeCombate servicioDeCombate(MotorDeAcciones motor) {
        return new ServicioDeCombate(motor);
    }
}
