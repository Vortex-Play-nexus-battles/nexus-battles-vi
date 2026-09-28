package nexus.combate.api;

import nexus.combate.ClienteHeroesException;
import nexus.combate.IndiceNormal;
import nexus.combate.reglas.CatalogoDeCombate;
import nexus.combate.reglas.CatalogoDePrueba;
import nexus.combate.reglas.MotorDeAcciones;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /combate/acciones} y {@code /turnos} por HTTP, con el motor de
 * verdad sobre un catalogo en memoria: la forma del JSON de
 * {@code motor-combate.yaml} 1.2.0 de ida y de vuelta, y cada fallo con su
 * codigo y su {@code type}. La seguridad de estas rutas la prueba
 * {@code SeguridadDelMotorTest}; aqui los filtros van apagados por la misma
 * razon que en {@code CombateControllerTest}.
 */
@WebMvcTest(controllers = AccionesController.class)
@ContextConfiguration(classes = nexus.combate.arranque.MotorCombateApplication.class)
@Import({nexus.combate.arranque.SeguridadConfig.class, AccionesControllerTest.MotorDePrueba.class})
@TestPropertySource(properties = {
        "motor.heroes.url=http://localhost:65535",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:65535/jwks"
})
@AutoConfigureMockMvc(addFilters = false)
class AccionesControllerTest {

    /** El motor real, sobre el catalogo en memoria; «Catalogo Caido» simula heroes apagado. */
    @TestConfiguration
    static class MotorDePrueba {

        @Bean
        ServicioDeCombate servicioDeCombate() {
            CatalogoDePrueba enMemoria = new CatalogoDePrueba();
            CatalogoDeCombate catalogo = (prototipo, nivel) -> {
                if ("Catalogo Caido".equals(prototipo)) {
                    throw new ClienteHeroesException("heroes no responde");
                }
                return enMemoria.ficha(prototipo, nivel);
            };
            return new ServicioDeCombate(new MotorDeAcciones(catalogo, IndiceNormal.porOmision()));
        }
    }

    private static final String DOS = """
            [
              { "id": "armas", "prototipo": "Guerrero Armas", "vidaActual": 44 },
              { "id": "tanque", "prototipo": "Guerrero Tanque", "vidaActual": 44 }
            ]
            """;

    @Autowired
    private MockMvc mvc;

    private org.springframework.test.web.servlet.ResultActions accion(String cuerpo) throws Exception {
        return mvc.perform(post("/api/v1/combate/acciones").contentType("application/json").content(cuerpo));
    }

