package com.nexusbattles.ms_chatbot.chat.api;

import com.nexusbattles.ms_chatbot.chat.identidad.IdentidadDelChat;
import com.nexusbattles.ms_chatbot.chat.identidad.ResolutorDeIdentidad;
import com.nexusbattles.ms_chatbot.chat.identidad.SesionesAnonimas;
import com.nexusbattles.ms_chatbot.chat.limite.LimiteDeFrecuenciaExcedido;
import com.nexusbattles.ms_chatbot.chat.moderacion.ContenidoBloqueado;
import com.nexusbattles.ms_chatbot.chat.moderacion.ModeracionNoDisponible;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.soporte.SesionRequeridaException;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
}
