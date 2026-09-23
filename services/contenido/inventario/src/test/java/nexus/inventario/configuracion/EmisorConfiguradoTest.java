package nexus.inventario.configuracion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

/**
 * Guardian del emisor configurado — R9.7.
 *
 * <h2>Por que existe</h2>
 *
 * {@code SeguridadDelInventarioTest} ya prueba la cadena de seguridad con
 * tokens reales de ms-identidad: firma, caducidad, {@code uid} como propietario.
 * Lo que <b>no</b> prueba —porque usa un decodificador de prueba— es contra que
 * emisor se valida en el host. Y ahi estaba el defecto.
 *
 * <p>Hasta R9.7 este servicio leia {@code KEYCLOAK_JWK_SET_URI}, con un realm de
 * Keycloak (localhost:8180) por omision. Dos problemas, no uno:
 *
 * <ol>
 *   <li>El emisor del sistema es <b>ms-identidad</b> (ADR-002 / ADR-005). Ese
 *       Keycloak no existe ni esta desplegado.</li>
 *   <li>{@code KEYCLOAK_JWK_SET_URI} es justo la unica variable que el CD
 *       <b>no</b> reparte. En el host de contenido siempre ganaba el valor por
 *       omision del compose —el {@code jwks-dev} de esa red—, asi que inventario
 *       validaba con una clave mientras ms-subastas y salas-partidas firmaban
 *       con otra: las operaciones internas de HU-INV-010 no podian funcionar en
 *       AWS. El banco E2E no lo destapaba porque su compose si fija el emisor
 *       correcto — el defecto solo existia en el entorno real.</li>
 * </ol>
 *
 * <p>Lo que esta prueba fija es el <b>orden</b> de la cadena de respaldo:
 * {@code IDENTIDAD_JWKS_URL} (la que el CD reparte) manda;
 * {@code KEYCLOAK_JWK_SET_URI} se conserva para no romper a quien todavia la
 * inyecte; y el ultimo recurso apunta a ms-identidad en local, no a un Keycloak.
 *
 * <p>No levanta contexto: resuelve el {@code application.properties} real con la
 * misma maquinaria de Spring que lo resolvera en produccion.
 */
@DisplayName("El jwk-set-uri prefiere el emisor que el CD reparte")
class EmisorConfiguradoTest {

    private static final String PROPIEDAD =
            "spring.security.oauth2.resourceserver.jwt.jwk-set-uri";

    private static final String DE_IDENTIDAD = "http://ms-identidad:8089/api/v1/auth/jwks";
    private static final String DE_JWKS_DEV = "http://jwks-dev/certs.json";

    @Test
    @DisplayName("con IDENTIDAD_JWKS_URL definida, gana el emisor real")
    void ganaIdentidad() throws IOException {
        assertEquals(DE_IDENTIDAD, resolver(Map.of(
                "IDENTIDAD_JWKS_URL", DE_IDENTIDAD,
                "KEYCLOAK_JWK_SET_URI", DE_JWKS_DEV)));
    }

    @Test
    @DisplayName("sin ella, se respeta KEYCLOAK_JWK_SET_URI como respaldo heredado")
    void respaldoHeredado() throws IOException {
        assertEquals(DE_JWKS_DEV, resolver(Map.of("KEYCLOAK_JWK_SET_URI", DE_JWKS_DEV)));
    }

    @Test
    @DisplayName("sin ninguna de las dos, el ultimo recurso es ms-identidad en local, no un realm de Keycloak")
    void ultimoRecursoEsMsIdentidad() throws IOException {
        assertEquals("http://localhost:8089/api/v1/auth/jwks", resolver(Map.of()));
    }

    /** Resuelve la propiedad del application.properties real con las variables dadas. */
    private static String resolver(Map<String, Object> variables) throws IOException {
        StandardEnvironment entorno = new StandardEnvironment();
        // Fuera las variables de entorno y del sistema de la maquina que corre
        // la prueba: aqui solo deben pesar el archivo y lo que se le inyecte.
        entorno.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        entorno.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        entorno.getPropertySources().addLast(
                new ResourcePropertySource(new ClassPathResource("application.properties")));
        entorno.getPropertySources().addFirst(new MapPropertySource("del-despliegue", variables));
        return entorno.getProperty(PROPIEDAD);
    }
}
