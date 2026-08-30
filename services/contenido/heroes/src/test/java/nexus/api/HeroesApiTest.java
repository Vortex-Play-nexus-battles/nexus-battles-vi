package nexus.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HU-HER-001 desde fuera del servicio: el contrato REST que consumen la
 * aplicacion web y los otros equipos (contracts/openapi/heroes.yaml).
 */
@SpringBootTest
@AutoConfigureMockMvc
class HeroesApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("GET /api/v1/heroes responde el catalogo completo")
    void listarCatalogo() throws Exception {
        mvc.perform(get("/api/v1/heroes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[0].nombre").value("Guerrero Tanque"))
                .andExpect(jsonPath("$[0].tipo").value("Guerrero"));
    }

    @Test
    @DisplayName("GET /api/v1/heroes/{nombre} responde la ficha con estadisticas y acciones")
    void fichaDeUnPrototipo() throws Exception {
        mvc.perform(get("/api/v1/heroes/{nombre}", "Chamán"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Chamán"))
                .andExpect(jsonPath("$.esSanador").value(true))
                .andExpect(jsonPath("$.estadisticasNivel1.sanar").value("6 + 1d6"))
                .andExpect(jsonPath("$.estadisticasNivel1.ataque").doesNotExist())
                .andExpect(jsonPath("$.acciones.length()").value(3))
                .andExpect(jsonPath("$.acciones[2].costo").value("6 puntos de poder"));
    }

    @Test
    @DisplayName("la ficha expone la formula estructurada para el motor de combate")
    void formulaEstructuradaParaElMotor() throws Exception {
        // Pedida por el consumidor (motor, HU-JUE-003): las piezas de la
        // formula como datos, para calcular sin parsear el texto de la Tabla 6.
        mvc.perform(get("/api/v1/heroes/{nombre}", "Guerrero Tanque"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadisticasNivel1.ataqueDetalle.base").value(10))
                .andExpect(jsonPath("$.estadisticasNivel1.ataqueDetalle.cantidadDados").value(1))
                .andExpect(jsonPath("$.estadisticasNivel1.ataqueDetalle.caras").value(6))
                .andExpect(jsonPath("$.estadisticasNivel1.danoDetalle.base").value(0))
                .andExpect(jsonPath("$.estadisticasNivel1.danoDetalle.caras").value(4))
                .andExpect(jsonPath("$.estadisticasNivel1.sanarDetalle").doesNotExist());
    }

    @Test
    @DisplayName("el detalle estructurado de un sanador trae sanar y omite ataque y dano")
    void formulaEstructuradaDeSanador() throws Exception {
        mvc.perform(get("/api/v1/heroes/{nombre}", "Chamán"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadisticasNivel1.sanarDetalle.base").value(6))
                .andExpect(jsonPath("$.estadisticasNivel1.sanarDetalle.cantidadDados").value(1))
                .andExpect(jsonPath("$.estadisticasNivel1.sanarDetalle.caras").value(6))
                .andExpect(jsonPath("$.estadisticasNivel1.ataqueDetalle").doesNotExist());
    }

    @Test
    @DisplayName("la busqueda tolera tildes y mayusculas")
    void busquedaToleranteATildes() throws Exception {
        mvc.perform(get("/api/v1/heroes/{nombre}", "picaro veneno"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Pícaro Veneno"));
    }

    @Test
    @DisplayName("un prototipo inexistente responde 404 en formato de detalles de problema")
    void inexistenteRespondeProblemDetail() throws Exception {
        // El campo detail es el mensaje apto para el usuario; la interfaz muestra
        // ese texto, nunca el codigo (regla del cliente, acta 2026-08-13).
        mvc.perform(get("/api/v1/heroes/{nombre}", "Nigromante"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Héroe no disponible"))
                .andExpect(jsonPath("$.detail").value("El héroe solicitado no está disponible en el catálogo."));
    }
}
