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
import com.nexusbattles.ms_chatbot.chat.soporte.CapturaDeSoporte;
import com.nexusbattles.ms_chatbot.chat.soporte.CapturaDemasiadoGrandeException;
import com.nexusbattles.ms_chatbot.chat.soporte.CapturaNoAdmitidaException;
import com.nexusbattles.ms_chatbot.chat.soporte.CapturasCompletasException;
import com.nexusbattles.ms_chatbot.chat.soporte.CapturasDeSoporteService;
import com.nexusbattles.ms_chatbot.chat.soporte.ExaminadorDeCapturas;
import com.nexusbattles.ms_chatbot.chat.soporte.SesionRequeridaException;
import com.nexusbattles.ms_chatbot.chat.soporte.SinConversacionException;
import com.nexusbattles.ms_chatbot.chat.soporte.TicketAbiertoException;
import com.nexusbattles.ms_chatbot.chat.soporte.TicketCerradoException;
import com.nexusbattles.ms_chatbot.chat.soporte.TicketNoEncontradoException;
import com.nexusbattles.ms_chatbot.chat.soporte.TicketSoporte;
import com.nexusbattles.ms_chatbot.chat.soporte.TicketSoporteService;
import com.nexusbattles.ms_chatbot.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

    @MockitoBean
    private CapturasDeSoporteService capturas;

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

    // --- ms-chatbot.yaml 1.14.0 (§7.4.6): capturas de una solicitud --------

    private static final UUID SOLICITUD = UUID.fromString("3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f");
    private static final UUID CAPTURA = UUID.fromString("9e8d7c6b-5a49-4382-9716-05f4e3d2c1b0");
    private static final String RUTA_CAPTURAS = "/chat/tickets/" + SOLICITUD + "/capturas";

    private static byte[] png() throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB), "png", salida);
        return salida.toByteArray();
    }

    private static CapturaDeSoporte guardada(byte[] png) {
        CapturaDeSoporte captura = new CapturaDeSoporte(SOLICITUD, new ExaminadorDeCapturas().examinar(png), AHORA);
        ReflectionTestUtils.setField(captura, "id", CAPTURA);
        return captura;
    }

    private static MockMultipartFile archivo(byte[] contenido) {
        return new MockMultipartFile("archivo", "mi pantallazo.png", "image/png", contenido);
    }

    private static CapturaDeSoporte.Resumen resumen() {
        CapturaDeSoporte.Resumen resumen = mock(CapturaDeSoporte.Resumen.class);
        when(resumen.getId()).thenReturn(CAPTURA);
        when(resumen.getTicketId()).thenReturn(SOLICITUD);
        when(resumen.getTipo()).thenReturn("image/jpeg");
        when(resumen.getTamano()).thenReturn(5120);
        when(resumen.getAncho()).thenReturn(1280);
        when(resumen.getAlto()).thenReturn(720);
        when(resumen.getSubidaEn()).thenReturn(AHORA);
        return resumen;
    }

    @Test
    void adjuntarConTokenDevuelve201SoloConLosDatosDeLaCaptura() throws Exception {
        byte[] png = png();
        when(capturas.adjuntar(any(), eq(SOLICITUD), any())).thenReturn(guardada(png));

        mockMvc.perform(multipart(RUTA_CAPTURAS).file(archivo(png))
                .with(jwt().jwt(j -> j.claim("uid", UID.toString()))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.capturaId").value(CAPTURA.toString()))
            .andExpect(jsonPath("$.tipo").value("image/png"))
            .andExpect(jsonPath("$.ancho").value(20))
            .andExpect(jsonPath("$.alto").value(10))
            .andExpect(jsonPath("$.tamano").isNumber())
            .andExpect(jsonPath("$.subidaEn").exists())
            .andExpect(jsonPath("$.contenido").doesNotExist())
            .andExpect(jsonPath("$.nombre").doesNotExist());

        ArgumentCaptor<IdentidadDelChat> identidad = ArgumentCaptor.forClass(IdentidadDelChat.class);
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(capturas).adjuntar(identidad.capture(), eq(SOLICITUD), bytes.capture());
        assertThat(identidad.getValue().uid()).isEqualTo(UID.toString());
        assertThat(bytes.getValue()).isEqualTo(png);
    }

    @Test
    void unVisitanteAdjuntaConLaSesionVigenteDeSuNavegador() throws Exception {
        SesionAnonima sesion = sesionRegistrada();
        when(sesiones.validar(DECLARADA)).thenReturn(Optional.of(sesion));
        when(capturas.adjuntar(any(), any(), any())).thenReturn(guardada(png()));

        mockMvc.perform(multipart(RUTA_CAPTURAS).file(archivo(png())).header("X-Id-Sesion-Anonima", DECLARADA))
            .andExpect(status().isCreated());

        ArgumentCaptor<IdentidadDelChat> identidad = ArgumentCaptor.forClass(IdentidadDelChat.class);
        verify(capturas).adjuntar(identidad.capture(), eq(SOLICITUD), any());
        assertThat(identidad.getValue().autenticado()).isFalse();
        assertThat(identidad.getValue().claveDeConversacion()).isEqualTo(sesion.claveDeConversacion());
        verify(sesiones, never()).declarar(anyString(), any());
    }

    @Test
    void sinNadieQueLaFirmeEs401() throws Exception {
        when(capturas.adjuntar(isNull(), any(), any())).thenThrow(new SesionRequeridaException());

        mockMvc.perform(multipart(RUTA_CAPTURAS).file(archivo(png())))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.motivo").value("SESION_REQUERIDA"));
    }

    @Test
    void sinLaParteArchivoEs400ConMotivoYNoLlegaAlServicio() throws Exception {
        mockMvc.perform(multipart(RUTA_CAPTURAS)
                .file(new MockMultipartFile("imagen", "x.png", "image/png", png()))
                .with(jwt().jwt(j -> j.claim("uid", UID.toString()))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.motivo").value("CAPTURA_AUSENTE"));
        verifyNoInteractions(capturas);
    }

    @Test
    void unArchivoQueNoSePuedeLeerEs415SinLlegarAlServicio() throws Exception {
        MockMultipartFile ilegible = new MockMultipartFile("archivo", new byte[] {1}) {
            @Override
            public byte[] getBytes() throws IOException {
                throw new IOException("se corto la subida");
            }
        };

        mockMvc.perform(multipart(RUTA_CAPTURAS).file(ilegible)
                .with(jwt().jwt(j -> j.claim("uid", UID.toString()))))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.motivo").value("CAPTURA_NO_ADMITIDA"));
        verifyNoInteractions(capturas);
    }

    @Test
    void losRechazosDeUnaCapturaLlevanSuMotivo() throws Exception {
        when(capturas.adjuntar(any(), any(), any()))
            .thenThrow(new CapturaNoAdmitidaException("Solo PNG o JPEG."))
            .thenThrow(new CapturaDemasiadoGrandeException("La captura mide mas de 4096 pixeles por lado."))
            .thenThrow(new MaxUploadSizeExceededException(2L * 1024 * 1024))
            .thenThrow(new CapturasCompletasException())
            .thenThrow(new TicketCerradoException())
            .thenThrow(new TicketNoEncontradoException())
            .thenThrow(new LimiteDeFrecuenciaExcedido("espera", 7));
        Object[][] esperados = {
            {415, "CAPTURA_NO_ADMITIDA", "Solo PNG o JPEG."},
            {413, "CAPTURA_DEMASIADO_GRANDE", "La captura mide mas de 4096 pixeles por lado."},
            {413, "CAPTURA_DEMASIADO_GRANDE", "La captura pesa mas de 2 MB."},
            {409, "CAPTURAS_COMPLETAS", null},
            {409, "SOLICITUD_CERRADA", null},
            {404, "SOLICITUD_NO_ENCONTRADA", null},
            {429, "LIMITE_DE_FRECUENCIA", null},
        };
        for (Object[] esperado : esperados) {
            var resultado = mockMvc.perform(multipart(RUTA_CAPTURAS).file(archivo(new byte[] {1}))
                    .with(jwt().jwt(j -> j.claim("uid", UID.toString()))))
                .andExpect(status().is((int) esperado[0]))
                .andExpect(jsonPath("$.motivo").value((String) esperado[1]));
            if (esperado[2] != null) {
                resultado.andExpect(jsonPath("$.detail").value((String) esperado[2]));
            }
        }
    }

    @Test
    void laDuenaLeeSuCapturaSinQueSeEjecuteNiSeGuarde() throws Exception {
        CapturaDeSoporte captura = guardada(png());
        when(capturas.propiaDe(any(), eq(SOLICITUD), eq(CAPTURA))).thenReturn(captura);

        mockMvc.perform(get(RUTA_CAPTURAS + "/" + CAPTURA).with(jwt().jwt(j -> j.claim("uid", UID.toString()))))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/png"))
            .andExpect(content().bytes(captura.getContenido()))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
            .andExpect(header().string("Cache-Control", "no-store, private"));

        ArgumentCaptor<IdentidadDelChat> identidad = ArgumentCaptor.forClass(IdentidadDelChat.class);
        verify(capturas).propiaDe(identidad.capture(), eq(SOLICITUD), eq(CAPTURA));
        assertThat(identidad.getValue().uid()).isEqualTo(UID.toString());
    }

    @Test
    void laCapturaDeOtraPersonaEs404ComoUnaQueNoExiste() throws Exception {
        when(capturas.propiaDe(any(), any(), any())).thenThrow(new TicketNoEncontradoException());

        mockMvc.perform(get(RUTA_CAPTURAS + "/" + CAPTURA).with(jwt().jwt(j -> j.claim("uid", UID.toString())))
                .header("Accept", "image/*"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.motivo").value("SOLICITUD_NO_ENCONTRADA"));
    }

    @Test
    void misTicketsTraeLasCapturasDeCadaSolicitudSinSuContenido() throws Exception {
        TicketSoporte conCaptura = ticket();
        ReflectionTestUtils.setField(conCaptura, "id", SOLICITUD);
        TicketSoporte sinCaptura = ticket();
        ReflectionTestUtils.setField(sinCaptura, "id", UUID.fromString("5d6e7f80-9a1b-4c2d-8e3f-405162738495"));
        CapturaDeSoporte.Resumen resumen = resumen();
        when(servicio.misTickets(any())).thenReturn(List.of(conCaptura, sinCaptura));
        when(capturas.deLasSolicitudes(List.of(SOLICITUD, sinCaptura.getId())))
            .thenReturn(Map.of(SOLICITUD, List.of(resumen)));

        mockMvc.perform(get("/chat/tickets").with(jwt().jwt(j -> j.claim("uid", UID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].capturas.length()").value(1))
            .andExpect(jsonPath("$[0].capturas[0].capturaId").value(CAPTURA.toString()))
            .andExpect(jsonPath("$[0].capturas[0].tipo").value("image/jpeg"))
            .andExpect(jsonPath("$[0].capturas[0].ancho").value(1280))
            .andExpect(jsonPath("$[0].capturas[0].contenido").doesNotExist())
            .andExpect(jsonPath("$[1].capturas").doesNotExist());
    }
}
