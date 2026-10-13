package com.nexusbattles.ms_chatbot.chat.sugerencias;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import com.nexusbattles.ms_chatbot.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// ms-chatbot.yaml 1.3.3: GET /chat/sugerencias, publica.
@WebMvcTest(SugerenciasController.class)
@Import(SecurityConfig.class)
class SugerenciasControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SugerenciasService servicio;

    private static TemaConocimiento torneo() {
        return new TemaConocimiento(null, "k-torneo", Categoria.MODALIDAD_JUEGO, TipoRespuesta.DIRECTA,
            "Cómo funciona el Torneo", "torneo", null, "respuesta", null, 0, true);
    }

    @Test
    void sinTokenDevuelveLasSugerencias() throws Exception {
        when(servicio.sugerir("torn", Categoria.MODALIDAD_JUEGO, 3)).thenReturn(List.of(torneo()));

        mockMvc.perform(get("/chat/sugerencias")
                .param("q", "torn")
                .param("categoria", "MODALIDAD_JUEGO")
                .param("limite", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].clave").value("k-torneo"))
            .andExpect(jsonPath("$[0].titulo").value("Cómo funciona el Torneo"))
            .andExpect(jsonPath("$[0].categoria").value("MODALIDAD_JUEGO"))
            .andExpect(jsonPath("$[0].pregunta").value("Cómo funciona el Torneo"));
    }

    @Test
    void sinParametrosUsaLosValoresPorOmision() throws Exception {
        when(servicio.sugerir(isNull(), isNull(), isNull())).thenReturn(List.of());

        mockMvc.perform(get("/chat/sugerencias"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void unLimiteFueraDeRangoOUnaCategoriaInexistenteSon400() throws Exception {
        mockMvc.perform(get("/chat/sugerencias").param("limite", "50"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/chat/sugerencias").param("categoria", "NO_EXISTE"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/chat/sugerencias").param("q", "x".repeat(101)))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(servicio);
    }
}
