package nexus.combate.api;

import nexus.combate.ClienteHeroesException;
import nexus.combate.HeroeNoEncontradoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API del motor — espejo de {@code contracts/openapi/motor-combate.yaml}.
 *
 * <p>Se comprueba la forma del contrato y que cada fallo salga con el codigo y
 * el {@code type} que el contrato promete. La resolucion en si ya esta probada
 * en {@code ResolverAtaqueTest} y no se repite.
 */
@WebMvcTest(controllers = CombateController.class)
class CombateControllerTest {

    private static final String CUERPO = """
            {
              "heroeAtacante": "Guerrero Tanque",
              "defensaObjetivo": 11,
              "distribucion": { "prototipo": "GUERRERO_TANQUE" }
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResolverAtaque resolverAtaque;

    @Test
    @DisplayName("un ataque resuelto sale con la forma exacta del contrato")
    void ataqueResuelto() throws Exception {
        when(resolverAtaque.ejecutar(any()))
                .thenReturn(new RespuestaDeAtaque("CAUSAR_DANO_CRITICO", 21, 14, 11, 3120));

        mockMvc.perform(post("/api/v1/combate/ataques")
                        .contentType("application/json")
                        .content(CUERPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria").value("CAUSAR_DANO_CRITICO"))
                .andExpect(jsonPath("$.danoAplicado").value(21))
                .andExpect(jsonPath("$.ataqueResuelto").value(14))
                .andExpect(jsonPath("$.defensaObjetivo").value(11))
                .andExpect(jsonPath("$.indiceTabla").value(3120));
    }

    @Test
    @DisplayName("un ataque fallido tambien es 200: fallar es un resultado del combate")
    void ataqueFallido() throws Exception {
        when(resolverAtaque.ejecutar(any()))
                .thenReturn(new RespuestaDeAtaque("SIN_EFECTO", 0, 12, 500, null));

        mockMvc.perform(post("/api/v1/combate/ataques")
                        .contentType("application/json")
                        .content(CUERPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.danoAplicado").value(0))
                .andExpect(jsonPath("$.indiceTabla").isEmpty());
    }

    @Test
    @DisplayName("una peticion mal formada sale 400 con su tipo")
    void peticionInvalida() throws Exception {
        when(resolverAtaque.ejecutar(any()))
                .thenThrow(new PeticionInvalida("Hace falta la defensa del objetivo."));

        mockMvc.perform(post("/api/v1/combate/ataques")
                        .contentType("application/json")
                        .content(CUERPO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type")
                        .value("https://nexusbattles.local/errores/peticion-invalida"));
    }

    @Test
    @DisplayName("una regla del dominio incumplida tambien es 400: lo manda mal quien llama")
    void reglaDelDominio() throws Exception {
        when(resolverAtaque.ejecutar(any()))
                .thenThrow(new IllegalArgumentException("Los porcentajes deben sumar 100."));

        mockMvc.perform(post("/api/v1/combate/ataques")
                        .contentType("application/json")
                        .content(CUERPO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.containsString("sumar 100")));
    }

    @Test
    @DisplayName("un heroe que no existe sale 404")
    void heroeNoEncontrado() throws Exception {
        when(resolverAtaque.ejecutar(any()))
                .thenThrow(new HeroeNoEncontradoException("Arquero del Sur", "no esta"));

        mockMvc.perform(post("/api/v1/combate/ataques")
                        .contentType("application/json")
                        .content(CUERPO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type")
                        .value("https://nexusbattles.local/errores/heroe-no-encontrado"));
    }

    @Test
    @DisplayName("un sanador sale 422, distinto de 404 y de dano cero")
    void heroeSinAtaque() throws Exception {
        when(resolverAtaque.ejecutar(any())).thenThrow(new HeroeSinAtaque("Chaman"));

        mockMvc.perform(post("/api/v1/combate/ataques")
                        .contentType("application/json")
                        .content(CUERPO))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type")
                        .value("https://nexusbattles.local/errores/heroe-sin-ataque"));
    }

    @Test
    @DisplayName("si el catalogo no responde sale 503, no un resultado inventado")
    void catalogoCaido() throws Exception {
        when(resolverAtaque.ejecutar(any()))
                .thenThrow(new ClienteHeroesException("sin respuesta"));

        mockMvc.perform(post("/api/v1/combate/ataques")
                        .contentType("application/json")
                        .content(CUERPO))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type")
                        .value("https://nexusbattles.local/errores/catalogo-de-heroes-no-disponible"));
    }

    @Test
    @DisplayName("los seis prototipos se publican con sus porcentajes reales")
    void listaDeDistribuciones() throws Exception {
        mockMvc.perform(get("/api/v1/combate/distribuciones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].nombre").value("GUERRERO_TANQUE"))
                // Los numeros salen del dominio, no de una copia en el controlador.
                .andExpect(jsonPath("$[0].distribucion.causarDano").value(40))
                .andExpect(jsonPath("$[0].distribucion.sinEfecto").value(50))
                .andExpect(jsonPath("$[2].nombre").value("MAGO_FUEGO"))
                .andExpect(jsonPath("$[2].distribucion.causarDano").value(70));
    }
}
