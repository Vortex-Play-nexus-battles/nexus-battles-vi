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

    /**
     * Misma secuencia que el flujo de despliegue (cd.yml): el jar se compila
     * desde la raiz y el Dockerfile de heroes solo lo copia, con la raiz del
     * monorepo como contexto (-f al Dockerfile), como todo contenido.
     */
    private static void construirImagen(Path raizDelRepo) throws IOException, InterruptedException {
        // "sh gradlew" no necesita el bit de ejecucion (en un checkout de CI el
        // wrapper puede venir sin el); en Windows el .bat va por cmd.
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        String[] gradle = windows
                ? new String[] {"cmd", "/c", "gradlew.bat", ":services:contenido:heroes:bootJar", "--no-daemon", "-q"}
                : new String[] {"sh", "gradlew", ":services:contenido:heroes:bootJar", "--no-daemon", "-q"};
        ejecutar(raizDelRepo, "compilar el jar de heroes (gradle bootJar)", gradle);
        ejecutar(raizDelRepo, "construir la imagen de heroes (docker build)",
                "docker", "build", "--progress=plain", "-f", "services/contenido/heroes/Dockerfile", "-t", NOMBRE_IMAGEN, ".");
    }

    private static void ejecutar(Path raizDelRepo, String paso, String... comando) throws IOException, InterruptedException {
        Path log = Files.createTempFile("heroes-" + comando[comando.length - 1].replaceAll("[^A-Za-z0-9]", "") + "-", ".log");

        Process proceso = new ProcessBuilder(comando)
            .directory(raizDelRepo.toFile())
            .redirectErrorStream(true)
            .redirectOutput(log.toFile())
            .start();
        proceso.getOutputStream().close();

        boolean termino = proceso.waitFor(5, java.util.concurrent.TimeUnit.MINUTES);
        String salida = Files.readString(log, StandardCharsets.UTF_8);

        if (!termino || proceso.exitValue() != 0) {
            throw new IllegalStateException(
                "No se pudo " + paso + ". Raiz del repo: " + raizDelRepo + System.lineSeparator()
                    + "Sugerencia: reproduce a mano, desde la raiz del repo:" + System.lineSeparator()
                    + "./gradlew :services:contenido:heroes:bootJar" + System.lineSeparator()
                    + "docker build -f services/contenido/heroes/Dockerfile -t " + NOMBRE_IMAGEN + " ." + System.lineSeparator()
                    + "Salida:" + System.lineSeparator() + salida);
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
