package nexus.combate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
class ClienteHeroesHttpIntegracionTest {

    private static final String NOMBRE_IMAGEN = "nexus-heroes-test:local";

    private static GenericContainer<?> heroesContainer;
    private static ClienteHeroesHttp cliente;

    @BeforeAll
    static void construirImagenYLevantarContenedor() throws IOException, InterruptedException {
        Path raizDelRepo = Paths.get("").toAbsolutePath().resolve("../../..").normalize();

        if (!imagenExiste()) {
            construirImagen(raizDelRepo);
        }

        heroesContainer = new GenericContainer<>(NOMBRE_IMAGEN)
            .withExposedPorts(8080)
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofMinutes(2));

        heroesContainer.start();

        String host = heroesContainer.getHost();
        Integer puerto = heroesContainer.getMappedPort(8080);
        cliente = new ClienteHeroesHttp(URI.create("http://" + host + ":" + puerto));
    }

    private static boolean imagenExiste() throws IOException, InterruptedException {
        Process consulta = new ProcessBuilder("docker", "images", "-q", NOMBRE_IMAGEN)
            .redirectErrorStream(true)
            .start();
        consulta.getOutputStream().close();

        String salida;
        try (var lector = consulta.inputReader()) {
            salida = lector.lines().collect(java.util.stream.Collectors.joining());
        }
        consulta.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);

        return !salida.isBlank();
    }

    private static void construirImagen(Path raizDelRepo) throws IOException, InterruptedException {
        Path logDocker = Files.createTempFile("docker-build-heroes-", ".log");

        ProcessBuilder pb = new ProcessBuilder(
                "docker", "build",
                "--progress=plain",
                "-f", "services/contenido/heroes/Dockerfile",
                "-t", NOMBRE_IMAGEN,
                ".")
            .directory(raizDelRepo.toFile())
            .redirectErrorStream(true)
            .redirectOutput(logDocker.toFile());

        Process construccion = pb.start();
        construccion.getOutputStream().close();

        boolean termino = construccion.waitFor(5, java.util.concurrent.TimeUnit.MINUTES);
        String salida = Files.readString(logDocker, StandardCharsets.UTF_8);

        if (!termino || construccion.exitValue() != 0) {
            throw new IllegalStateException(
                "No se pudo construir la imagen de heroes (docker build). "
                    + "Raiz del repo: " + raizDelRepo + System.lineSeparator()
                    + "Sugerencia: construye la imagen manualmente con:" + System.lineSeparator()
                    + "docker build -f services/contenido/heroes/Dockerfile -t " + NOMBRE_IMAGEN + " ."
                    + System.lineSeparator()
                    + "Salida de docker build:" + System.lineSeparator() + salida);
        }
    }

    @AfterAll
    static void detenerContenedor() {
        if (heroesContainer != null) {
            heroesContainer.stop();
        }
    }

    @Test
    void obtieneEstadisticasDelGuerreroTanqueDesdeElServicioReal() {
        EstadisticasHeroeRespuesta respuesta = cliente.obtenerEstadisticas("Guerrero Tanque");

        assertNotNull(respuesta);
        assertEquals(11, respuesta.defensa());
        assertNotNull(respuesta.ataqueDetalle());
        assertEquals(10, respuesta.ataqueDetalle().base());
        assertEquals(1, respuesta.ataqueDetalle().cantidadDados());
        assertEquals(6, respuesta.ataqueDetalle().caras());
    }

    @Test
    void sanadorNoTraeAtaqueDetalle() {
        EstadisticasHeroeRespuesta respuesta = cliente.obtenerEstadisticas("Chamán");

        assertNotNull(respuesta);
        assertNull(respuesta.ataqueDetalle());
    }

    @Test
    void heroeInexistenteLanzaExcepcion() {
        assertThrows(HeroeNoEncontradoException.class,
            () -> cliente.obtenerEstadisticas("Heroe Que No Existe En El Catalogo"));
    }
}
