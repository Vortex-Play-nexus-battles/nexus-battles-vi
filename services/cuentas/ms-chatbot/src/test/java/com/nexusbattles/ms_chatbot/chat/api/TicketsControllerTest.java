package com.nexusbattles.ms_chatbot.chat.api;

import com.nexusbattles.ms_chatbot.chat.enriquecido.VistaDelChat;
import com.nexusbattles.ms_chatbot.chat.identidad.IdentidadDelChat;
import com.nexusbattles.ms_chatbot.chat.identidad.ResolutorDeIdentidad;
import com.nexusbattles.ms_chatbot.chat.identidad.SesionAnonima;
import com.nexusbattles.ms_chatbot.chat.identidad.SesionesAnonimas;
import com.nexusbattles.ms_chatbot.chat.limite.LimiteDeFrecuenciaExcedido;
import com.nexusbattles.ms_chatbot.chat.moderacion.ContenidoBloqueado;
import com.nexusbattles.ms_chatbot.chat.moderacion.ModeracionNoDisponible;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.soporte.SesionRequeridaException;
import com.nexusbattles.ms_chatbot.chat.soporte.SinConversacionException;
import com.nexusbattles.ms_chatbot.chat.soporte.TicketAbiertoException;
import com.nexusbattles.ms_chatbot.chat.soporte.TicketSoporte;
import com.nexusbattles.ms_chatbot.chat.soporte.TicketSoporteService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// ms-chatbot.yaml 1.3.0: POST y GET /chat/tickets, con los errores del contrato.
@WebMvcTest(TicketsController.class)
@Import({SecurityConfig.class, ResolutorDeIdentidad.class, ManejadorErroresDelChat.class})
class TicketsControllerTest {

    private static final UUID UID = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final Instant AHORA = Instant.parse("2026-09-28T18:00:00Z");
    private static final String CUERPO =
        "{\"categoria\":\"SOPORTE_TECNICO\",\"asunto\":\"No carga\",\"mensaje\":\"Se queda cargando\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketSoporteService servicio;

    @MockitoBean
    private SesionesAnonimas sesiones;

    private static TicketSoporte ticket() {
        return TicketSoporte.abrir(UID.toString(), Categoria.SOPORTE_TECNICO, "No carga", "Se queda cargando",
            List.of(), AHORA);
    }

    @Test
    void abrirConTokenDevuelve201YLoQueVeElJugador() throws Exception {
        when(servicio.abrir(any(), eq(Categoria.SOPORTE_TECNICO), eq("No carga"), eq("Se queda cargando")))
            .thenReturn(ticket());

        mockMvc.perform(post("/chat/tickets")
                .with(jwt().jwt(j -> j.claim("uid", UID.toString())))
                .contentType("application/json")
                .content(CUERPO))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.estado").value("ABIERTO"))
            .andExpect(jsonPath("$.asunto").value("No carga"))
            .andExpect(jsonPath("$.categoria").value("SOPORTE_TECNICO"))
            .andExpect(jsonPath("$.uid").doesNotExist())
            .andExpect(jsonPath("$.contexto").doesNotExist());

        ArgumentCaptor<IdentidadDelChat> identidad = ArgumentCaptor.forClass(IdentidadDelChat.class);
        verify(servicio).abrir(identidad.capture(), any(), anyString(), anyString());
        assertThat(identidad.getValue().autenticado()).isTrue();
        assertThat(identidad.getValue().uid()).isEqualTo(UID.toString());
    }

    @Test
    void sinTokenElServicioRecibeNadieYResponde401ConMotivo() throws Exception {
        when(servicio.abrir(isNull(), any(), anyString(), anyString())).thenThrow(new SesionRequeridaException());

        mockMvc.perform(post("/chat/tickets")
                .header("X-Id-Sesion-Anonima", "anon_" + "A".repeat(43))
                .contentType("application/json")
                .content(CUERPO))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.motivo").value("SESION_REQUERIDA"));
    }

