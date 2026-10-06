package com.nexusbattles.ms_chatbot.chat.api;

import com.nexusbattles.ms_chatbot.chat.enriquecido.EnlaceInterno;
import com.nexusbattles.ms_chatbot.chat.enriquecido.RespuestaEnriquecida;
import com.nexusbattles.ms_chatbot.chat.enriquecido.VistaDelChat;
import com.nexusbattles.ms_chatbot.chat.identidad.IdentidadDelChat;
import com.nexusbattles.ms_chatbot.chat.identidad.ResolutorDeIdentidad;
import com.nexusbattles.ms_chatbot.chat.identidad.SesionAnonima;
import com.nexusbattles.ms_chatbot.chat.identidad.SesionesAnonimas;
import com.nexusbattles.ms_chatbot.chat.limite.LimiteDeFrecuenciaExcedido;
import com.nexusbattles.ms_chatbot.chat.model.Calificacion;
import com.nexusbattles.ms_chatbot.chat.model.Mensaje;
import com.nexusbattles.ms_chatbot.chat.model.Remitente;
import com.nexusbattles.ms_chatbot.chat.moderacion.ContenidoBloqueado;
import com.nexusbattles.ms_chatbot.chat.moderacion.ModeracionNoDisponible;
import com.nexusbattles.ms_chatbot.chat.service.CalificacionService;
import com.nexusbattles.ms_chatbot.chat.service.ChatService;
import com.nexusbattles.ms_chatbot.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// B11: la identidad la resuelve ResolutorDeIdentidad (real aqui); las sesiones
// de visitante y los servicios van simulados. El ataque completo contra la
// base real esta en SeguridadDelHistorialIT.
@WebMvcTest(ChatController.class)
@Import({SecurityConfig.class, ResolutorDeIdentidad.class, ManejadorErroresDelChat.class})
class ChatControllerTest {

    private static final String CABECERA = "X-Id-Sesion-Anonima";
    private static final String SESION = "anon_" + "A".repeat(43);
    private static final UUID UID = UUID.fromString("11111111-2222-4333-8444-555555555555");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @MockitoBean
    private CalificacionService calificacionService;

    @MockitoBean
    private SesionesAnonimas sesiones;

    private static SesionAnonima sesion() {
        return new SesionAnonima("huella", Instant.parse("2026-10-01T10:00:00Z"), Duration.ofHours(24));
    }

