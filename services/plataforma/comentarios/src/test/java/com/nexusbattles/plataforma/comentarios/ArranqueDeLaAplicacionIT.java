package com.nexusbattles.plataforma.comentarios;

import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.plataforma.comentarios.publicacion.ComentariosController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Levanta la aplicacion COMPLETA, igual que hace el contenedor en el
 * servidor: Flyway sobre PostgreSQL, JPA, el servidor web y todos los
 * {@code @Configuration} del servicio.
 *
 * <p>Por que existe: el 2026-09-17 este servicio no arranco en el host de
 * desarrollo ({@code required a bean of type RestClient$Builder that could
 * not be found}) con el build en verde. Ninguna de las pruebas de este
 * modulo cargaba el contexto de Spring -- todas eran unitarias o rebanadas
 * web -- asi que faltaba una dependencia de ejecucion y nadie se entero
 * hasta el despliegue. Una prueba que arranca el contexto es la unica que
 * cubre esa clase de fallo.
 *
 * <p>Sin {@code disabledWithoutDocker}, mismo criterio que los IT de
 * salas-partidas: una prueba de integracion omitida no es una prueba que
 * pasa, es una que no se ejecuto.
 *
 * <p><b>Por que {@code ddl-auto=validate}</b>, aunque el servicio arranque en
 * produccion sin el: asi Hibernate compara su mapeo contra las columnas que
 * dejo Flyway, y el esquema no puede irse separando del modelo sin que nadie
 * se entere. Nada mas escribirla, esta prueba encontro que
 * {@code comentario_imagenes.orden} era SMALLINT y {@code @OrderColumn}
 * esperaba integer; se corrigio con la migracion V3. Mismo criterio que
 * RepositorioSalasJpaIT en salas-partidas.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jpa.hibernate.ddl-auto=validate"
)
class ArranqueDeLaAplicacionIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    /**
     * El JWKS del que la aplicacion saca la clave publica (ADR-002) es el del
     * emisor de prueba: el mismo camino de configuracion que en produccion
     * ({@code jwk-set-uri}), con tokens firmados de verdad.
     */
    @DynamicPropertySource
    static void jwks(DynamicPropertyRegistry registro) {
        EmisorDeTokensDePrueba.registrarJwks(registro);
    }

    @Autowired
    private ApplicationContext contexto;

    @LocalServerPort
    private int puerto;

    @Test
    @DisplayName("el contexto de la aplicacion arranca por completo")
    void arranca() {
        assertNotNull(contexto);
        assertNotNull(contexto.getBean(ComentariosController.class),
                "el controlador de la HU-COM-001 debe estar publicado");
    }

    @Test
    @DisplayName("de punta a punta: sin token 401, con token real el comentario se guarda a nombre del uid del token")
    void publicaConTokenRealYGuardaElAutorDelToken() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        String ruta = "http://localhost:" + puerto + "/api/v1/products/espada-it/comments";
        String cuerpo = "{\"autorId\":\"suplantado\",\"apodoAutor\":\"Otro\",\"texto\":\"Llega bien\",\"imagenes\":[]}";

        HttpResponse<String> sinToken = http.send(
                HttpRequest.newBuilder(URI.create(ruta))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, sinToken.statusCode(), "sin token la cadena real debe cortar antes del controlador");

        UUID uid = UUID.randomUUID();
        String token = EmisorDeTokensDePrueba.emisor().tokenDeJugador("Lyra_IT", uid);
        HttpResponse<String> conToken = http.send(
                HttpRequest.newBuilder(URI.create(ruta))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + token)
                        .POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build(),
                HttpResponse.BodyHandlers.ofString());
        // 202 y no 201: aqui no hay lista negra que consultar y el filtro
        // falla cerrado (queda en revision). Lo que se afirma es la identidad.
        assertTrue(conToken.statusCode() == 201 || conToken.statusCode() == 202, conToken.body());
        assertTrue(conToken.body().contains("\"autorId\":\"" + uid + "\""),
                "el autor guardado es el uid del token, no el del cuerpo: " + conToken.body());
        assertTrue(conToken.body().contains("\"apodoAutor\":\"Lyra_IT\""), conToken.body());

        HttpResponse<String> hilo = http.send(
                HttpRequest.newBuilder(URI.create(ruta)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, hilo.statusCode(), "leer el hilo es publico");
        assertTrue(hilo.body().contains("\"productoId\":\"espada-it\""), hilo.body());
    }

    @Test
    @DisplayName("RestClient.Builder esta disponible para el cliente de la lista negra")
    void hayClienteHttp() {
        // Comprueba justo lo que fallo en el servidor: que la autoconfiguracion
        // de RestClient esta en el classpath y el cliente se pudo construir.
        assertNotNull(contexto.getBean(RestClient.Builder.class));
        assertNotNull(contexto.getBean("restClientComentarios", RestClient.class));
    }
}