    @Test
    void unCuerpoInvalidoEs400() throws Exception {
        mockMvc.perform(post("/chat/tickets")
                .with(jwt().jwt(j -> j.claim("uid", UID.toString())))
                .contentType("application/json")
                .content("{\"categoria\":\"SOPORTE_TECNICO\",\"asunto\":\"  \",\"mensaje\":\"x\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(servicio);
    }

    @Test
    void conUnTicketAbiertoEs409() throws Exception {
        when(servicio.abrir(any(), any(), anyString(), anyString())).thenThrow(new TicketAbiertoException());

        mockMvc.perform(post("/chat/tickets")
                .with(jwt().jwt(j -> j.claim("uid", UID.toString())))
                .contentType("application/json")
                .content(CUERPO))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.motivo").value("TICKET_ABIERTO"));
    }

    @Test
    void losErroresDelChatTambienAplicanALosTickets() throws Exception {
        when(servicio.abrir(any(), any(), anyString(), anyString()))
            .thenThrow(new LimiteDeFrecuenciaExcedido("espera", 12))
            .thenThrow(new ContenidoBloqueado())
            .thenThrow(new ModeracionNoDisponible());

        mockMvc.perform(post("/chat/tickets").with(jwt().jwt(j -> j.claim("uid", UID.toString())))
                .contentType("application/json").content(CUERPO))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "12"))
            .andExpect(jsonPath("$.motivo").value("LIMITE_DE_FRECUENCIA"));
        mockMvc.perform(post("/chat/tickets").with(jwt().jwt(j -> j.claim("uid", UID.toString())))
                .contentType("application/json").content(CUERPO))
            .andExpect(status().is(422))
            .andExpect(jsonPath("$.motivo").value("CONTENIDO_BLOQUEADO"));
        mockMvc.perform(post("/chat/tickets").with(jwt().jwt(j -> j.claim("uid", UID.toString())))
                .contentType("application/json").content(CUERPO))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.motivo").value("MODERACION_NO_DISPONIBLE"));
    }