    @Test
    void enviarMensaje_conSesionValida_usaLaConversacionDelVisitante() throws Exception {
        SesionAnonima sesion = sesion();
        when(sesiones.validar(SESION)).thenReturn(Optional.of(sesion));
        Mensaje respuestaBot = crearMensajeBot("Respuesta de prueba");
        when(chatService.enviarMensaje(any(), anyString(), any(), any())).thenReturn(respuestaBot);

        mockMvc.perform(post("/chat/mensajes")
                .header(CABECERA, SESION)
                .contentType("application/json")
                .content("{\"contenido\":\"Hola\"}"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(CABECERA))
            .andExpect(jsonPath("$.remitente").value("BOT"))
            .andExpect(jsonPath("$.contenido").value("Respuesta de prueba"));

        ArgumentCaptor<IdentidadDelChat> identidad = ArgumentCaptor.forClass(IdentidadDelChat.class);
        verify(chatService).enviarMensaje(identidad.capture(), eq("Hola"), any(), any());
        assertThat(identidad.getValue().autenticado()).isFalse();
        assertThat(identidad.getValue().claveDeConversacion()).isEqualTo("anonimo:" + sesion.getId());
    }

    // 1.3.0: adjuntoUrl esta obsoleto. Se acepta en el cuerpo (200, no 400)
    // pero no llega al servicio: nunca se guarda.
    @Test
    void enviarMensaje_conAdjuntoUrl_loAceptaPeroNoLoGuarda() throws Exception {
        when(sesiones.validar(SESION)).thenReturn(Optional.of(sesion()));
        Mensaje respuestaBot = crearMensajeBot("Respuesta de prueba");
        when(chatService.enviarMensaje(any(), anyString(), any(), any())).thenReturn(respuestaBot);

        mockMvc.perform(post("/chat/mensajes")
                .header(CABECERA, SESION)
                .contentType("application/json")
                .content("{\"contenido\":\"Hola\",\"adjuntoUrl\":\"https://img.example/captura.png\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contenido").value("Respuesta de prueba"));

        verify(chatService).enviarMensaje(any(), eq("Hola"), isNull(), any());
    }

    // 1.3.4: la vista llega al servicio y la respuesta trae lo enriquecido.
    @Test
    void enviarMensaje_conVista_laPasaAlServicioYDevuelveLoEnriquecido() throws Exception {
        when(sesiones.validar(SESION)).thenReturn(Optional.of(sesion()));
        Mensaje respuestaBot = crearMensajeBot("Para pujar necesitas créditos.");
        when(respuestaBot.getEnriquecido()).thenReturn(new RespuestaEnriquecida(List.of("Elige", "Puja"),
            List.of(new EnlaceInterno("Ir a Subastas", "subastas")), List.of(), List.of("Cómo publicar"), false));
        when(chatService.enviarMensaje(any(), anyString(), any(), any())).thenReturn(respuestaBot);

        mockMvc.perform(post("/chat/mensajes")
                .header(CABECERA, SESION)
                .contentType("application/json")
                .content("{\"contenido\":\"como pujo\",\"vista\":\"SUBASTAS\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enriquecido.pasos[1]").value("Puja"))
            .andExpect(jsonPath("$.enriquecido.enlaces[0].destino").value("subastas"))
            .andExpect(jsonPath("$.enriquecido.respuestasRapidas[0]").value("Cómo publicar"))
            .andExpect(jsonPath("$.enriquecido.ofrecerSoporteHumano").value(false));

        verify(chatService).enviarMensaje(any(), eq("como pujo"), isNull(), eq(VistaDelChat.SUBASTAS));
    }

    @Test
    void enviarMensaje_conUnaVistaQueNoExiste_devuelve400() throws Exception {
        mockMvc.perform(post("/chat/mensajes")
                .header(CABECERA, SESION)
                .contentType("application/json")
                .content("{\"contenido\":\"hola\",\"vista\":\"COCINA\"}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(chatService);
    }

    // 1.2.0 (#708): el asistente de la interfaz manda su propio identificador,
    // 'visitante-' + crypto.randomUUID(). Se registra como sesion de visitante
    // y no se emite otra: el navegador sigue con el suyo.
    @Test
    void enviarMensaje_conIdentificadorDeclaradoPorElNavegador_loRegistraYNoEmiteOtro() throws Exception {
        String declarada = "visitante-" + UUID.randomUUID();
        SesionAnonima sesion = sesion();
        when(sesiones.validar(declarada)).thenReturn(Optional.empty());
        when(sesiones.declarar(declarada, "203.0.113.7")).thenReturn(sesion);
        Mensaje respuestaBot = crearMensajeBot("Hola visitante");
        when(chatService.enviarMensaje(any(), anyString(), any(), any())).thenReturn(respuestaBot);

        mockMvc.perform(post("/chat/mensajes")
                .header(CABECERA, declarada)
                .header("X-Real-IP", "203.0.113.7")
                .contentType("application/json")
                .content("{\"contenido\":\"Hola\"}"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(CABECERA));

        ArgumentCaptor<IdentidadDelChat> identidad = ArgumentCaptor.forClass(IdentidadDelChat.class);
        verify(chatService).enviarMensaje(identidad.capture(), eq("Hola"), any(), any());
        assertThat(identidad.getValue().autenticado()).isFalse();
        assertThat(identidad.getValue().claveDeConversacion()).isEqualTo("anonimo:" + sesion.getId());
        verify(sesiones, never()).emitir(anyString());
    }

    // Sin sesion valida, el servidor emite una y la devuelve en la cabecera;
    // el mensaje abre una conversacion nueva de visitante.
    @Test
    void enviarMensaje_sinSesion_emiteUnaNuevaYLaDevuelveEnLaCabecera() throws Exception {
        SesionAnonima sesion = sesion();
        String emitida = "anon_" + "B".repeat(43);
        when(sesiones.validar(any())).thenReturn(Optional.empty());
        when(sesiones.emitir(anyString())).thenReturn(new SesionesAnonimas.Emitida(emitida, sesion));
        Mensaje respuestaBot = crearMensajeBot("Hola visitante");
        when(chatService.enviarMensaje(any(), anyString(), any(), any())).thenReturn(respuestaBot);

        mockMvc.perform(post("/chat/mensajes")
                .header("X-Real-IP", "203.0.113.7")
                .contentType("application/json")
                .content("{\"contenido\":\"Hola\"}"))
            .andExpect(status().isOk())
            .andExpect(header().string(CABECERA, emitida));

        verify(sesiones).emitir("203.0.113.7");
    }

    // El token manda: la cabecera se ignora aunque traiga la sesion de otro.
    @Test
    void enviarMensaje_conJwt_usaSoloElUidDelTokenEIgnoraLaCabecera() throws Exception {
        Mensaje respuestaBot = crearMensajeBot("Hola jugador");
        when(chatService.enviarMensaje(any(), anyString(), any(), any())).thenReturn(respuestaBot);

        mockMvc.perform(post("/chat/mensajes")
                .with(jwt().jwt(j -> j.claim("uid", UID.toString())))
                .header(CABECERA, SESION)
                .contentType("application/json")
                .content("{\"contenido\":\"Hola\"}"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(CABECERA));

        ArgumentCaptor<IdentidadDelChat> identidad = ArgumentCaptor.forClass(IdentidadDelChat.class);
        verify(chatService).enviarMensaje(identidad.capture(), eq("Hola"), any(), any());
        assertThat(identidad.getValue().autenticado()).isTrue();
        assertThat(identidad.getValue().claveDeConversacion()).isEqualTo(UID.toString());
        verifyNoInteractions(sesiones);
    }

    // Un token que no identifica a una persona (sin uid UUID) degrada a visitante.
    @Test
    void enviarMensaje_conTokenSinUid_seTrataComoVisitante() throws Exception {
        when(sesiones.validar(any())).thenReturn(Optional.empty());
        when(sesiones.emitir(anyString())).thenReturn(new SesionesAnonimas.Emitida("anon_" + "C".repeat(43), sesion()));
        Mensaje respuestaBot = crearMensajeBot("Hola");
        when(chatService.enviarMensaje(any(), anyString(), any(), any())).thenReturn(respuestaBot);

        mockMvc.perform(post("/chat/mensajes")
                .with(jwt().jwt(j -> j.subject("torneos").claim("rol", "SERVICIO")))
                .contentType("application/json")
                .content("{\"contenido\":\"Hola\"}"))
            .andExpect(status().isOk())
            .andExpect(header().exists(CABECERA));
    }

    // El ataque de la auditoria, del lado HTTP: el uid de un jugador en la
    // cabecera no es una sesion emitida, asi que no identifica a nadie.
    @Test
    void historial_conUnUidEnLaCabecera_devuelveVacioYNoConsultaNada() throws Exception {
        when(sesiones.validar(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/chat/historial").header(CABECERA, UID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());

        verifyNoInteractions(chatService);
    }

    @Test
    void limpiarHistorial_conUnUidEnLaCabecera_noBorraNada() throws Exception {
        when(sesiones.validar(any())).thenReturn(Optional.empty());

        mockMvc.perform(delete("/chat/historial").header(CABECERA, UID.toString()))
            .andExpect(status().isNoContent());

        verifyNoInteractions(chatService);
    }

    @Test
    void calificar_sinIdentidadValida_devuelve404SinRevelarNada() throws Exception {
        when(sesiones.validar(any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/chat/mensajes/{mensajeId}/calificacion", UUID.randomUUID())
                .header(CABECERA, UID.toString())
                .contentType("application/json")
                .content("{\"util\":true}"))
            .andExpect(status().isNotFound());

        verifyNoInteractions(calificacionService);
    }

    @Test
    void obtenerHistorial_conSesionValida_devuelveLosMensajesDeLaSesion() throws Exception {
        SesionAnonima sesion = sesion();
        when(sesiones.validar(SESION)).thenReturn(Optional.of(sesion));
        Mensaje mensaje = crearMensajeBot("Historial");
        when(chatService.obtenerHistorial(any())).thenReturn(List.of(mensaje));

        mockMvc.perform(get("/chat/historial").header(CABECERA, SESION))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].contenido").value("Historial"));
    }


    // 1.3.5: con antesDe o limite, una pagina; sin ellos, todo (compatibilidad).
    @Test
    void obtenerHistorial_conCursorYLimite_pideUnaPagina() throws Exception {
        when(sesiones.validar(SESION)).thenReturn(Optional.of(sesion()));
        UUID cursor = UUID.randomUUID();
        Mensaje mensaje = crearMensajeBot("Pagina");
        when(chatService.obtenerHistorial(any(), eq(cursor), eq(20))).thenReturn(List.of(mensaje));

        mockMvc.perform(get("/chat/historial").header(CABECERA, SESION)
                .param("antesDe", cursor.toString())
                .param("limite", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].contenido").value("Pagina"));

        verify(chatService, never()).obtenerHistorial(any());
    }

    @Test
    void obtenerHistorial_soloConCursor_usaElLimitePorDefecto() throws Exception {
        when(sesiones.validar(SESION)).thenReturn(Optional.of(sesion()));
        UUID cursor = UUID.randomUUID();

        mockMvc.perform(get("/chat/historial").header(CABECERA, SESION).param("antesDe", cursor.toString()))
            .andExpect(status().isOk());

        verify(chatService).obtenerHistorial(any(), eq(cursor), eq(50));
    }

    @Test
    void obtenerHistorial_conParametrosInvalidos_responde400() throws Exception {
        mockMvc.perform(get("/chat/historial").param("limite", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/chat/historial").param("limite", "101")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/chat/historial").param("antesDe", "no-es-un-uuid")).andExpect(status().isBadRequest());

        verifyNoInteractions(chatService);
    }

    @Test
    void limpiarHistorial_conJwt_borraLaConversacionDelUsuario() throws Exception {
        mockMvc.perform(delete("/chat/historial").with(jwt().jwt(j -> j.claim("uid", UID.toString()))))
            .andExpect(status().isNoContent());

        ArgumentCaptor<IdentidadDelChat> identidad = ArgumentCaptor.forClass(IdentidadDelChat.class);
        verify(chatService).limpiarHistorial(identidad.capture());
        assertThat(identidad.getValue().claveDeConversacion()).isEqualTo(UID.toString());
    }

    @Test
    void crearSesion_devuelve201ConElIdentificadorEnElCuerpoYEnLaCabecera() throws Exception {
        String emitida = "anon_" + "D".repeat(43);
        SesionAnonima sesion = sesion();
        when(sesiones.emitir(anyString())).thenReturn(new SesionesAnonimas.Emitida(emitida, sesion));

        mockMvc.perform(post("/chat/sesiones"))
            .andExpect(status().isCreated())
            .andExpect(header().string(CABECERA, emitida))
            .andExpect(jsonPath("$.idSesionAnonima").value(emitida))
            .andExpect(jsonPath("$.expiraEn").exists());
    }

    // 7.4.8: limite de frecuencia -> 429 con Retry-After y motivo.
    @Test
    void crearSesion_porEncimaDelLimite_devuelve429ConRetryAfter() throws Exception {
        when(sesiones.emitir(anyString())).thenThrow(new LimiteDeFrecuenciaExcedido("Demasiadas", 42));

        mockMvc.perform(post("/chat/sesiones"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "42"))
            .andExpect(jsonPath("$.motivo").value("LIMITE_DE_FRECUENCIA"));
    }

    @Test
    void enviarMensaje_bloqueadoPorLaListaNegra_devuelve422() throws Exception {
        when(sesiones.validar(SESION)).thenReturn(Optional.of(sesion()));
        when(chatService.enviarMensaje(any(), anyString(), any(), any())).thenThrow(new ContenidoBloqueado());

        mockMvc.perform(post("/chat/mensajes")
                .header(CABECERA, SESION)
                .contentType("application/json")
                .content("{\"contenido\":\"algo feo\"}"))
            .andExpect(status().is(422))
            .andExpect(jsonPath("$.motivo").value("CONTENIDO_BLOQUEADO"));
    }

    @Test
    void enviarMensaje_sinListaNegra_devuelve503() throws Exception {
        when(sesiones.validar(SESION)).thenReturn(Optional.of(sesion()));
        when(chatService.enviarMensaje(any(), anyString(), any(), any())).thenThrow(new ModeracionNoDisponible());

        mockMvc.perform(post("/chat/mensajes")
                .header(CABECERA, SESION)
                .contentType("application/json")
                .content("{\"contenido\":\"hola\"}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.motivo").value("MODERACION_NO_DISPONIBLE"));
    }

    // HU-CHA-011: un visitante califica una respuesta y recibe 201 con la
    // calificacion creada.
    @Test
    void calificarMensaje_visitante_devuelve201ConLaCalificacion() throws Exception {
        SesionAnonima sesion = sesion();
        when(sesiones.validar(SESION)).thenReturn(Optional.of(sesion));
        UUID mensajeId = UUID.randomUUID();
        Calificacion calificacion = crearCalificacion(mensajeId, true, "Muy clara");
        when(calificacionService.calificar(mensajeId, "anonimo:" + sesion.getId(), true, "Muy clara"))
            .thenReturn(calificacion);

        mockMvc.perform(post("/chat/mensajes/{mensajeId}/calificacion", mensajeId)
                .header(CABECERA, SESION)
                .contentType("application/json")
                .content("{\"util\":true,\"comentario\":\"Muy clara\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.mensajeId").value(mensajeId.toString()))
            .andExpect(jsonPath("$.util").value(true))
            .andExpect(jsonPath("$.comentario").value("Muy clara"));
    }

    // HU-CHA-011: un usuario autenticado califica con su uid como identidad,
    // igual que en el resto del chat.
    @Test
    void calificarMensaje_conJwt_usaElUidDelTokenComoIdentificador() throws Exception {
        UUID mensajeId = UUID.randomUUID();
        Calificacion calificacion = crearCalificacion(mensajeId, false, null);
        when(calificacionService.calificar(eq(mensajeId), eq(UID.toString()), eq(false), any()))
            .thenReturn(calificacion);

        mockMvc.perform(post("/chat/mensajes/{mensajeId}/calificacion", mensajeId)
                .with(jwt().jwt(j -> j.claim("uid", UID.toString())))
                .contentType("application/json")
                .content("{\"util\":false}"))
            .andExpect(status().isCreated());

        verify(calificacionService).calificar(eq(mensajeId), eq(UID.toString()), eq(false), any());
    }

    // Sin 'util' no se sabe que califico el usuario: 400 en vez de asumir
    // false y guardar un "no util" que nunca hizo.
    @Test
    void calificarMensaje_sinCampoUtil_devuelve400YNoLlamaAlServicio() throws Exception {
        mockMvc.perform(post("/chat/mensajes/{mensajeId}/calificacion", UUID.randomUUID())
                .header(CABECERA, SESION)
                .contentType("application/json")
                .content("{\"comentario\":\"sin calificacion\"}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(calificacionService);
    }

    @Test
    void calificarMensaje_comentarioDemasiadoLargo_devuelve400YNoLlamaAlServicio() throws Exception {
        String comentarioLargo = "a".repeat(1001);

        mockMvc.perform(post("/chat/mensajes/{mensajeId}/calificacion", UUID.randomUUID())
                .header(CABECERA, SESION)
                .contentType("application/json")
                .content("{\"util\":true,\"comentario\":\"" + comentarioLargo + "\"}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(calificacionService);
    }

    // HU-CHA-011, tarea tecnica 2 (lado HTTP): cuando el servicio detecta
    // una calificacion duplicada, el endpoint responde 409.
    @Test
    void calificarMensaje_yaCalificado_devuelve409() throws Exception {
        when(sesiones.validar(SESION)).thenReturn(Optional.of(sesion()));
        UUID mensajeId = UUID.randomUUID();
        when(calificacionService.calificar(eq(mensajeId), anyString(), anyBoolean(), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Esta respuesta ya fue calificada."));

        mockMvc.perform(post("/chat/mensajes/{mensajeId}/calificacion", mensajeId)
                .header(CABECERA, SESION)
                .contentType("application/json")
                .content("{\"util\":true}"))
            .andExpect(status().isConflict());
    }

    private Mensaje crearMensajeBot(String contenido) {
        Mensaje mensaje = mock(Mensaje.class);
        when(mensaje.getRemitente()).thenReturn(Remitente.BOT);
        when(mensaje.getContenido()).thenReturn(contenido);
        return mensaje;
    }

    private Calificacion crearCalificacion(UUID mensajeId, boolean util, String comentario) {
        Mensaje mensaje = mock(Mensaje.class);
        when(mensaje.getId()).thenReturn(mensajeId);

        Calificacion calificacion = mock(Calificacion.class);
        when(calificacion.getId()).thenReturn(UUID.randomUUID());
        when(calificacion.getMensaje()).thenReturn(mensaje);
        when(calificacion.isUtil()).thenReturn(util);
        when(calificacion.getComentario()).thenReturn(comentario);
        when(calificacion.getFechaCalificacion()).thenReturn(Instant.now());
        return calificacion;
    }
}
