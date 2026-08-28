package com.nexusbattles.plataforma.correo.envio;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@SpringBootTest
@Testcontainers
class EnviadorCorreoServiceIT {

    @Container
    static GenericContainer<?> mailpit = new GenericContainer<>(DockerImageName.parse("axllent/mailpit"))
            .withExposedPorts(1025, 8025);

    @DynamicPropertySource
    static void configurarSmtp(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", mailpit::getHost);
        registry.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
    }

    @Autowired
    private EnviadorCorreoService enviadorCorreoService;

    @Test
    void elCorreoEnviadoQuedaCapturadoEnMailpit() {
        enviadorCorreoService.enviar(
                "destino@nexusbattles.test",
                "Asunto de prueba",
                "email/plantilla-prueba",
                Map.of("mensaje", "Hola desde el test de integracion")
        );

        String urlMailpit = "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025);

        given()
            .baseUri(urlMailpit)
        .when()
            .get("/api/v1/messages")
        .then()
            .statusCode(200)
            .body("messages_count", greaterThanOrEqualTo(1))
            .body("messages[0].Subject", equalTo("Asunto de prueba"));
    }
}
