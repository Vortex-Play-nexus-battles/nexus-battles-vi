package com.nexusbattles.ms_chatbot.chat.api;

import com.nexusbattles.ms_chatbot.chat.model.Calificacion;
import com.nexusbattles.ms_chatbot.chat.model.Mensaje;
import com.nexusbattles.ms_chatbot.chat.model.Remitente;
import com.nexusbattles.ms_chatbot.chat.service.CalificacionService;
import com.nexusbattles.ms_chatbot.chat.service.ChatService;
import com.nexusbattles.ms_chatbot.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
@Import(SecurityConfig.class)
class ChatControllerTest {

    private static final String CABECERA = "X-Id-Sesion-Anonima";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @MockitoBean
    private CalificacionService calificacionService;

    @Test
    void enviarMensaje_conCabeceraAnonima_devuelveElMensajeDelBot() throws Exception {
        Mensaje respuestaBot = crearMensajeBot("Respuesta de prueba");
        when(chatService.enviarMensaje(anyString(), eq(false), anyString(), any(), any()))
            .thenReturn(respuestaBot);

        mockMvc.perform(post("/chat/mensajes")
                .header(CABECERA, "visitante-001")
                .contentType("application/json")
                .content("{\"contenido\":\"Hola\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.remitente").value("BOT"))
            .andExpect(jsonPath("$.contenido").value("Respuesta de prueba"));
    }

    @Test
    void enviarMensaje_sinCabeceraNiJwt_devuelve400() throws Exception {
        mockMvc.perform(post("/chat/mensajes")
                .contentType("application/json")
                .content("{\"contenido\":\"Hola\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void enviarMensaje_conJwtAutenticado_usaElUidDelTokenComoIdentificador() throws Exception {
        Mensaje respuestaBot = crearMensajeBot("Hola usuario autenticado");
        when(chatService.enviarMensaje(eq("uid-123"), eq(true), anyString(), any(), any()))
            .thenReturn(respuestaBot);

        mockMvc.perform(post("/chat/mensajes")
                .with(jwt().jwt(j -> j.claim("uid", "uid-123")))
                .contentType("application/json")
                .content("{\"contenido\":\"Hola\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void obtenerHistorial_devuelveLosMensajesDeLaSesion() throws Exception {
        Mensaje mensaje = crearMensajeBot("Historial");
        when(chatService.obtenerHistorial("visitante-002"))
            .thenReturn(List.of(mensaje));

        mockMvc.perform(get("/chat/historial").header(CABECERA, "visitante-002"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].contenido").value("Historial"));
    }

    @Test
    void limpiarHistorial_devuelve204() throws Exception {
        mockMvc.perform(delete("/chat/historial").header(CABECERA, "visitante-003"))
            .andExpect(status().isNoContent());
    }

    // HU-CHA-011: un visitante califica una respuesta y recibe 201 con la
    // calificacion creada.
    @Test
    void calificarMensaje_visitante_devuelve201ConLaCalificacion() throws Exception {
        UUID mensajeId = UUID.randomUUID();
        Calificacion calificacion = crearCalificacion(mensajeId, true, "Muy clara");
        when(calificacionService.calificar(mensajeId, "visitante-010", true, "Muy clara"))
            .thenReturn(calificacion);

        mockMvc.perform(post("/chat/mensajes/{mensajeId}/calificacion", mensajeId)
                .header(CABECERA, "visitante-010")
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
        when(calificacionService.calificar(eq(mensajeId), eq("uid-123"), eq(false), any()))
            .thenReturn(calificacion);

        mockMvc.perform(post("/chat/mensajes/{mensajeId}/calificacion", mensajeId)
                .with(jwt().jwt(j -> j.claim("uid", "uid-123")))
                .contentType("application/json")
                .content("{\"util\":false}"))
            .andExpect(status().isCreated());

        verify(calificacionService).calificar(eq(mensajeId), eq("uid-123"), eq(false), any());
    }

    // Sin 'util' no se sabe que califico el usuario: 400 en vez de asumir
    // false y guardar un "no util" que nunca hizo.
    @Test
    void calificarMensaje_sinCampoUtil_devuelve400YNoLlamaAlServicio() throws Exception {
        mockMvc.perform(post("/chat/mensajes/{mensajeId}/calificacion", UUID.randomUUID())
                .header(CABECERA, "visitante-011")
                .contentType("application/json")
                .content("{\"comentario\":\"sin calificacion\"}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(calificacionService);
    }

    @Test
    void calificarMensaje_comentarioDemasiadoLargo_devuelve400YNoLlamaAlServicio() throws Exception {
        String comentarioLargo = "a".repeat(1001);

        mockMvc.perform(post("/chat/mensajes/{mensajeId}/calificacion", UUID.randomUUID())
                .header(CABECERA, "visitante-012")
                .contentType("application/json")
                .content("{\"util\":true,\"comentario\":\"" + comentarioLargo + "\"}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(calificacionService);
    }

    // HU-CHA-011, tarea tecnica 2 (lado HTTP): cuando el servicio detecta
    // una calificacion duplicada, el endpoint responde 409.
    @Test
    void calificarMensaje_yaCalificado_devuelve409() throws Exception {
        UUID mensajeId = UUID.randomUUID();
        when(calificacionService.calificar(eq(mensajeId), eq("visitante-013"), anyBoolean(), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Esta respuesta ya fue calificada."));

        mockMvc.perform(post("/chat/mensajes/{mensajeId}/calificacion", mensajeId)
                .header(CABECERA, "visitante-013")
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
