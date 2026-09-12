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
 * HU-SIM-001: la configuracion de rotaciones se valida como servicio, para que
 * el configurador de estrategia (RF-MIS-38) y el modulo de misiones, que es
 * quien la guarda (RNF-16), no reimplementen la regla. Contrato en
 * contracts/openapi/heroes.yaml.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EstrategiasApiTest {

    private static final String RUTA = "/api/v1/estrategias/validacion";

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("el ejemplo del documento (seccion 7.8.5) se acepta con prioridades alta, media y baja")
    void ejemploDelDocumento() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("""
                {"heroe":"Guerrero Armas","nivel":8,"rotaciones":[
                  {"pasos":["Golpe de tormenta","Embate sangriento","Ataque básico"]},
                  {"pasos":["Lanza de los dioses","Ataque básico","Ataque básico"]},
                  {"pasos":["Embate sangriento","Ataque básico","Ataque básico"]}]}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valida").value(true))
                .andExpect(jsonPath("$.heroe").value("Guerrero Armas"))
                .andExpect(jsonPath("$.nivel").value(8))
                .andExpect(jsonPath("$.porDefecto").value(false))
                .andExpect(jsonPath("$.rotaciones.length()").value(3))
                .andExpect(jsonPath("$.rotaciones[0].prioridad").value("Alta"))
                .andExpect(jsonPath("$.rotaciones[1].prioridad").value("Media"))
                .andExpect(jsonPath("$.rotaciones[2].prioridad").value("Baja"))
                .andExpect(jsonPath("$.rotaciones[0].pasos[0]").value("Golpe de tormenta"))
                .andExpect(jsonPath("$.motivo").doesNotExist());
    }

    @Test
    @DisplayName("una habilidad que el heroe no posee en su nivel se rechaza indicando las validas (ERS CU-61 E1)")
    void habilidadNoPoseida() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("""
                {"heroe":"guerrero armas","nivel":4,"rotaciones":[{"pasos":["Golpe de tormenta"]}]}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valida").value(false))
                .andExpect(jsonPath("$.motivo").value(
                        "La rotación 1 usa una habilidad que Guerrero Armas no posee en nivel 4: Golpe de tormenta."))
                .andExpect(jsonPath("$.habilidadesValidas[0]").value("Embate sangriento"))
                .andExpect(jsonPath("$.habilidadesValidas[1]").value("Lanza de los dioses"))
                .andExpect(jsonPath("$.habilidadesValidas[2]").value("Ataque básico"))
                .andExpect(jsonPath("$.rotaciones").doesNotExist());
    }

    @Test
    @DisplayName("sin rotaciones la estrategia es la por defecto: ataque basico (RF-MIS-15)")
    void sinRotaciones() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("""
                {"heroe":"Pícaro Veneno","nivel":3}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valida").value(true))
                .andExpect(jsonPath("$.porDefecto").value(true))
                .andExpect(jsonPath("$.comportamientoPorDefecto").value("Ataque básico"))
                .andExpect(jsonPath("$.rotaciones.length()").value(0));
    }

    @Test
    @DisplayName("sin nivel se asume el 1: solo la primera accion y el ataque basico")
    void nivelPorDefecto() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("""
                {"heroe":"Guerrero Tanque","rotaciones":[{"pasos":["Golpe con escudo","Ataque básico"]}]}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valida").value(true))
                .andExpect(jsonPath("$.nivel").value(1))
                .andExpect(jsonPath("$.habilidadesValidas.length()").value(2));
    }

    @Test
    @DisplayName("un heroe inexistente responde 404 en formato de detalles de problema")
    void heroeInexistente() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("""
                {"heroe":"Paladín","nivel":1,"rotaciones":[]}
                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Héroe no disponible"));
    }

    @Test
    @DisplayName("un nivel fuera de 1..8 responde 400 con mensaje apto para el usuario")
    void nivelFueraDeRango() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("""
                {"heroe":"Chamán","nivel":9,"rotaciones":[]}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Nivel no válido"));
    }
}