    @Test
    @DisplayName("una accion resuelta sale con la forma del contrato")
    void accionResuelta() throws Exception {
        accion("""
                { "accion": "ATAQUE_BASICO", "ejecutor": "armas", "objetivo": "tanque", "semilla": 7,
                  "combatientes": %s }
                """.formatted(DOS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accion").value("ATAQUE_BASICO"))
                .andExpect(jsonPath("$.accionEjecutada").value("ATAQUE_BASICO"))
                .andExpect(jsonPath("$.enValorBase").value(false))
                .andExpect(jsonPath("$.tipo").value("ATAQUE"))
                .andExpect(jsonPath("$.ataque.acierta").isBoolean())
                .andExpect(jsonPath("$.ataque.defensaObjetivo").value(11))
                .andExpect(jsonPath("$.eventos").isArray())
                .andExpect(jsonPath("$.afectados").isArray())
                .andExpect(jsonPath("$.combatientes.length()").value(2))
                .andExpect(jsonPath("$.combatientes[0].id").value("armas"))
                .andExpect(jsonPath("$.combatientes[0].nivel").value(1))
                .andExpect(jsonPath("$.combatientes[0].poderActual").value(8))
                .andExpect(jsonPath("$.combatientes[0].turnosJugados").value(1))
                .andExpect(jsonPath("$.combatientes[0].estadisticas.vida").value(44))
                .andExpect(jsonPath("$.combatientes[0].estadisticas.ataque.caras").value(6))
                .andExpect(jsonPath("$.combatientes[0].acciones[*].codigo").value(hasItem("Embate sangriento")))
                .andExpect(jsonPath("$.combatientes[0].acciones[0].disponible").value(true))
                .andExpect(jsonPath("$.combatientes[0].recargas").isMap());
    }

    @Test
    @DisplayName("un efecto activo viaja con su tipo y sale con hastaSuTurno")
    void efectos() throws Exception {
        accion("""
                { "accion": "Mano de piedra", "ejecutor": "tanque", "semilla": 1,
                  "combatientes": [
                    { "id": "armas", "prototipo": "Guerrero Armas", "vidaActual": 44 },
                    { "id": "tanque", "prototipo": "Guerrero Tanque", "nivel": 4, "vidaActual": 100,
                      "poderActual": 10,
                      "efectos": [ { "codigo": "PIQUETE", "nombre": "Piquete", "tipo": "BONO_ATAQUE",
                                     "valor": 1, "turnos": 2, "origen": "tanque" } ] }
                  ] }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objetivo").value("tanque"))
                .andExpect(jsonPath("$.ataque").doesNotExist())
                .andExpect(jsonPath("$.combatientes[1].efectos[?(@.codigo == 'MANO_DE_PIEDRA')].hastaSuTurno")
                        .value(hasItem(true)))
                .andExpect(jsonPath("$.combatientes[1].efectos[?(@.codigo == 'PIQUETE')].turnos")
                        .value(hasItem(1)))
                .andExpect(jsonPath("$.combatientes[1].recargas['Mano de piedra']").value(1));
    }

    @Test
    @DisplayName("una accion que no se puede jugar es 409 con su motivo")
    void accionNoPermitida() throws Exception {
        accion("""
                { "accion": "Mano de piedra", "ejecutor": "tanque", "combatientes": %s }
                """.formatted(DOS))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/accion-no-permitida"))
                .andExpect(jsonPath("$.motivo").value("BLOQUEADA_POR_NIVEL"))
                .andExpect(jsonPath("$.detail").value(containsString("nivel 4")));
    }

    @Test
    @DisplayName("un tipo de efecto que no existe es 400: el JSON no cumple el contrato")
    void jsonIlegible() throws Exception {
        accion("""
                { "accion": "ATAQUE_BASICO", "ejecutor": "armas",
                  "combatientes": [
                    { "id": "armas", "prototipo": "Guerrero Armas", "vidaActual": 44,
                      "efectos": [ { "codigo": "X", "tipo": "INVENTADO", "valor": 1, "turnos": 1 } ] },
                    { "id": "tanque", "prototipo": "Guerrero Tanque", "vidaActual": 44 }
                  ] }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/peticion-invalida"));
    }

    @Test
    @DisplayName("una peticion a medias es 400 con su tipo")
    void peticionInvalida() throws Exception {
        accion("""
                { "accion": "ATAQUE_BASICO", "ejecutor": "armas" }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/peticion-invalida"));
        mvc.perform(post("/api/v1/combate/acciones").contentType("application/json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("una cota del dominio incumplida (nivel 9) es 400")
    void cotaDelDominio() throws Exception {
        accion("""
                { "accion": "ATAQUE_BASICO", "ejecutor": "armas",
                  "combatientes": [
                    { "id": "armas", "prototipo": "Guerrero Armas", "nivel": 9, "vidaActual": 44 },
                    { "id": "tanque", "prototipo": "Guerrero Tanque", "vidaActual": 44 }
                  ] }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("1 a 8")));
    }

    @Test
    @DisplayName("un prototipo que el catalogo no tiene es 404")
    void heroeNoEncontrado() throws Exception {
        accion("""
                { "accion": "ATAQUE_BASICO", "ejecutor": "armas",
                  "combatientes": [
                    { "id": "armas", "prototipo": "Guerrero Armas", "vidaActual": 44 },
                    { "id": "otro", "prototipo": "Arquero del Sur", "vidaActual": 44 }
                  ] }
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/heroe-no-encontrado"));
    }

    @Test
    @DisplayName("si el catalogo de heroes no responde es 503, no un combatiente inventado")
    void catalogoCaido() throws Exception {
        accion("""
                { "accion": "ATAQUE_BASICO", "ejecutor": "armas",
                  "combatientes": [
                    { "id": "armas", "prototipo": "Guerrero Armas", "vidaActual": 44 },
                    { "id": "otro", "prototipo": "Catalogo Caido", "vidaActual": 44 }
                  ] }
                """)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type")
                        .value("https://nexusbattles.local/errores/catalogo-de-heroes-no-disponible"));
    }

    @Test
    @DisplayName("el comienzo de un turno sale con la forma del contrato")
    void turno() throws Exception {
        mvc.perform(post("/api/v1/combate/turnos").contentType("application/json").content("""
                        { "combatiente": "tanque",
                          "combatientes": [
                            { "id": "tanque", "prototipo": "Guerrero Tanque", "vidaActual": 44, "poderActual": 2 },
                            { "id": "armas", "prototipo": "Guerrero Armas", "vidaActual": 44 }
                          ] }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.combatiente").value("tanque"))
                .andExpect(jsonPath("$.combatientes[0].poderActual").value(4))
                .andExpect(jsonPath("$.eventos[0].tipo").value("PODER_RECUPERADO"))
                .andExpect(jsonPath("$.eventos[0].cantidad").value(2));
    }
}
