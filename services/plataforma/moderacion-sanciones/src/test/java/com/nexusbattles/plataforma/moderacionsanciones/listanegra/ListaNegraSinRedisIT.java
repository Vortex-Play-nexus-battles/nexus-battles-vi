package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis caido: la moderacion sigue respondiendo, desde PostgreSQL.
 *
 * <p>Antes, sin manejador de errores de cache, la excepcion de conexion subia
 * hasta el controlador: la verificacion respondia 500 y ms-identidad, que
 * falla hacia el lado abierto, aprobaba el apodo sin mirar la lista. Aqui
 * Redis apunta a un puerto donde no escucha nadie desde el arranque.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.jpa.hibernate.ddl-auto=validate", "sanciones.avisos.reintento-ms=3600000"})
@DisplayName("Lista negra · con Redis caido no hay 500: se lee de PostgreSQL")
class ListaNegraSinRedisIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void redisCaido(DynamicPropertyRegistry registro) {
        EmisorDeTokensDePrueba.registrarJwks(registro);
        registro.add("spring.data.redis.url", () -> "redis://127.0.0.1:" + puertoSinNadie());
    }

    @LocalServerPort
    private int puerto;

    private final HttpClient http = HttpClient.newHttpClient();

    private static int puertoSinNadie() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private HttpResponse<String> post(String ruta, String token, String cuerpo) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo));
        if (token != null) {
            peticion.header("Authorization", "Bearer " + token);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("verificar responde 200 con el resultado correcto, y un alta nueva se ve al momento")
    void sinRedis() throws Exception {
        HttpResponse<String> spiderman = post("/api/v1/lista-negra/verificar", null,
                "{\"texto\":\"Spider-Man\",\"contexto\":\"APODO\"}");
        assertThat(spiderman.statusCode()).as(spiderman.body()).isEqualTo(200);
        assertThat(spiderman.body()).contains("\"aprobado\":false").contains("\"accion\":\"RECHAZAR\"");

        HttpResponse<String> alta = post("/api/v1/lista-negra/terminos",
                EmisorDeTokensDePrueba.emisor().tokenDeUsuario("admin_it", UUID.randomUUID(), "ADMINISTRADOR"),
                "{\"termino\":\"sinredisprueba\"}");
        assertThat(alta.statusCode()).as("la invalidacion fallida no tumba el alta: " + alta.body()).isEqualTo(201);

        HttpResponse<String> nuevo = post("/api/v1/lista-negra/verificar", null, "{\"texto\":\"sin redis prueba\"}");
        assertThat(nuevo.statusCode()).isEqualTo(200);
        assertThat(nuevo.body()).contains("\"aprobado\":false");
    }
}
