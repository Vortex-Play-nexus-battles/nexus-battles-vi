package nexus.configuracion;

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
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * La cadena de seguridad de heroes, ejercitada con tokens <b>reales</b>.
 *
 * <p>No hay {@code JwtDecoder} falso ni principal inventado: el servicio apunta
 * su {@code jwk-set-uri} al JWKS de {@link EmisorDeTokensDePrueba}, que firma
 * con RSA y sirve su clave publica por HTTP igual que
 * {@code GET /api/v1/auth/jwks} de ms-identidad. Lo que aqui se acepta o se
 * rechaza es lo mismo que aceptaria o rechazaria el servicio desplegado:
 * firma verificada, caducidad comprobada, rol traducido por
 * {@code ConversorRolesJwt}.
 *
 * <p>Antes de R8.1 <b>todas</b> estas rutas respondian 200 sin token, incluidas
 * las cuatro POST, con el borde publicandolas a Internet.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Seguridad de heroes")
class SeguridadDeHeroesTest {

    private static final EmisorDeTokensDePrueba EMISOR = EmisorDeTokensDePrueba.emisor();

    // Cuerpos que el dominio acepta, copiados de EquiposApiTest y
    // EstrategiasApiTest: asi un 200 significa «paso la puerta Y el dominio
    // contesto», y no se confunde un rechazo de negocio con uno de seguridad.
    private static final String EQUIPO_VALIDO = """
            {"heroes":["Chamán","Guerrero Tanque","Mago Fuego"]}""";
    private static final String ESTRATEGIA_VALIDA = """
            {"heroe":"Guerrero Armas","nivel":8,"rotaciones":[
              {"pasos":["Golpe de tormenta","Embate sangriento","Ataque básico"]}]}""";
    private static final String PROGRESO_VALIDO = """
            {"nivel":1,"experiencia":0,"puntos":250}""";

    @DynamicPropertySource
    static void jwks(DynamicPropertyRegistry registro) {
        EmisorDeTokensDePrueba.registrarJwks(registro);
    }

    @Autowired
    private MockMvc mvc;

    @Nested
    @DisplayName("El catalogo del juego es publico")
    class Catalogo {

        @Test
        @DisplayName("listar heroes responde sin token: son fichas del producto, no datos de nadie")
        void listarEsPublico() throws Exception {
            mvc.perform(get("/api/v1/heroes")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("la ficha de un heroe responde sin token")
        void fichaEsPublica() throws Exception {
            mvc.perform(get("/api/v1/heroes/{nombre}", "Guerrero Tanque"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("la tabla de niveles responde sin token")
        void tablaDeNivelesEsPublica() throws Exception {
            mvc.perform(get("/api/v1/progresion/niveles")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("la experiencia por enemigo responde sin token")
        void experienciaPorEnemigoEsPublica() throws Exception {
            mvc.perform(get("/api/v1/progresion/experiencia-por-enemigo/{dado}", 8))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("la sonda de salud responde sin token (regla 3)")
        void saludEsPublica() throws Exception {
            mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("El calculo a peticion del jugador exige un usuario")
    class CalculoDelJugador {

        @Test
        @DisplayName("sin token, validar un equipo responde 401 con problem detail")
        void sinTokenEs401() throws Exception {
            mvc.perform(post("/api/v1/equipos/validacion")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(EQUIPO_VALIDO))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.type").value("urn:nexus:problema:no-autenticado"))
                    .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        @DisplayName("con token de jugador, validar un equipo responde")
        void conJugadorResponde() throws Exception {
            mvc.perform(post("/api/v1/equipos/validacion")
                            .header("Authorization", "Bearer "
                                    + EMISOR.tokenDeJugador("lyra", UUID.randomUUID()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(EQUIPO_VALIDO))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("con token de servicio tambien: un servicio puede calcular por el jugador")
        void conServicioResponde() throws Exception {
            mvc.perform(post("/api/v1/estrategias/validacion")
                            .header("Authorization", "Bearer "
                                    + EMISOR.tokenDeServicio("salas-partidas"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ESTRATEGIA_VALIDA))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("La logica de servidor es exclusiva de servicios")
    class LogicaDeServidor {

        @Test
        @DisplayName("un jugador NO puede decidir la jugada de la maquina: 403")
        void decisionConJugadorEs403() throws Exception {
            mvc.perform(post("/api/v1/estrategias/decision")
                            .header("Authorization", "Bearer "
                                    + EMISOR.tokenDeJugador("lyra", UUID.randomUUID()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.type").value("urn:nexus:problema:acceso-denegado"));
        }

        @Test
        @DisplayName("un administrador tampoco: el rol alto no es el rol correcto")
        void decisionConAdministradorEs403() throws Exception {
            mvc.perform(post("/api/v1/estrategias/decision")
                            .header("Authorization", "Bearer " + EMISOR.tokenDeUsuario(
                                    "raiz", UUID.randomUUID(), "SUPER_ADMINISTRADOR"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("un jugador NO puede calcularse su propia progresion: 403")
        void progresionConJugadorEs403() throws Exception {
            mvc.perform(post("/api/v1/progresion/experiencia")
                            .header("Authorization", "Bearer "
                                    + EMISOR.tokenDeJugador("lyra", UUID.randomUUID()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PROGRESO_VALIDO))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("un servicio si: pasa la puerta y el dominio contesta")
        void progresionConServicioPasaLaPuerta() throws Exception {
            mvc.perform(post("/api/v1/progresion/experiencia")
                            .header("Authorization", "Bearer "
                                    + EMISOR.tokenDeServicio("salas-partidas"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PROGRESO_VALIDO))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Un token que no es legitimo no entra")
    class TokenNoLegitimo {

        @Test
        @DisplayName("firmado por otra clave: 401, no 403")
        void firmadoPorOtroEs401() throws Exception {
            mvc.perform(post("/api/v1/equipos/validacion")
                            .header("Authorization", "Bearer "
                                    + EMISOR.tokenFirmadoPorOtro("lyra", UUID.randomUUID()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(EQUIPO_VALIDO))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("caducado: 401")
        void caducadoEs401() throws Exception {
            mvc.perform(post("/api/v1/equipos/validacion")
                            .header("Authorization", "Bearer "
                                    + EMISOR.tokenCaducado("lyra", UUID.randomUUID()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(EQUIPO_VALIDO))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("una cadena cualquiera: 401")
        void basuraEs401() throws Exception {
            mvc.perform(post("/api/v1/equipos/validacion")
                            .header("Authorization", "Bearer no-soy-un-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(EQUIPO_VALIDO))
                    .andExpect(status().isUnauthorized());
        }
    }
}
