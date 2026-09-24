package com.nexusbattles.ms_chatbot.chat.conocimiento;

import com.nexusbattles.ms_chatbot.chat.conocimiento.ResultadoEvaluacion.FalloDeCaso;
import com.nexusbattles.ms_chatbot.chat.motor.model.CasoEvaluacion;
import com.nexusbattles.ms_chatbot.chat.motor.model.VersionBaseConocimiento;
import com.nexusbattles.ms_chatbot.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EvaluacionBaseConocimientoController.class)
@Import(SecurityConfig.class)
class EvaluacionBaseConocimientoControllerTest {

    private static final String RUTA = "/chatbot/admin/base-conocimiento";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EvaluacionBaseConocimientoService evaluacionService;
    @MockitoBean
    private CasoEvaluacionService casoEvaluacionService;

    // ------------------------------------------------ evaluar / desplegar

    @Test
    void evaluar_devuelveLaComparacion() throws Exception {
        when(evaluacionService.evaluarCandidata()).thenReturn(comparacionPeor());

        mockMvc.perform(post(RUTA + "/borrador/evaluacion").with(administrador()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.candidataApta").value(false))
            .andExpect(jsonPath("$.candidata.aciertos").value(2))
            .andExpect(jsonPath("$.candidata.fallos[0].pregunta").value("como cambiar contrasena"))
            .andExpect(jsonPath("$.produccion.aciertos").value(3));
    }

    @Test
    void desplegar_candidataApta_devuelveLaVersionEnProduccion() throws Exception {
        VersionBaseConocimiento desplegada = VersionBaseConocimiento.nuevaCandidata(2, "Temas de torneos");
        desplegada.ponerEnProduccion(Instant.parse("2026-09-23T20:00:00Z"));
        when(evaluacionService.desplegarCandidata()).thenReturn(desplegada);

        mockMvc.perform(post(RUTA + "/borrador/despliegue").with(administrador()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.numero").value(2))
            .andExpect(jsonPath("$.estado").value("PRODUCCION"));
    }

    // Paso 9 visto desde la API: el rechazo sale como 409 problem details con
    // los dos resultados, para que el panel muestre que casos fallaron.
    @Test
    void desplegar_candidataQueRindePeor_devuelve409ConLosResultados() throws Exception {
        when(evaluacionService.desplegarCandidata()).thenThrow(new DespliegueRechazadoException(comparacionPeor()));

        mockMvc.perform(post(RUTA + "/borrador/despliegue").with(administrador()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title").value("Candidata con peor desempeno"))
            .andExpect(jsonPath("$.candidata.aciertos").value(2))
            .andExpect(jsonPath("$.produccion.aciertos").value(3))
            .andExpect(jsonPath("$.candidata.fallos[0].temaClaveEsperada").value("clave-contrasena"));
    }

    @Test
    void desplegar_comoJugador_devuelve403() throws Exception {
        mockMvc.perform(post(RUTA + "/borrador/despliegue")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_JUGADOR"))))
            .andExpect(status().isForbidden());

        verifyNoInteractions(evaluacionService);
    }

    @Test
    void desplegar_sinToken_devuelve401() throws Exception {
        mockMvc.perform(post(RUTA + "/borrador/despliegue"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(evaluacionService);
    }

    @Test
    void revertir_devuelveLaVersionRestaurada() throws Exception {
        VersionBaseConocimiento restaurada = VersionBaseConocimiento.nuevaCandidata(1, null);
        restaurada.ponerEnProduccion(Instant.parse("2026-09-23T20:00:00Z"));
        when(evaluacionService.revertir()).thenReturn(restaurada);

        mockMvc.perform(post(RUTA + "/produccion/reversion")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMINISTRADOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.numero").value(1))
            .andExpect(jsonPath("$.estado").value("PRODUCCION"));
    }

    @Test
    void revertir_sinVersionAnterior_devuelve409() throws Exception {
        when(evaluacionService.revertir())
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "No hay una version anterior a la cual revertir."));

        mockMvc.perform(post(RUTA + "/produccion/reversion").with(administrador()))
            .andExpect(status().isConflict());
    }

    // --------------------------------------------------- casos de evaluacion

    @Test
    void listarCasos_devuelveLosCasos() throws Exception {
        when(casoEvaluacionService.listar()).thenReturn(List.of(
            CasoEvaluacion.nuevo("como pujo", "clave-pujas"),
            CasoEvaluacion.nuevo("como hackeo el juego", null)));

        mockMvc.perform(get(RUTA + "/casos-evaluacion").with(administrador()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].temaClaveEsperada").value("clave-pujas"))
            .andExpect(jsonPath("$[1].temaClaveEsperada").doesNotExist())
            .andExpect(jsonPath("$[1].activo").value(true));
    }

    @Test
    void crearCaso_devuelve201() throws Exception {
        when(casoEvaluacionService.crear(any())).thenReturn(CasoEvaluacion.nuevo("como pujo", "clave-pujas"));

        mockMvc.perform(post(RUTA + "/casos-evaluacion").with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pregunta\": \"como pujo\", \"temaClaveEsperada\": \"clave-pujas\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pregunta").value("como pujo"));
    }

    @Test
    void crearCaso_sinPregunta_devuelve400() throws Exception {
        mockMvc.perform(post(RUTA + "/casos-evaluacion").with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pregunta\": \"  \"}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(casoEvaluacionService);
    }

    @Test
    void editarCaso_devuelveElCasoEditado() throws Exception {
        UUID casoId = UUID.randomUUID();
        when(casoEvaluacionService.editar(eq(casoId), any()))
            .thenReturn(CasoEvaluacion.nuevo("como hago una puja", "clave-pujas"));

        mockMvc.perform(put(RUTA + "/casos-evaluacion/" + casoId).with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pregunta\": \"como hago una puja\", \"temaClaveEsperada\": \"clave-pujas\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pregunta").value("como hago una puja"));
    }

    @Test
    void eliminarCaso_devuelve204() throws Exception {
        UUID casoId = UUID.randomUUID();

        mockMvc.perform(delete(RUTA + "/casos-evaluacion/" + casoId).with(administrador()))
            .andExpect(status().isNoContent());

        verify(casoEvaluacionService).eliminar(casoId);
    }

    // -------------------------------------------------------------- ayudas

    private static RequestPostProcessor administrador() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"));
    }

    private static ComparacionEvaluacion comparacionPeor() {
        ResultadoEvaluacion candidata = new ResultadoEvaluacion(UUID.randomUUID(), 2, 3, 2, 2.0 / 3,
            List.of(new FalloDeCaso(UUID.randomUUID(), "como cambiar contrasena", "clave-contrasena", null)));
        ResultadoEvaluacion produccion = new ResultadoEvaluacion(UUID.randomUUID(), 1, 3, 3, 1.0, List.of());
        return ComparacionEvaluacion.de(candidata, produccion);
    }
}