    @Test
    void misTicketsConToken() throws Exception {
        when(servicio.misTickets(any())).thenReturn(List.of(ticket()));

        mockMvc.perform(get("/chat/tickets").with(jwt().jwt(j -> j.claim("uid", UID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].asunto").value("No carga"))
            .andExpect(jsonPath("$[0].estado").value("ABIERTO"));
    }

    @Test
    void misTicketsSinTokenEs401() throws Exception {
        when(servicio.misTickets(isNull())).thenThrow(new SesionRequeridaException());

        mockMvc.perform(get("/chat/tickets"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.motivo").value("SESION_REQUERIDA"));
    }

    // --- ms-chatbot.yaml 1.12.0: solicitudes de un visitante ------------------

    private static final String DECLARADA = "visitante-0f8e4c2a-1b3d-4e5f-9a7b-6c5d4e3f2a1b";

    private static SesionAnonima sesionRegistrada() {
        return new SesionAnonima("b".repeat(64), AHORA, Duration.ofHours(24));
    }

    @Test
    void unVisitanteAbreLaSuyaConLaSesionQueDeclaraSuNavegador() throws Exception {
        SesionAnonima sesion = sesionRegistrada();
        when(sesiones.declarar(eq(DECLARADA), any())).thenReturn(sesion);
        when(servicio.abrir(any(), any(), anyString(), anyString())).thenReturn(TicketSoporte.abrir(
            sesion.claveDeConversacion(), Categoria.SOPORTE_TECNICO, "No carga", "Se queda cargando", List.of(), AHORA));

        mockMvc.perform(post("/chat/tickets")
                .header("X-Id-Sesion-Anonima", DECLARADA)
                .contentType("application/json")
                .content(CUERPO))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.asunto").value("No carga"))
            .andExpect(jsonPath("$.uid").doesNotExist());

        ArgumentCaptor<IdentidadDelChat> identidad = ArgumentCaptor.forClass(IdentidadDelChat.class);
        verify(servicio).abrir(identidad.capture(), any(), anyString(), anyString());
        assertThat(identidad.getValue().autenticado()).isFalse();
        assertThat(identidad.getValue().claveDeConversacion()).isEqualTo(sesion.claveDeConversacion());
    }

    @Test
    void conTokenLaCabeceraDelVisitanteNoCuentaNiSeRegistra() throws Exception {
        when(servicio.abrirDesdeConversacion(any(), any(), any())).thenReturn(ticket());

        mockMvc.perform(post("/chat/tickets/desde-conversacion")
                .with(jwt().jwt(j -> j.claim("uid", UID.toString())))
                .header("X-Id-Sesion-Anonima", DECLARADA))
            .andExpect(status().isCreated());

        verify(sesiones, never()).declarar(anyString(), any());
        ArgumentCaptor<IdentidadDelChat> identidad = ArgumentCaptor.forClass(IdentidadDelChat.class);
        verify(servicio).abrirDesdeConversacion(identidad.capture(), any(), any());
        assertThat(identidad.getValue().uid()).isEqualTo(UID.toString());
    }

    @Test
    void unVisitanteConSesionVigenteVeLasSuyas() throws Exception {
        SesionAnonima sesion = sesionRegistrada();
        when(sesiones.validar(DECLARADA)).thenReturn(Optional.of(sesion));
        when(servicio.misTickets(any())).thenReturn(List.of(TicketSoporte.abrir(sesion.claveDeConversacion(),
            Categoria.SOPORTE_TECNICO, "No carga", "Se queda cargando", List.of(), AHORA)));

        mockMvc.perform(get("/chat/tickets").header("X-Id-Sesion-Anonima", DECLARADA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].asunto").value("No carga"))
            .andExpect(jsonPath("$[0].uid").doesNotExist());

        ArgumentCaptor<IdentidadDelChat> identidad = ArgumentCaptor.forClass(IdentidadDelChat.class);
        verify(servicio).misTickets(identidad.capture());
        assertThat(identidad.getValue().claveDeConversacion()).isEqualTo(sesion.claveDeConversacion());
    }

    @Test
    void unaSesionConFormaSinRegistrarOVencidaNoTieneSolicitudes() throws Exception {
        mockMvc.perform(get("/chat/tickets").header("X-Id-Sesion-Anonima", DECLARADA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/chat/tickets").header("X-Id-Sesion-Anonima", "anon_" + "A".repeat(43)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
        verifyNoInteractions(servicio);
    }

    @Test
    void unaCabeceraSinFormaDeSesionEs401() throws Exception {
        when(servicio.misTickets(isNull())).thenThrow(new SesionRequeridaException());

        mockMvc.perform(get("/chat/tickets").header("X-Id-Sesion-Anonima", UID.toString()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.motivo").value("SESION_REQUERIDA"));
    }

    // --- ms-chatbot.yaml 1.8.0: POST /chat/tickets/desde-conversacion ------

    @Test
    void desdeLaConversacionConTokenDevuelve201YLaIdentidadEsLaDelToken() throws Exception {
        when(servicio.abrirDesdeConversacion(any(), eq(VistaDelChat.SUBASTAS), eq("Urgente"))).thenReturn(ticket());

        mockMvc.perform(post("/chat/tickets/desde-conversacion")
                .with(jwt().jwt(j -> j.claim("uid", UID.toString())))
                .contentType("application/json")
                .content("{\"vista\":\"SUBASTAS\",\"comentario\":\"Urgente\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.estado").value("ABIERTO"))
            .andExpect(jsonPath("$.uid").doesNotExist())
            .andExpect(jsonPath("$.contexto").doesNotExist());

        ArgumentCaptor<IdentidadDelChat> identidad = ArgumentCaptor.forClass(IdentidadDelChat.class);
        verify(servicio).abrirDesdeConversacion(identidad.capture(), any(), any());
        assertThat(identidad.getValue().uid()).isEqualTo(UID.toString());
    }

    @Test
    void desdeLaConversacionSinCuerpoOConUnoVacioVale() throws Exception {
        when(servicio.abrirDesdeConversacion(any(), isNull(), isNull())).thenReturn(ticket());

        mockMvc.perform(post("/chat/tickets/desde-conversacion")
                .with(jwt().jwt(j -> j.claim("uid", UID.toString()))))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/chat/tickets/desde-conversacion")
                .with(jwt().jwt(j -> j.claim("uid", UID.toString())))
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isCreated());
    }

    @Test
    void desdeLaConversacionSinTokenEs401YUnVisitanteNoEligeConversacion() throws Exception {
        when(servicio.abrirDesdeConversacion(isNull(), any(), any())).thenThrow(new SesionRequeridaException());

        mockMvc.perform(post("/chat/tickets/desde-conversacion")
                .header("X-Id-Sesion-Anonima", "anon_" + "A".repeat(43))
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.motivo").value("SESION_REQUERIDA"));
    }

    @Test
    void desdeLaConversacionUnComentarioLargoOUnaVistaInexistenteSon400() throws Exception {
        mockMvc.perform(post("/chat/tickets/desde-conversacion")
                .with(jwt().jwt(j -> j.claim("uid", UID.toString())))
                .contentType("application/json")
                .content("{\"comentario\":\"" + "x".repeat(2001) + "\"}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/chat/tickets/desde-conversacion")
                .with(jwt().jwt(j -> j.claim("uid", UID.toString())))
                .contentType("application/json")
                .content("{\"vista\":\"PANEL\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(servicio);
    }

    @Test
    void desdeLaConversacionSinPreguntasOConUnaAbiertaEs409ConSuMotivo() throws Exception {
        when(servicio.abrirDesdeConversacion(any(), any(), any()))
            .thenThrow(new SinConversacionException())
            .thenThrow(new TicketAbiertoException());

        mockMvc.perform(post("/chat/tickets/desde-conversacion").with(jwt().jwt(j -> j.claim("uid", UID.toString()))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.motivo").value("SIN_CONVERSACION"));
        mockMvc.perform(post("/chat/tickets/desde-conversacion").with(jwt().jwt(j -> j.claim("uid", UID.toString()))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.motivo").value("TICKET_ABIERTO"));
    }
}
