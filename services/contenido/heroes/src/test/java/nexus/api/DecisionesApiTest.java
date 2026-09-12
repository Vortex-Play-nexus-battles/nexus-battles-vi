package nexus.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HU-SIM-002: la decision de la IA por turno como servicio sin estado. La
 * simulacion (misiones) manda la estrategia y el estado del heroe al empezar
 * el turno y recibe la accion a ejecutar, por que se descarto cada rotacion
 * anterior y los cursores para el turno siguiente. Contrato en
 * contracts/openapi/heroes.yaml.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DecisionesApiTest {

    private static final String RUTA = "/api/v1/estrategias/decision";

    private static final String EJEMPLO = """
            "heroe":"Guerrero Armas","nivel":8,"rotaciones":[
              {"pasos":["Golpe de tormenta","Embate sangriento","Ataque básico"]},
              {"pasos":["Lanza de los dioses","Ataque básico","Ataque básico"]},
              {"pasos":["Embate sangriento","Ataque básico","Ataque básico"]}]
            """;

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("con poder y sin recargas se ejecuta la Rotacion 1 y se devuelven los cursores del turno siguiente")
    void rotacionUno() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(
                        "{" + EJEMPLO + ",\"estado\":{\"turno\":1,\"poder\":64,\"vida\":44}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accion").value("Golpe de tormenta"))
                .andExpect(jsonPath("$.rotacion").value(1))
                .andExpect(jsonPath("$.costoDePoder").value(6))
                .andExpect(jsonPath("$.cursoresSiguientes[0]").value(1))
                .andExpect(jsonPath("$.evaluaciones[0].viable").value(true))
                .andExpect(jsonPath("$.heroe").value("Guerrero Armas"))
                .andExpect(jsonPath("$.turno").value(1));
    }

    @Test
    @DisplayName("sin poder para la Rotacion 1 se ejecuta la 2 y se explica por que se descarto la 1 (RF-MIS-14)")
    void pasaALaSegunda() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(
                        "{" + EJEMPLO + ",\"estado\":{\"turno\":1,\"poder\":5,\"vida\":44}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accion").value("Lanza de los dioses"))
                .andExpect(jsonPath("$.rotacion").value(2))
                .andExpect(jsonPath("$.evaluaciones[0].viable").value(false))
                .andExpect(jsonPath("$.evaluaciones[0].razon").value(
                        "Poder insuficiente: Golpe de tormenta cuesta 6 y el héroe tiene 5."));
    }

    @Test
    @DisplayName("en recarga la habilidad no es viable: se informa el turno en que vuelve (HU-HER-007)")
    void enRecarga() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(
                        "{" + EJEMPLO + ",\"estado\":{\"turno\":4,\"poder\":64,\"vida\":44,"
                                + "\"turnoDeUltimoUso\":{\"Golpe de tormenta\":3}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accion").value("Lanza de los dioses"))
                .andExpect(jsonPath("$.evaluaciones[0].razon").value(
                        "En recarga: Golpe de tormenta se usó en el turno 3 y vuelve a estar disponible en el turno 5."));
    }

    @Test
    @DisplayName("si ninguna rotacion es viable, ataque basico sin consumir poder (RF-MIS-15)")
    void ataqueBasico() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("""
                {"heroe":"Guerrero Armas","nivel":8,"rotaciones":[{"pasos":["Golpe de tormenta"]},{"pasos":["Lanza de los dioses"]}],
                 "estado":{"turno":1,"poder":0,"vida":44}}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accion").value("Ataque básico"))
                .andExpect(jsonPath("$.rotacion").doesNotExist())
                .andExpect(jsonPath("$.costoDePoder").value(0))
                .andExpect(jsonPath("$.evaluaciones.length()").value(2));
    }

    @Test
    @DisplayName("un heroe sin vida no actua: 400 con mensaje apto para el usuario")
    void sinVida() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(
                        "{" + EJEMPLO + ",\"estado\":{\"turno\":1,\"poder\":64,\"vida\":0}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Solicitud no válida"))
                .andExpect(jsonPath("$.detail").value("Un héroe sin vida no actúa."));
    }

    @Test
    @DisplayName("una rotacion con una habilidad que el heroe no posee responde 400 con el motivo")
    void rotacionInvalida() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("""
                {"heroe":"Guerrero Armas","nivel":1,"rotaciones":[{"pasos":["Golpe de tormenta"]}],
                 "estado":{"turno":1,"poder":8,"vida":44}}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "La rotación 1 usa una habilidad que Guerrero Armas no posee en nivel 1: Golpe de tormenta."));
    }

    @Test
    @DisplayName("un heroe inexistente responde 404 en formato de detalles de problema")
    void heroeInexistente() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("""
                {"heroe":"Paladín","nivel":1,"estado":{"turno":1,"poder":1,"vida":1}}
                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Héroe no disponible"));
    }

    @Test
    @DisplayName("una solicitud sin estado del heroe responde 400 con mensaje apto para el usuario")
    void sinEstado() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(
                        "{" + EJEMPLO + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Solicitud no válida"))
                .andExpect(jsonPath("$.detail").value("La decisión necesita el estado del héroe en el turno."));
    }
}
