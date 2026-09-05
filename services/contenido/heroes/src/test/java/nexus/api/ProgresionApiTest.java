package nexus.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Las reglas de progresion (HU-HER-003/004/006/007/008/009/010) expuestas como
 * servicio, para que motor, misiones e inventario las consulten en vez de
 * reimplementarlas. Contrato: contracts/openapi/heroes.yaml.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProgresionApiTest {

    @Autowired
    private MockMvc mvc;

    // --- GET /api/v1/heroes/{nombre}/niveles/{nivel} ------------------------

    @Test
    @DisplayName("la vista por nivel trae las estadisticas escaladas del ejemplo del cliente")
    void vistaPorNivelEscalaLasEstadisticas() throws Exception {
        // Ejemplo textual del documento: mago de fuego nivel 3, ataque base 30
        mvc.perform(get("/api/v1/heroes/{nombre}/niveles/{nivel}", "Mago Fuego", 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Mago Fuego"))
                .andExpect(jsonPath("$.nivel").value(3))
                .andExpect(jsonPath("$.estadisticas.ataque").value("30 + 1d8"))
                .andExpect(jsonPath("$.estadisticas.ataqueDetalle.base").value(30))
                .andExpect(jsonPath("$.estadisticas.ataqueDetalle.caras").value(8))
                .andExpect(jsonPath("$.estadisticas.vida").value(120))
                .andExpect(jsonPath("$.multiplicadorDeEfecto").value(3))
                .andExpect(jsonPath("$.experienciaParaSubir").value(144.0));
    }

    @Test
    @DisplayName("las acciones disponibles siguen el desbloqueo 1/4/8 dictado en clase (RC-01)")
    void accionesDesbloqueadasPorNivel() throws Exception {
        mvc.perform(get("/api/v1/heroes/{nombre}/niveles/{nivel}", "Guerrero Tanque", 3))
                .andExpect(jsonPath("$.accionesDisponibles.length()").value(1));
        mvc.perform(get("/api/v1/heroes/{nombre}/niveles/{nivel}", "Guerrero Tanque", 4))
                .andExpect(jsonPath("$.accionesDisponibles.length()").value(2));
        mvc.perform(get("/api/v1/heroes/{nombre}/niveles/{nivel}", "Guerrero Tanque", 8))
                .andExpect(jsonPath("$.accionesDisponibles.length()").value(3))
                .andExpect(jsonPath("$.accionesDisponibles[0].nombre").value("Golpe con escudo"))
                .andExpect(jsonPath("$.experienciaParaSubir").doesNotExist());
    }

    @Test
    @DisplayName("la vista incluye la epica afin con su efecto potenciado (Tabla 20)")
    void epicaAfinConEfectoPotenciado() throws Exception {
        mvc.perform(get("/api/v1/heroes/{nombre}/niveles/{nivel}", "Mago Fuego", 1))
                .andExpect(jsonPath("$.epica.nombre").value("Luz cegadora"))
                .andExpect(jsonPath("$.epica.efectoGeneral").value("+1 a la vida"))
                .andExpect(jsonPath("$.epica.efectoPotenciado").value("+2 al daño, +1% de crítico"))
                .andExpect(jsonPath("$.epica.turnosDeRecarga").value(2));
    }

    @Test
    @DisplayName("un sanador no trae efecto general de epica (la Tabla 20 dice No aplica)")
    void epicaDeSanadorSinEfectoGeneral() throws Exception {
        mvc.perform(get("/api/v1/heroes/{nombre}/niveles/{nivel}", "Chamán", 2))
                .andExpect(jsonPath("$.epica.nombre").value("Té changua"))
                .andExpect(jsonPath("$.epica.efectoGeneral").doesNotExist())
                .andExpect(jsonPath("$.estadisticas.sanar").value("12 + 1d6"));
    }

    @Test
    @DisplayName("un nivel fuera de 1..8 responde 400 con mensaje apto para el usuario")
    void nivelFueraDeRango() throws Exception {
        mvc.perform(get("/api/v1/heroes/{nombre}/niveles/{nivel}", "Mago Fuego", 9))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Nivel no válido"))
                .andExpect(jsonPath("$.detail").value("El nivel de un héroe está entre 1 y 8."));
    }

    @Test
    @DisplayName("un prototipo inexistente en la vista por nivel responde 404")
    void prototipoInexistenteEnVistaPorNivel() throws Exception {
        mvc.perform(get("/api/v1/heroes/{nombre}/niveles/{nivel}", "Nigromante", 2))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Héroe no disponible"));
    }

    // --- Progresion sin prototipo -------------------------------------------

    @Test
    @DisplayName("la tabla de niveles trae los ocho niveles con la formula 100 x 1,2^(N-1)")
    void tablaDeNiveles() throws Exception {
        mvc.perform(get("/api/v1/progresion/niveles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[0].nivel").value(1))
                .andExpect(jsonPath("$[0].experienciaParaSubir").value(100.0))
                .andExpect(jsonPath("$[1].experienciaParaSubir").value(120.0))
                .andExpect(jsonPath("$[7].nivel").value(8))
                .andExpect(jsonPath("$[7].experienciaParaSubir").doesNotExist());
    }

    @Test
    @DisplayName("progresar aplica puntos con sobrante y encadena niveles")
    void progresarEncadenaNiveles() throws Exception {
        mvc.perform(post("/api/v1/progresion/experiencia")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nivel\":1,\"experiencia\":0,\"puntos\":250}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nivel").value(3))
                .andExpect(jsonPath("$.experiencia").value(30.0));
    }

    @Test
    @DisplayName("progresar rechaza puntos negativos con 400")
    void progresarRechazaNegativos() throws Exception {
        mvc.perform(post("/api/v1/progresion/experiencia")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nivel\":1,\"experiencia\":0,\"puntos\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La experiencia ganada no puede ser negativa."));
    }

    @Test
    @DisplayName("la experiencia por enemigo derrotado sigue 10 x 1,2^(dado)")
    void experienciaPorEnemigo() throws Exception {
        mvc.perform(get("/api/v1/progresion/experiencia-por-enemigo/{dado}", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dado").value(1))
                .andExpect(jsonPath("$.puntos").value(12.0));
        mvc.perform(get("/api/v1/progresion/experiencia-por-enemigo/{dado}", 9))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El resultado de un 1d8 está entre 1 y 8."));
    }
}
