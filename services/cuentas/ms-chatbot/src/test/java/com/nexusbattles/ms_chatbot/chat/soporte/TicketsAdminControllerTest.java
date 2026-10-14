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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// ms-chatbot.yaml 1.3.0 (RF-ADM-004): /chatbot/admin/tickets.
@WebMvcTest(TicketsAdminController.class)
@Import({SecurityConfig.class, ManejadorErroresDeSoporte.class})
class TicketsAdminControllerTest {

    private static final String RUTA = "/chatbot/admin/tickets";
    private static final Instant AL_ABRIR = Instant.parse("2026-09-28T18:00:00Z");
    private static final UUID SOLICITUD = UUID.fromString("3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f");
    private static final UUID CAPTURA = UUID.fromString("9e8d7c6b-5a49-4382-9716-05f4e3d2c1b0");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketSoporteAdminService servicio;

    @MockitoBean
    private CapturasDeSoporteService capturas;

    private static RequestPostProcessor administrador() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"));
    }

    private static TicketSoporte ticket() {
        TicketSoporte ticket = TicketSoporte.abrir("uid-1", Categoria.SOPORTE_TECNICO, "No carga",
            "Se queda cargando", List.of(new MensajeDeContexto("USUARIO", "hola", AL_ABRIR)), AL_ABRIR);
        ReflectionTestUtils.setField(ticket, "id", SOLICITUD);
        return ticket;
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

    // 1.3.9: reabrir cuando el jugador ya tiene otro abierto.
    @Test
    void reabrirConOtroAbiertoEs409ConSuMotivo() throws Exception {
        UUID id = UUID.randomUUID();
        when(servicio.atender(eq(id), any(), any(), any(), anyBoolean())).thenThrow(new OtroTicketAbiertoException());

        mockMvc.perform(patch(RUTA + "/" + id).with(administrador())
                .contentType("application/json")
                .content("{\"estado\":\"EN_PROCESO\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.motivo").value("OTRO_TICKET_ABIERTO"));
    }

    // CORS no autoriza nada: quien atiende un ticket lo decide el backend.
    @Test
    void atenderSinTokenEs401YComoJugadorEs403() throws Exception {
        String ruta = RUTA + "/" + UUID.randomUUID();
        mockMvc.perform(patch(ruta)
                .contentType("application/json")
                .content("{\"estado\":\"CERRADO\"}"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(patch(ruta).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_JUGADOR")))
                .contentType("application/json")
                .content("{\"estado\":\"CERRADO\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }

    @Test
    void unJugadorNoEntraYSinTokenTampoco() throws Exception {
        mockMvc.perform(get(RUTA).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_JUGADOR"))))
            .andExpect(status().isForbidden());
        mockMvc.perform(get(RUTA))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio);
    }

    // --- ms-chatbot.yaml 1.14.0 (§7.4.6): las capturas de la solicitud ------

    private static CapturaDeSoporte captura() throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 6, BufferedImage.TYPE_INT_RGB), "png", salida);
        CapturaDeSoporte captura = new CapturaDeSoporte(SOLICITUD,
            new ExaminadorDeCapturas().examinar(salida.toByteArray()), AL_ABRIR);
        ReflectionTestUtils.setField(captura, "id", CAPTURA);
        return captura;
    }

    @Test
    void obtenerYAtenderTraenSusCapturasSinElContenido() throws Exception {
        CapturaDeSoporte.Resumen resumen = mock(CapturaDeSoporte.Resumen.class);
        when(resumen.getId()).thenReturn(CAPTURA);
        when(resumen.getTipo()).thenReturn("image/png");
        when(resumen.getTamano()).thenReturn(1234);
        when(resumen.getAncho()).thenReturn(800);
        when(resumen.getAlto()).thenReturn(600);
        when(resumen.getSubidaEn()).thenReturn(AL_ABRIR);
        when(capturas.deLasSolicitudes(List.of(SOLICITUD))).thenReturn(Map.of(SOLICITUD, List.of(resumen)));
        when(servicio.obtener(SOLICITUD)).thenReturn(ticket());
        when(servicio.atender(eq(SOLICITUD), eq(EstadoTicket.EN_PROCESO), isNull(), isNull(), eq(false)))
            .thenReturn(ticket());

        mockMvc.perform(get(RUTA + "/" + SOLICITUD).with(administrador()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.capturas.length()").value(1))
            .andExpect(jsonPath("$.capturas[0].capturaId").value(CAPTURA.toString()))
            .andExpect(jsonPath("$.capturas[0].tipo").value("image/png"))
            .andExpect(jsonPath("$.capturas[0].tamano").value(1234))
            .andExpect(jsonPath("$.capturas[0].ancho").value(800))
            .andExpect(jsonPath("$.capturas[0].alto").value(600))
            .andExpect(jsonPath("$.capturas[0].contenido").doesNotExist());
        mockMvc.perform(patch(RUTA + "/" + SOLICITUD).with(administrador())
                .contentType("application/json")
                .content("{\"estado\":\"EN_PROCESO\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.capturas[0].capturaId").value(CAPTURA.toString()));
    }

    @Test
    void sinCapturasLaListaVaVacia() throws Exception {
        when(servicio.obtener(SOLICITUD)).thenReturn(ticket());

        mockMvc.perform(get(RUTA + "/" + SOLICITUD).with(administrador()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.capturas.length()").value(0));
    }

    @Test
    void elAdministradorLeeLaCapturaSinQueSeEjecuteNiSeGuarde() throws Exception {
        CapturaDeSoporte captura = captura();
        when(capturas.deCualquiera(SOLICITUD, CAPTURA)).thenReturn(captura);

        mockMvc.perform(get(RUTA + "/" + SOLICITUD + "/capturas/" + CAPTURA).with(administrador()))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/png"))
            .andExpect(content().bytes(captura.getContenido()))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
            .andExpect(header().string("Cache-Control", "no-store, private"))
            .andExpect(header().string("Cross-Origin-Resource-Policy", "same-origin"))
            .andExpect(header().string("Content-Disposition", "inline; filename=\"captura-" + CAPTURA + ".png\""));
    }

    @Test
    void unaCapturaQueNoEsDeEsaSolicitudEs404() throws Exception {
        when(capturas.deCualquiera(any(), any())).thenThrow(new TicketNoEncontradoException());

        mockMvc.perform(get(RUTA + "/" + SOLICITUD + "/capturas/" + CAPTURA).with(administrador()))
            .andExpect(status().isNotFound());
    }

    @Test
    void unJugadorNoLeeCapturasDelPanelYSinTokenTampoco() throws Exception {
        String ruta = RUTA + "/" + SOLICITUD + "/capturas/" + CAPTURA;
        mockMvc.perform(get(ruta).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_JUGADOR"))))
            .andExpect(status().isForbidden());
        mockMvc.perform(get(ruta))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(capturas);
    }
}
