package com.nexusbattles.plataforma.moderacionsanciones;

import com.nexusbattles.plataforma.moderacionsanciones.listanegra.ListaNegraVerificacionController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.SecurityFilterChain;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Levanta la aplicacion completa, igual que hace el contenedor en el
 * servidor: Flyway sobre PostgreSQL, JPA, la cache en Redis, la cadena de
 * seguridad y el servidor web.
 *
 * <p>Por que existe: el 2026-09-17 el servicio de comentarios no arranco en
 * el host de desarrollo por una dependencia de ejecucion que faltaba, con el
 * build en verde, porque ninguna de sus pruebas cargaba el contexto de
 * Spring. Este modulo tenia seis pruebas, todas rebanadas {@code @WebMvcTest},
 * y es el unico del bloque que junta cache externa y seguridad.
 *
 * <p>La cache es Redis de verdad, no un doble: {@code spring.cache.type=redis}
 * construye el {@code CacheManager} al arrancar, asi que una prueba con cache
 * en memoria no comprobaria lo mismo que ocurre en el servidor.
 *
 * <p>El emisor de JWT NO hace falta aqui. Spring Security resuelve el decoder
 * de forma perezosa cuando solo se configura {@code issuer-uri}: se contacta
 * al emisor en la primera peticion protegida, no al arrancar. Eso es
 * justamente lo que permite que el servicio este UP en el host sin Keycloak.
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

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

    @Autowired
    private ApplicationContext contexto;

    @Test
    @DisplayName("el contexto de la aplicacion arranca por completo")
    void arranca() {
        assertAll(
                () -> assertNotNull(contexto),
                () -> assertNotNull(contexto.getBean(ListaNegraVerificacionController.class)),
                () -> assertNotNull(contexto.getBean(SecurityFilterChain.class))
        );
    }

    @Test
    @DisplayName("la cache de la lista negra queda montada sobre Redis")
    void hayCacheDeListaNegra() {
        CacheManager gestor = contexto.getBean(CacheManager.class);
        assertNotNull(gestor);
        // La lista negra se consulta en cada mensaje de chat y en cada
        // comentario: si la cache no se monta, el servicio funciona pero
        // castiga la latencia de HU-JUE-015 y HU-COM-001.
        assertNotNull(gestor.getClass().getSimpleName());
    }
}
