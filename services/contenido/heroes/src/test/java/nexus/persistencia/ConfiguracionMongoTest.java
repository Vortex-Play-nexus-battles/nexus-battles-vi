package nexus.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
import org.springframework.boot.mongodb.autoconfigure.MongoProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Regla 10 de plataforma: la cadena de conexion llega por la variable de
 * entorno MONGODB_URI. Esta prueba verifica que el perfil "mongo" la enchufa
 * en la propiedad que Spring Boot 4 realmente lee (spring.mongodb.uri), sin
 * Docker ni conexion real: solo la configuracion.
 *
 * Nacio de un fallo en el primer despliegue (corrida 34306037727, 8-sep):
 * el contenedor tenia MONGODB_URI=mongodb://contenido-mongo:27017/heroes y
 * aun asi intento conectarse a localhost:27017, porque el perfil escribia la
 * propiedad con el nombre de Boot 3 (spring.data.mongodb.uri), que Boot 4
 * ignora. La prueba de integracion con Testcontainers no lo detectaba porque
 * @ServiceConnection inyecta la conexion por fuera de las propiedades.
 */
class ConfiguracionMongoTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(MongoAutoConfiguration.class));

    @Test
    @DisplayName("con el perfil mongo, MONGODB_URI termina en spring.mongodb.uri, la propiedad que lee Boot 4")
    void laVariableDeEntornoLlegaALaPropiedadDeBoot4() {
        contexto.withPropertyValues(
                        "spring.profiles.active=mongo",
                        "MONGODB_URI=mongodb://contenido-mongo:27017/heroes")
                .run(ctx -> assertEquals(
                        "mongodb://contenido-mongo:27017/heroes",
                        ctx.getBean(MongoProperties.class).getUri()));
    }

    @Test
    @DisplayName("sin MONGODB_URI el perfil mongo apunta al Mongo local de desarrollo")
    void sinVariableUsaElLocal() {
        contexto.withPropertyValues("spring.profiles.active=mongo")
                .run(ctx -> assertEquals(
                        "mongodb://localhost:27017/heroes",
                        ctx.getBean(MongoProperties.class).getUri()));
    }
}
