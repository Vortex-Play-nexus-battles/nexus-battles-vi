package com.nexusbattles.ms_chatbot.chat.sugerencias;

import com.nexusbattles.ms_chatbot.chat.enriquecido.VistaDelChat;
import com.nexusbattles.ms_chatbot.chat.identidad.IdentidadDelChat;
import com.nexusbattles.ms_chatbot.chat.identidad.ResolutorDeIdentidad;
import com.nexusbattles.ms_chatbot.chat.identidad.SesionAnonima;
import com.nexusbattles.ms_chatbot.chat.identidad.SesionesAnonimas;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import com.nexusbattles.ms_chatbot.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// ms-chatbot.yaml 1.3.3: GET /chat/sugerencias, publica.
// 1.15.0: GET /chat/sugerencias/para-ti, con la identidad del chat.
@WebMvcTest(SugerenciasController.class)
@Import({SecurityConfig.class, ResolutorDeIdentidad.class})
class SugerenciasControllerTest {

    private static final UUID UID = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final String DECLARADA = "visitante-0f8e4c2a-1b3d-4e5f-9a7b-6c5d4e3f2a1b";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SugerenciasService servicio;

    @MockitoBean
    private SugerenciasParaTiService paraTi;

    @MockitoBean
    private SesionesAnonimas sesiones;

    private static TemaConocimiento torneo() {
        return new TemaConocimiento(null, "k-torneo", Categoria.MODALIDAD_JUEGO, TipoRespuesta.DIRECTA,
            "Cómo funciona el Torneo", "torneo", null, "respuesta", null, 0, true);
    }

    @Test
    void sinTokenDevuelveLasSugerencias() throws Exception {
        when(servicio.sugerencias("torn", Categoria.MODALIDAD_JUEGO, 3))
            .thenReturn(List.of(SugerenciaResponse.desde(torneo())));

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
        when(servicio.sugerencias(isNull(), isNull(), isNull())).thenReturn(List.of());

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

    // --- 1.15.0 (7.4.3): sugerencias para ti ----------------------------------

    @Test
    void paraTiConTokenLasPideConSuIdentidadYDiceElMotivo() throws Exception {
        when(paraTi.para(any(), eq(VistaDelChat.SUBASTAS), eq(2))).thenReturn(List.of(
            new SugerenciasParaTiService.SugerenciaParaTi(torneo(), MotivoDeSugerencia.POR_LO_QUE_PREGUNTASTE,
                "Cómo funciona la Subasta")));

        mockMvc.perform(get("/chat/sugerencias/para-ti").param("vista", "SUBASTAS").param("limite", "2")
                .with(jwt().jwt(j -> j.claim("uid", UID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].clave").value("k-torneo"))
            .andExpect(jsonPath("$[0].pregunta").value("Cómo funciona el Torneo"))
            .andExpect(jsonPath("$[0].motivo").value("POR_LO_QUE_PREGUNTASTE"))
            .andExpect(jsonPath("$[0].relacionadoCon").value("Cómo funciona la Subasta"));

        ArgumentCaptor<IdentidadDelChat> identidad = ArgumentCaptor.forClass(IdentidadDelChat.class);
        verify(paraTi).para(identidad.capture(), eq(VistaDelChat.SUBASTAS), eq(2));
        assertThat(identidad.getValue().claveDeConversacion()).isEqualTo(UID.toString());
    }

    @Test
    void paraTiDeUnVisitanteUsaSuSesionYSinNadieNoHayIdentidad() throws Exception {
        SesionAnonima sesion = new SesionAnonima("b".repeat(64), Instant.parse("2026-10-14T10:00:00Z"),
            Duration.ofHours(24));
        when(sesiones.validar(DECLARADA)).thenReturn(Optional.of(sesion));
        when(paraTi.para(any(), isNull(), isNull())).thenReturn(List.of());

        mockMvc.perform(get("/chat/sugerencias/para-ti").header("X-Id-Sesion-Anonima", DECLARADA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/chat/sugerencias/para-ti"))
            .andExpect(status().isOk());

        ArgumentCaptor<IdentidadDelChat> identidad = ArgumentCaptor.forClass(IdentidadDelChat.class);
        verify(paraTi, org.mockito.Mockito.times(2)).para(identidad.capture(), isNull(), isNull());
        assertThat(identidad.getAllValues().get(0).claveDeConversacion()).isEqualTo(sesion.claveDeConversacion());
        assertThat(identidad.getAllValues().get(1)).isNull();
        // Pedir sugerencias nunca registra una sesion.
        verify(sesiones, org.mockito.Mockito.never()).declarar(any(), any());
    }

    @Test
    void paraTiConUnLimiteOUnaVistaFueraDelContratoEs400() throws Exception {
        mockMvc.perform(get("/chat/sugerencias/para-ti").param("limite", "6"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/chat/sugerencias/para-ti").param("vista", "PANEL"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(paraTi);
    }
}
