package com.nexusbattles.ms_ecommerce;

import com.nexusbattles.ms_ecommerce.controller.VitrinaController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El servicio arranca de verdad, y su salud esta donde el despliegue la busca.
 *
 * <p><b>Por que existe.</b> ms-ecommerce era el unico de los diecisiete
 * servicios del monorepo sin ninguna prueba que levantara el contexto: sus tres
 * pruebas eran de Mockito puro. Compilaba, se empaquetaba, se publicaba la
 * imagen... y nadie habia comprobado nunca que arrancara.
 *
 * <p><b>Que destapa.</b> El servicio declara
 * {@code server.servlet.context-path=/ecommerce}, asi que TODO cuelga de ahi,
 * Actuator incluido: su salud vive en {@code /ecommerce/actuator/health}.
 * {@code scripts/cd/desplegar.sh} la buscaba en {@code /api/v1/actuator/health}
 * —el comentario de {@code docker-compose.ms-ecommerce.yml} afirmaba que el
 * context-path era {@code /api/v1}, y no lo es—, de modo que el despliegue
 * habria esperado tres minutos y dado el servicio por muerto aunque estuviera
 * perfectamente vivo.
 *
 * <p>Las dos comprobaciones de abajo fijan eso: si alguien cambia el
 * context-path, esta prueba se pone roja antes de que lo haga el despliegue.
 *
 * <p>Con PostgreSQL de verdad y no con H2: Flyway aplica V1 contra el motor
 * real y Hibernate ({@code ddl-auto=validate}) comprueba que las entidades
 * casan con esas tablas, que es lo que pasa en el servidor. Con H2 la prueba
 * diria "arranca" sin haber probado lo que se despliega.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DisplayName("ms-ecommerce arranca y expone su salud donde el despliegue la busca")
class ArranqueDeLaAplicacionIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> BASE = new PostgreSQLContainer<>("postgres:15-alpine");

    @LocalServerPort
    private int puerto;

    @Autowired
    private VitrinaController vitrina;

    private RestClient cliente() {
        return RestClient.create("http://localhost:" + puerto);
    }

    @Test
    @DisplayName("el contexto levanta con la configuracion que se despliega")
    void elContextoLevanta() {
        // Si la clase de prueba llega aqui es que el contexto se construyo.
        // La comprobacion explicita es que el bean de la vitrina existe: es el
        // que sirve el listado que consume la tienda.
        assertTrue(vitrina != null, "sin la vitrina no hay catalogo que mostrar");
    }

    @Test
    @DisplayName("la salud vive bajo /ecommerce, no bajo /api/v1")
    void laSaludEstaDondeElDespliegueLaBusca() {
        ResponseEntity<String> bajoEcommerce = cliente()
                .get().uri("/ecommerce/actuator/health")
                .retrieve().toEntity(String.class);

        // Sin desactivar el manejo por defecto, un 404 se convertiria en
        // excepcion y la prueba no podria afirmar nada sobre el codigo.
        ResponseEntity<String> bajoApiV1 = cliente()
                .get().uri("/api/v1/actuator/health")
                .retrieve()
                .onStatus(estado -> true, (peticion, respuesta) -> { })
                .toEntity(String.class);

        assertAll(
                () -> assertEquals(200, bajoEcommerce.getStatusCode().value()),
                () -> assertTrue(bajoEcommerce.getBody().contains("\"status\":\"UP\""),
                        "el despliegue busca literalmente \"status\":\"UP\""),
                () -> assertEquals(404, bajoApiV1.getStatusCode().value(),
                        "si esto deja de ser 404, revisar ruta_salud_de() en desplegar.sh"));
    }
}
