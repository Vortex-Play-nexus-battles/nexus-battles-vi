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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de integración de envío real (HU-COR-001, subtarea SCRUM-1049).
 *
 * <p>Levanta un Mailpit efímero con Testcontainers, envía un correo de verdad
 * por SMTP y comprueba en la API de Mailpit que llegó con la plantilla
 * corporativa y el logo incrustado.
 *
 * <p>No usa RestAssured a propósito: la biblioteca depende de Groovy 4 y
 * thymeleaf-layout-dialect —que necesitamos para la plantilla— arrastra Groovy
 * 5, lo que rompe RestAssured con un NullPointerException en sus internos. El
 * cliente HTTP de la biblioteca estándar cubre el caso sin ese conflicto.
 */
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
    void elCorreoEnviadoLlegaConLaPlantillaCorporativaYElLogo() throws Exception {
        enviadorCorreoService.enviar(
                "destino@nexusbattles.test",
                "Asunto de prueba",
                "email/plantilla-prueba",
                Map.of("mensaje", "Hola desde el test de integracion"));

        String bandeja = obtener("/api/v1/messages");

        assertThat(bandeja)
                .as("el correo debe haber llegado a la bandeja")
                .contains("\"messages_count\":1")
                .contains("Asunto de prueba");

        String id = bandeja.split("\"ID\":\"")[1].split("\"")[0];
        String mensaje = obtener("/api/v1/message/" + id);

        assertThat(mensaje)
                .as("el encabezado corporativo debe venir en el cuerpo")
                .contains("THE NEXUS BATTLES VI");
        assertThat(mensaje)
                .as("el logo debe viajar incrustado y referenciado por cid")
                .contains("\"ContentID\":\"logo-nexus\"")
                .contains("cid:logo-nexus");
        assertThat(mensaje)
                .as("el logo no debe aparecer como adjunto suelto")
                .contains("\"Attachments\":[]");
    }

    private static String obtener(String ruta) throws Exception {
        String base = "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025);
        HttpResponse<String> respuesta = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(base + ruta)).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(respuesta.statusCode()).isEqualTo(200);
        return respuesta.body();
    }
}
