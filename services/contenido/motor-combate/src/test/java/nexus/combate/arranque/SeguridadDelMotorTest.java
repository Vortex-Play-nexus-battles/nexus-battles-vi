package nexus.combate.arranque;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * La cadena de seguridad del motor, con tokens reales.
 *
 * <p>La afirmacion que importa es la de {@link SoloServicios}: resolver un
 * ataque decide quien gana la partida, y un jugador autenticado —incluso un
 * super administrador— no debe poder pedirlo. Antes de R8.1 lo podia pedir
 * cualquiera sin token.
 *
 * <p>Para el caso positivo no se afirma 200 sino «paso la puerta»: el motor
 * llama a heroes para resolver al atacante y aqui esa URL apunta a un puerto
 * cerrado a proposito, asi que contestara con el 503 de catalogo caido. Lo que
 * se prueba es la seguridad, no la resolucion — de eso ya se encargan
 * {@code ResolverAtaqueTest} y {@code CombateControllerTest}.
 */
@SpringBootTest(properties = "motor.heroes.url=http://localhost:65535")
@AutoConfigureMockMvc
@DisplayName("Seguridad del motor de combate")
class SeguridadDelMotorTest {

    private static final EmisorDeTokensDePrueba EMISOR = EmisorDeTokensDePrueba.emisor();

    private static final String ATAQUE = """
            {
              "heroeAtacante": "Guerrero Tanque",
              "defensaObjetivo": 11,
              "distribucion": { "prototipo": "GUERRERO_TANQUE" }
            }
            """;

    @DynamicPropertySource
    static void jwks(DynamicPropertyRegistry registro) {
        EmisorDeTokensDePrueba.registrarJwks(registro);
    }

    @Autowired
    private MockMvc mvc;

    @Nested
    @DisplayName("Resolver un ataque es exclusivo de servicios")
    class SoloServicios {

        @Test
        @DisplayName("sin token: 401 con problem detail, no un ataque resuelto")
        void sinTokenEs401() throws Exception {
            mvc.perform(post("/api/v1/combate/ataques")
                            .contentType("application/json")
                            .content(ATAQUE))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.type").value("urn:nexus:problema:no-autenticado"));
        }

        @Test
        @DisplayName("con token de jugador: 403 — el combate lo arbitra el servidor")
        void conJugadorEs403() throws Exception {
            mvc.perform(post("/api/v1/combate/ataques")
                            .header("Authorization", "Bearer "
                                    + EMISOR.tokenDeJugador("lyra", UUID.randomUUID()))
                            .contentType("application/json")
                            .content(ATAQUE))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.type").value("urn:nexus:problema:acceso-denegado"));
        }

        @Test
        @DisplayName("con token de super administrador: 403 tambien")
        void conAdministradorEs403() throws Exception {
            mvc.perform(post("/api/v1/combate/ataques")
                            .header("Authorization", "Bearer " + EMISOR.tokenDeUsuario(
                                    "raiz", UUID.randomUUID(), "SUPER_ADMINISTRADOR"))
                            .contentType("application/json")
                            .content(ATAQUE))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("con la credencial de salas-partidas: pasa la puerta")
        void conServicioPasaLaPuerta() throws Exception {
            int estado = mvc.perform(post("/api/v1/combate/ataques")
                            .header("Authorization", "Bearer "
                                    + EMISOR.tokenDeServicio("salas-partidas"))
                            .contentType("application/json")
                            .content(ATAQUE))
                    .andReturn().getResponse().getStatus();

            org.junit.jupiter.api.Assertions.assertTrue(
                    estado != 401 && estado != 403,
                    "un servicio autenticado no puede salir 401 ni 403; salio " + estado);
        }
    }

    @Nested
    @DisplayName("El resto de la superficie")
    class Resto {

        @Test
        @DisplayName("las distribuciones exigen estar autenticado")
        void distribucionesSinTokenEs401() throws Exception {
            mvc.perform(get("/api/v1/combate/distribuciones"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("las distribuciones responden a un jugador autenticado")
        void distribucionesConJugadorEs200() throws Exception {
            mvc.perform(get("/api/v1/combate/distribuciones")
                            .header("Authorization", "Bearer "
                                    + EMISOR.tokenDeJugador("lyra", UUID.randomUUID())))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("la sonda de salud sigue abierta (regla 3)")
        void saludEsPublica() throws Exception {
            mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("un token firmado por otra clave no entra: 401")
        void firmadoPorOtroEs401() throws Exception {
            mvc.perform(get("/api/v1/combate/distribuciones")
                            .header("Authorization", "Bearer "
                                    + EMISOR.tokenFirmadoPorOtro("lyra", UUID.randomUUID())))
                    .andExpect(status().isUnauthorized());
        }
    }
}
