package com.nexusbattles.ms_chatbot.chat.api;

import com.nexusbattles.ms_chatbot.chat.identidad.IdentidadDelChat;
import com.nexusbattles.ms_chatbot.chat.identidad.ResolutorDeIdentidad;
import com.nexusbattles.ms_chatbot.chat.identidad.SesionAnonima;
import com.nexusbattles.ms_chatbot.chat.identidad.SesionesAnonimas;
import com.nexusbattles.ms_chatbot.chat.preferencias.IdiomaPreferido;
import com.nexusbattles.ms_chatbot.chat.preferencias.NivelDeDetalle;
import com.nexusbattles.ms_chatbot.chat.preferencias.PreferenciasDeRespuesta;
import com.nexusbattles.ms_chatbot.chat.preferencias.PreferenciasService;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// ms-chatbot.yaml 1.3.6: GET y PUT /chat/preferencias, con la identidad de B11.
@WebMvcTest(PreferenciasController.class)
@Import({SecurityConfig.class, ResolutorDeIdentidad.class, ManejadorErroresDelChat.class})
class PreferenciasControllerTest {

    private static final String CABECERA = "X-Id-Sesion-Anonima";
    private static final UUID UID = UUID.fromString("11111111-2222-4333-8444-555555555555");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PreferenciasService servicio;

    @MockitoBean
    private SesionesAnonimas sesiones;

    private static SesionAnonima sesion() {
        return new SesionAnonima("huella", Instant.parse("2026-10-01T10:00:00Z"), Duration.ofHours(24));
    }

    // Un uid en la cabecera no es una sesion: no abre las preferencias de nadie.
    @Test
    void obtener_sinIdentidadValida_devuelveLasDePorDefectoSinConsultarNada() throws Exception {
        when(sesiones.validar(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/chat/preferencias").header(CABECERA, UID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idioma").value("AUTOMATICO"))
            .andExpect(jsonPath("$.nivelDetalle").value("NORMAL"));

        verifyNoInteractions(servicio);
    }

    @Test
    void obtener_conToken_devuelveLasDelJugador() throws Exception {
        when(servicio.de(any())).thenReturn(new PreferenciasDeRespuesta(IdiomaPreferido.EN, NivelDeDetalle.BREVE));

        mockMvc.perform(get("/chat/preferencias").with(jwt().jwt(j -> j.claim("uid", UID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idioma").value("EN"))
            .andExpect(jsonPath("$.nivelDetalle").value("BREVE"));

        ArgumentCaptor<IdentidadDelChat> identidad = ArgumentCaptor.forClass(IdentidadDelChat.class);
        verify(servicio).de(identidad.capture());
        assertThat(identidad.getValue().claveDeConversacion()).isEqualTo(UID.toString());
    }

    @Test
    void guardar_conToken_guardaParaElJugadorYDevuelveLoGuardado() throws Exception {
        PreferenciasDeRespuesta nuevas = new PreferenciasDeRespuesta(IdiomaPreferido.ES, NivelDeDetalle.DETALLADO);
        when(servicio.guardar(any(), eq(nuevas))).thenReturn(nuevas);

        mockMvc.perform(put("/chat/preferencias").with(jwt().jwt(j -> j.claim("uid", UID.toString())))
                .contentType("application/json")
                .content("{\"idioma\":\"ES\",\"nivelDetalle\":\"DETALLADO\"}"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(CABECERA))
            .andExpect(jsonPath("$.idioma").value("ES"))
            .andExpect(jsonPath("$.nivelDetalle").value("DETALLADO"));

        ArgumentCaptor<IdentidadDelChat> identidad = ArgumentCaptor.forClass(IdentidadDelChat.class);
        verify(servicio).guardar(identidad.capture(), eq(nuevas));
        assertThat(identidad.getValue().claveDeConversacion()).isEqualTo(UID.toString());
    }

    // Como el primer mensaje: un visitante sin sesion recibe una.
    @Test
    void guardar_visitanteSinSesion_recibeUnaSesionEmitida() throws Exception {
        String emitida = "anon_" + "E".repeat(43);
        when(sesiones.emitir(anyString())).thenReturn(new SesionesAnonimas.Emitida(emitida, sesion()));
        PreferenciasDeRespuesta nuevas = new PreferenciasDeRespuesta(IdiomaPreferido.AUTOMATICO, NivelDeDetalle.BREVE);
        when(servicio.guardar(any(), eq(nuevas))).thenReturn(nuevas);

        mockMvc.perform(put("/chat/preferencias")
                .contentType("application/json")
                .content("{\"idioma\":\"AUTOMATICO\",\"nivelDetalle\":\"BREVE\"}"))
            .andExpect(status().isOk())
            .andExpect(header().string(CABECERA, emitida))
            .andExpect(jsonPath("$.nivelDetalle").value("BREVE"));
    }

    @Test
    void guardar_conValoresInvalidosOIncompletos_responde400() throws Exception {
        mockMvc.perform(put("/chat/preferencias").with(jwt().jwt(j -> j.claim("uid", UID.toString())))
                .contentType("application/json")
                .content("{\"idioma\":\"FR\",\"nivelDetalle\":\"BREVE\"}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/chat/preferencias").with(jwt().jwt(j -> j.claim("uid", UID.toString())))
                .contentType("application/json")
                .content("{\"idioma\":\"ES\"}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(servicio);
    }
}
