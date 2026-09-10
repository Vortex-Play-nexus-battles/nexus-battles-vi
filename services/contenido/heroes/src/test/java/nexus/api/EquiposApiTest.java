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
 * HU-JUE-009, criterio 2: "la validacion de composicion... se expone como
 * servicio con su contrato publicado" para que Jugar online y Torneo la
 * consuman. Contrato: contracts/openapi/heroes.yaml.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EquiposApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("un equipo con un solo sanador es valido (RC-08)")
    void unSanadorValido() throws Exception {
        mvc.perform(post("/api/v1/equipos/validacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"heroes\":[\"Chamán\",\"Guerrero Tanque\",\"Mago Fuego\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valida").value(true))
                .andExpect(jsonPath("$.sanadores").value(1))
                .andExpect(jsonPath("$.individual").value(false))
                .andExpect(jsonPath("$.motivo").doesNotExist());
    }

    @Test
    @DisplayName("dos sanadores en un equipo se rechazan con el motivo apto para el jugador (ERS CU-44 E4)")
    void dosSanadoresRechazados() throws Exception {
        mvc.perform(post("/api/v1/equipos/validacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"heroes\":[\"chaman\",\"MÉDICO\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valida").value(false))
                .andExpect(jsonPath("$.sanadores").value(2))
                .andExpect(jsonPath("$.motivo").value("Un equipo admite un único sanador: Chamán o Médico."));
    }

    @Test
    @DisplayName("el individual con sanador sigue el parametro propio; por defecto rige RC-09 (solo en equipo)")
    void individualConSanadorSegunParametro() throws Exception {
        mvc.perform(post("/api/v1/equipos/validacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"heroes\":[\"Médico\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valida").value(false))
                .andExpect(jsonPath("$.individual").value(true))
                .andExpect(jsonPath("$.motivo").value("Los sanadores solo participan en combate por equipos."));
    }

    @Test
    @DisplayName("un heroe inexistente en la composicion responde 404 en formato de detalles de problema")
    void heroeInexistente() throws Exception {
        mvc.perform(post("/api/v1/equipos/validacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"heroes\":[\"Paladín\"]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Héroe no disponible"));
    }

    @Test
    @DisplayName("un equipo vacio responde 400 con mensaje apto para el usuario")
    void equipoVacio() throws Exception {
        mvc.perform(post("/api/v1/equipos/validacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"heroes\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Solicitud no válida"))
                .andExpect(jsonPath("$.detail").value("Un equipo tiene al menos un héroe."));
    }

    @Test
    @DisplayName("una solicitud sin el campo heroes responde 400 con mensaje apto para el usuario")
    void sinCampoHeroes() throws Exception {
        mvc.perform(post("/api/v1/equipos/validacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Solicitud no válida"))
                .andExpect(jsonPath("$.detail").value("Un equipo tiene al menos un héroe."));
    }
}
