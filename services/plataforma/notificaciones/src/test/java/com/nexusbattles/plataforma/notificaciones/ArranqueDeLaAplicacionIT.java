package com.nexusbattles.plataforma.notificaciones;

import com.nexusbattles.plataforma.notificaciones.bandeja.CanalStomp;
import com.nexusbattles.plataforma.notificaciones.bandeja.NotificacionesController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Levanta la aplicacion completa, igual que hace el contenedor en el
 * servidor: Flyway sobre PostgreSQL, JPA, el servidor web y el canal STOMP
 * de HU-NOT-006.
 *
 * <p>Por que existe: el 2026-09-17 el servicio de comentarios no arranco en
 * el host de desarrollo por una dependencia de ejecucion que faltaba, con el
 * build en verde, porque ninguna de sus pruebas cargaba el contexto de
 * Spring. Este modulo tenia ocho pruebas, todas rebanadas {@code @WebMvcTest}
 * o unitarias, y es el que mas superficie de arranque tiene del bloque:
 * WebSocket, JPA y Flyway a la vez.
 *
 * <p>Con {@code ddl-auto=validate}: asi Hibernate compara su mapeo contra las
 * columnas que dejo Flyway y el esquema no puede irse separando del modelo
 * sin que nadie se entere. Mismo criterio que RepositorioSalasJpaIT.
 *
 * <p>Sin {@code disabledWithoutDocker}: una prueba de integracion omitida no
 * es una prueba que pasa, es una que no se ejecuto.
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

    @Autowired
    private ApplicationContext contexto;

    @Test
    @DisplayName("el contexto de la aplicacion arranca por completo")
    void arranca() {
        assertAll(
                () -> assertNotNull(contexto),
                () -> assertNotNull(contexto.getBean(NotificacionesController.class))
        );
    }

    @Test
    @DisplayName("el canal STOMP de la bandeja queda publicado")
    void hayCanalEnTiempoReal() {
        // Es lo que hace util a HU-NOT-006: sin este bean no hay sincronizacion
        // entre sesiones, y una rebanada web nunca lo levanta.
        assertAll(
                () -> assertNotNull(contexto.getBean(SimpMessagingTemplate.class)),
                () -> assertNotNull(contexto.getBean(CanalStomp.class))
        );
    }
}
