package com.nexusbattles.ms_chatbot.chat.soporte;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// ms-chatbot.yaml 1.3.0 (RF-ADM-004): /chatbot/admin/tickets.
@WebMvcTest(TicketsAdminController.class)
@Import({SecurityConfig.class, ManejadorErroresDeSoporte.class})
class TicketsAdminControllerTest {

    private static final String RUTA = "/chatbot/admin/tickets";
    private static final Instant AL_ABRIR = Instant.parse("2026-09-28T18:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketSoporteAdminService servicio;

    private static RequestPostProcessor administrador() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"));
    }

    private static TicketSoporte ticket() {
        return TicketSoporte.abrir("uid-1", Categoria.SOPORTE_TECNICO, "No carga", "Se queda cargando",
            List.of(new MensajeDeContexto("USUARIO", "hola", AL_ABRIR)), AL_ABRIR);
    }

    @Test
    void listarDevuelveLaPaginaSinContexto() throws Exception {
        when(servicio.listar(EstadoTicket.ABIERTO, 0, 20))
            .thenReturn(new PageImpl<>(List.of(ticket()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get(RUTA).param("estado", "ABIERTO").with(administrador()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElementos").value(1))
            .andExpect(jsonPath("$.pagina").value(0))
            .andExpect(jsonPath("$.tamano").value(20))
            .andExpect(jsonPath("$.contenido[0].asunto").value("No carga"))
            .andExpect(jsonPath("$.contenido[0].contexto").doesNotExist())
            .andExpect(jsonPath("$.contenido[0].uid").doesNotExist());
    }

    @Test
    void unTamanoFueraDeRangoEs400() throws Exception {
        mockMvc.perform(get(RUTA).param("tamano", "500").with(administrador()))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(servicio);
    }

    @Test
    void obtenerTraeUidYContexto() throws Exception {
        UUID id = UUID.randomUUID();
        when(servicio.obtener(id)).thenReturn(ticket());

        mockMvc.perform(get(RUTA + "/" + id).with(administrador()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uid").value("uid-1"))
            .andExpect(jsonPath("$.contexto[0].contenido").value("hola"))
            .andExpect(jsonPath("$.contexto[0].remitente").value("USUARIO"));
    }

    @Test
    void obtenerUnoQueNoExisteEs404() throws Exception {
        UUID id = UUID.randomUUID();
        when(servicio.obtener(id)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe"));

        mockMvc.perform(get(RUTA + "/" + id).with(administrador()))
            .andExpect(status().isNotFound());
    }

    @Test
    void atenderResuelveConRespuesta() throws Exception {
        UUID id = UUID.randomUUID();
        TicketSoporte resuelto = ticket();
        resuelto.atender(EstadoTicket.RESUELTO, "Ya quedo", null, false, AL_ABRIR);
        when(servicio.atender(eq(id), eq(EstadoTicket.RESUELTO), eq("Ya quedo"), isNull(), eq(false)))
            .thenReturn(resuelto);

        mockMvc.perform(patch(RUTA + "/" + id).with(administrador())
                .contentType("application/json")
                .content("{\"estado\":\"RESUELTO\",\"respuesta\":\"Ya quedo\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("RESUELTO"))
            .andExpect(jsonPath("$.respuesta").value("Ya quedo"));
    }

    @Test
    void asignadoANullDesasigna() throws Exception {
        UUID id = UUID.randomUUID();
        when(servicio.atender(eq(id), isNull(), isNull(), isNull(), eq(true))).thenReturn(ticket());

        mockMvc.perform(patch(RUTA + "/" + id).with(administrador())
                .contentType("application/json")
                .content("{\"asignadoA\":null}"))
            .andExpect(status().isOk());

        verify(servicio).atender(eq(id), isNull(), isNull(), isNull(), eq(true));
    }

    @Test
    void unCuerpoVacioEs400() throws Exception {
        mockMvc.perform(patch(RUTA + "/" + UUID.randomUUID()).with(administrador())
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(servicio);
    }

    @Test
    void unaTransicionInvalidaEs409ConMotivo() throws Exception {
        UUID id = UUID.randomUUID();
        when(servicio.atender(eq(id), any(), any(), any(), anyBoolean()))
            .thenThrow(new TransicionNoPermitidaException("El ticket esta cerrado y ya no se puede cambiar."));

        mockMvc.perform(patch(RUTA + "/" + id).with(administrador())
                .contentType("application/json")
                .content("{\"estado\":\"EN_PROCESO\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.motivo").value("TRANSICION_NO_PERMITIDA"));
    }

    @Test
    void unJugadorNoEntraYSinTokenTampoco() throws Exception {
        mockMvc.perform(get(RUTA).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_JUGADOR"))))
            .andExpect(status().isForbidden());
        mockMvc.perform(get(RUTA))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio);
    }
}
