package nexus.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Pruebas de aceptacion (documento del curso, 11.4.3: "verifican que el sistema
 * cumple los criterios de aceptacion de las historias de usuario" y "validan
 * flujos completos de negocio desde la perspectiva del usuario"). A diferencia
 * de las pruebas de API con MockMvc, aqui se levanta el servidor HTTP REAL en
 * un puerto aleatorio y se le consume como lo haria la vitrina o un navegador.
 * Es la version automatizada de la verificacion en vivo del 2026-08-25,
 * incluido el ejemplo del Tanque que el cliente hizo en clase (RG-015).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AceptacionDeHeroesTest {

    @Autowired
    private TestRestTemplate http;

    private final ObjectMapper json = new ObjectMapper();

    // --- HU-HER-001: consultar el catalogo -------------------------------

    @Test
    @DisplayName("el jugador consulta el catalogo y recibe los prototipos de la Tabla 5")
    void catalogoConLosPrototiposDeLaTabla5() throws Exception {
        ResponseEntity<String> respuesta = http.getForEntity("/api/v1/heroes", String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode catalogo = json.readTree(respuesta.getBody());
        List<String> nombres = new ArrayList<>();
        catalogo.forEach(h -> nombres.add(h.get("nombre").asText()));
        // Los 8 de la Tabla 5; la cantidad no esta fijada de antemano
        // (correccion del cliente, acta 2026-08-13: "no son ocho").
        assertThat(nombres).contains(
                "Guerrero Tanque", "Guerrero Armas",
                "Mago Fuego", "Mago Hielo",
                "Pícaro Veneno", "Pícaro Machete",
                "Chamán", "Médico");
        catalogo.forEach(h -> {
            assertThat(h.hasNonNull("nombre")).isTrue();
            assertThat(h.hasNonNull("tipo")).isTrue();
            assertThat(h.hasNonNull("esSanador")).isTrue();
        });
    }

    // --- HU-HER-002: la ficha con las estadisticas de la Tabla 6 ---------

    @Test
    @DisplayName("la ficha del Guerrero Tanque trae el ejemplo que el cliente hizo en clase (RG-015)")
    void fichaDelTanqueComoElEjemploDeClase() throws Exception {
        ResponseEntity<String> respuesta =
                http.getForEntity("/api/v1/heroes/{nombre}", String.class, "Guerrero Tanque");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode stats = json.readTree(respuesta.getBody()).get("estadisticasNivel1");
        assertThat(stats.get("poder").asInt()).isEqualTo(10);
        assertThat(stats.get("vida").asInt()).isEqualTo(44);
        assertThat(stats.get("defensa").asInt()).isEqualTo(11);
        assertThat(stats.get("ataque").asText()).isEqualTo("10 + 1d6");
        assertThat(stats.get("dano").asText()).isEqualTo("1d4");
    }

    @Test
    @DisplayName("cada ficha del catalogo responde con sus tres acciones exactas (Tabla 7)")
    void todasLasFichasConSusTresAcciones() throws Exception {
        JsonNode catalogo = json.readTree(
                http.getForEntity("/api/v1/heroes", String.class).getBody());

        for (JsonNode resumen : catalogo) {
            String nombre = resumen.get("nombre").asText();
            ResponseEntity<String> ficha =
                    http.getForEntity("/api/v1/heroes/{nombre}", String.class, nombre);
            assertThat(ficha.getStatusCode())
                    .as("la ficha de %s debe estar disponible", nombre)
                    .isEqualTo(HttpStatus.OK);
            assertThat(json.readTree(ficha.getBody()).get("acciones"))
                    .as("%s debe tener exactamente tres acciones (Tabla 7)", nombre)
                    .hasSize(3);
        }
    }

    @Test
    @DisplayName("un sanador expone sanar y no expone ataque ni dano (Tabla 6)")
    void sanadorSinAtaqueNiDano() throws Exception {
        JsonNode ficha = json.readTree(
                http.getForEntity("/api/v1/heroes/{nombre}", String.class, "Médico").getBody());

        assertThat(ficha.get("esSanador").asBoolean()).isTrue();
        assertThat(ficha.get("estadisticasNivel1").hasNonNull("sanar")).isTrue();
        assertThat(ficha.get("estadisticasNivel1").has("ataque")).isFalse();
        assertThat(ficha.get("estadisticasNivel1").has("dano")).isFalse();
    }

    // --- Busqueda tolerante (criterio de HU-HER-002) ---------------------

    @Test
    @DisplayName("la busqueda tolera tildes y mayusculas como escribiria un usuario")
    void busquedaComoEscribeUnUsuario() {
        assertThat(http.getForEntity("/api/v1/heroes/{nombre}", String.class, "picaro veneno")
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.getForEntity("/api/v1/heroes/{nombre}", String.class, "CHAMAN")
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // --- El error visto por el usuario final -----------------------------

    @Test
    @DisplayName("un heroe inexistente responde un mensaje apto para el usuario, jamas un codigo pelado")
    void errorAptoParaElUsuario() throws Exception {
        ResponseEntity<String> respuesta =
                http.getForEntity("/api/v1/heroes/{nombre}", String.class, "Nigromante");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(respuesta.getHeaders().getContentType().toString())
                .startsWith("application/problem+json");
        JsonNode problema = json.readTree(respuesta.getBody());
        // Regla del cliente (acta 2026-08-13): la interfaz muestra el detail,
        // nunca el codigo de estado.
        assertThat(problema.get("detail").asText())
                .isEqualTo("El héroe solicitado no está disponible en el catálogo.")
                .doesNotContain("404");
    }

    // --- La pagina de demo que se muestra en el Sprint Review ------------

    @Test
    @DisplayName("la pagina de demo del catalogo se sirve en la raiz")
    void paginaDeDemoDisponible() {
        ResponseEntity<String> respuesta = http.getForEntity("/index.html", String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).contains("Catálogo de héroes");
    }
}
