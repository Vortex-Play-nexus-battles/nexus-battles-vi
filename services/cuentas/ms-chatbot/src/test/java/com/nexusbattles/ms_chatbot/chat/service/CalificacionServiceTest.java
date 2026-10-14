package com.nexusbattles.ms_chatbot.chat.service;

import com.nexusbattles.ms_chatbot.chat.entrada.InyeccionDetectada;
import com.nexusbattles.ms_chatbot.chat.entrada.RevisionDeEntrada;
import com.nexusbattles.ms_chatbot.chat.model.Calificacion;
import com.nexusbattles.ms_chatbot.chat.model.Conversacion;
import com.nexusbattles.ms_chatbot.chat.model.Mensaje;
import com.nexusbattles.ms_chatbot.chat.model.Remitente;
import com.nexusbattles.ms_chatbot.chat.moderacion.ContenidoBloqueado;
import com.nexusbattles.ms_chatbot.chat.moderacion.ModeracionDeContenido;
import com.nexusbattles.ms_chatbot.chat.privacidad.RedactorDeDatosSensibles;
import com.nexusbattles.ms_chatbot.chat.repository.CalificacionRepository;
import com.nexusbattles.ms_chatbot.chat.repository.MensajeRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalificacionServiceTest {

    private static final String SESION = "visitante-020";

    @Mock
    private MensajeRepository mensajeRepository;

    @Mock
    private CalificacionRepository calificacionRepository;

    @Mock
    private BrechaConocimientoService brechaConocimientoService;

    @Mock
    private ModeracionDeContenido moderacion;

    private CalificacionService calificacionService;

    private final UUID mensajeId = UUID.randomUUID();

    @BeforeEach
    void configurar() {
        calificacionService = new CalificacionService(mensajeRepository, calificacionRepository, brechaConocimientoService,
            new RevisionDeEntrada(new RedactorDeDatosSensibles(), new SimpleMeterRegistry()), moderacion);
    }

    @Test
    void calificar_util_guardaLaCalificacionYNoRegistraBrecha() {
        Mensaje respuesta = mensajeConRemitente(Remitente.BOT);
        when(mensajeRepository.findByIdAndConversacionIdentificadorSesion(mensajeId, SESION))
            .thenReturn(Optional.of(respuesta));
        when(calificacionRepository.existsByMensajeId(mensajeId)).thenReturn(false);
        when(calificacionRepository.saveAndFlush(any(Calificacion.class))).thenAnswer(inv -> inv.getArgument(0));

        Calificacion calificacion = calificacionService.calificar(mensajeId, SESION, true, "Muy clara");

        assertTrue(calificacion.isUtil());
        assertEquals("Muy clara", calificacion.getComentario());
        verifyNoInteractions(brechaConocimientoService);
    }

    // HU-CHA-011: "no util" registra como brecha la PREGUNTA del usuario que
    // origino la respuesta, no el texto de la respuesta del bot.
    @Test
    void calificar_noUtil_registraLaPreguntaOriginalComoBrecha() {
        Mensaje respuesta = respuestaDelBotEnConversacion();
        Mensaje pregunta = mock(Mensaje.class);
        when(pregunta.getContenido()).thenReturn("como subo de nivel");
        when(mensajeRepository.findByIdAndConversacionIdentificadorSesion(mensajeId, SESION))
            .thenReturn(Optional.of(respuesta));
        when(calificacionRepository.existsByMensajeId(mensajeId)).thenReturn(false);
        when(calificacionRepository.saveAndFlush(any(Calificacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mensajeRepository.findFirstByConversacionIdAndRemitenteAndFechaEnvioLessThanEqualOrderByFechaEnvioDesc(
            any(UUID.class), any(Remitente.class), any(Instant.class)))
            .thenReturn(Optional.of(pregunta));

        Calificacion calificacion = calificacionService.calificar(mensajeId, SESION, false, null);

        assertFalse(calificacion.isUtil());
        verify(brechaConocimientoService).registrarNoUtil("como subo de nivel");
    }

    // Si registrar la brecha falla, la calificacion del usuario no se pierde.
    @Test
    void calificar_noUtil_mantieneLaCalificacionAunqueFalleLaBrecha() {
        Mensaje respuesta = respuestaDelBotEnConversacion();
        Mensaje pregunta = mock(Mensaje.class);
        when(pregunta.getContenido()).thenReturn("pregunta rara");
        when(mensajeRepository.findByIdAndConversacionIdentificadorSesion(mensajeId, SESION))
            .thenReturn(Optional.of(respuesta));
        when(calificacionRepository.existsByMensajeId(mensajeId)).thenReturn(false);
        when(calificacionRepository.saveAndFlush(any(Calificacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mensajeRepository.findFirstByConversacionIdAndRemitenteAndFechaEnvioLessThanEqualOrderByFechaEnvioDesc(
            any(UUID.class), any(Remitente.class), any(Instant.class)))
            .thenReturn(Optional.of(pregunta));
        doThrow(new RuntimeException("choque con el indice unico"))
            .when(brechaConocimientoService).registrarNoUtil(anyString());

        Calificacion calificacion = calificacionService.calificar(mensajeId, SESION, false, null);

        assertFalse(calificacion.isUtil());
    }

    // Mensaje inexistente y mensaje de otra conversacion responden igual
    // (404), para no revelar que el mensaje existe.
    @Test
    void calificar_mensajeInexistenteOAjeno_lanza404SinGuardar() {
        when(mensajeRepository.findByIdAndConversacionIdentificadorSesion(mensajeId, SESION))
            .thenReturn(Optional.empty());

        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class,
            () -> calificacionService.calificar(mensajeId, SESION, true, null));

        assertEquals(HttpStatus.NOT_FOUND, excepcion.getStatusCode());
        verify(calificacionRepository, never()).saveAndFlush(any(Calificacion.class));
    }

    @Test
    void calificar_mensajeDelUsuario_lanza400SinGuardar() {
        Mensaje mensajeDelUsuario = mensajeConRemitente(Remitente.USUARIO);
        when(mensajeRepository.findByIdAndConversacionIdentificadorSesion(mensajeId, SESION))
            .thenReturn(Optional.of(mensajeDelUsuario));

        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class,
            () -> calificacionService.calificar(mensajeId, SESION, true, null));

        assertEquals(HttpStatus.BAD_REQUEST, excepcion.getStatusCode());
        verify(calificacionRepository, never()).saveAndFlush(any(Calificacion.class));
    }

    // HU-CHA-011, tarea tecnica 2: "no se admite calificar dos veces la
    // misma respuesta" (caso normal: ya existe una calificacion guardada).
    @Test
    void calificar_respuestaYaCalificada_lanza409SinGuardar() {
        Mensaje respuesta = mensajeConRemitente(Remitente.BOT);
        when(mensajeRepository.findByIdAndConversacionIdentificadorSesion(mensajeId, SESION))
            .thenReturn(Optional.of(respuesta));
        when(calificacionRepository.existsByMensajeId(mensajeId)).thenReturn(true);

        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class,
            () -> calificacionService.calificar(mensajeId, SESION, false, "otra vez"));

        assertEquals(HttpStatus.CONFLICT, excepcion.getStatusCode());
        verify(calificacionRepository, never()).saveAndFlush(any(Calificacion.class));
        verifyNoInteractions(brechaConocimientoService);
    }

    // HU-CHA-011, tarea tecnica 2 (carrera): dos peticiones simultaneas
    // pasan ambas el existsByMensajeId; la segunda choca con el indice unico
    // de V3 y tambien debe responder 409, no 500.
    @Test
    void calificar_calificacionSimultanea_chocaConIndiceUnicoYLanza409() {
        Mensaje respuesta = mensajeConRemitente(Remitente.BOT);
        when(mensajeRepository.findByIdAndConversacionIdentificadorSesion(mensajeId, SESION))
            .thenReturn(Optional.of(respuesta));
        when(calificacionRepository.existsByMensajeId(mensajeId)).thenReturn(false);
        when(calificacionRepository.saveAndFlush(any(Calificacion.class)))
            .thenThrow(new DataIntegrityViolationException("uk_calificaciones_mensaje_id"));

        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class,
            () -> calificacionService.calificar(mensajeId, SESION, false, null));

        assertEquals(HttpStatus.CONFLICT, excepcion.getStatusCode());
        verifyNoInteractions(brechaConocimientoService);
    }

    @Test
    void calificar_comentarioEnBlanco_seGuardaComoSinComentario() {
        Mensaje respuesta = mensajeConRemitente(Remitente.BOT);
        when(mensajeRepository.findByIdAndConversacionIdentificadorSesion(mensajeId, SESION))
            .thenReturn(Optional.of(respuesta));
        when(calificacionRepository.existsByMensajeId(mensajeId)).thenReturn(false);
        when(calificacionRepository.saveAndFlush(any(Calificacion.class))).thenAnswer(inv -> inv.getArgument(0));

        Calificacion calificacion = calificacionService.calificar(mensajeId, SESION, true, "   ");

        assertNull(calificacion.getComentario());
    }

    // 1.4.0 (7.4.8): el comentario se guarda redactado y pasa por la lista negra.
    @Test
    void calificar_comentarioConDatosSensibles_seGuardaRedactadoYModerado() {
        Mensaje respuesta = mensajeConRemitente(Remitente.BOT);
        when(mensajeRepository.findByIdAndConversacionIdentificadorSesion(mensajeId, SESION))
            .thenReturn(Optional.of(respuesta));
        when(calificacionRepository.existsByMensajeId(mensajeId)).thenReturn(false);
        when(calificacionRepository.saveAndFlush(any(Calificacion.class))).thenAnswer(inv -> inv.getArgument(0));

        Calificacion calificacion = calificacionService.calificar(mensajeId, SESION, true,
            "no me sirvio, mi password: Sup3r$ecreta");

        assertFalse(calificacion.getComentario().contains("Sup3r$ecreta"));
        verify(moderacion).verificar(calificacion.getComentario());
    }

    @Test
    void calificar_comentarioBloqueadoPorLaListaNegra_noGuarda() {
        Mensaje respuesta = mensajeConRemitente(Remitente.BOT);
        when(mensajeRepository.findByIdAndConversacionIdentificadorSesion(mensajeId, SESION))
            .thenReturn(Optional.of(respuesta));
        when(calificacionRepository.existsByMensajeId(mensajeId)).thenReturn(false);
        doThrow(new ContenidoBloqueado()).when(moderacion).verificar("algo feo");

        assertThrows(ContenidoBloqueado.class, () -> calificacionService.calificar(mensajeId, SESION, false, "algo feo"));

        verify(calificacionRepository, never()).saveAndFlush(any());
        verifyNoInteractions(brechaConocimientoService);
    }

    @Test
    void calificar_comentarioConInyeccion_noGuardaNiModera() {
        Mensaje respuesta = mensajeConRemitente(Remitente.BOT);
        when(mensajeRepository.findByIdAndConversacionIdentificadorSesion(mensajeId, SESION))
            .thenReturn(Optional.of(respuesta));
        when(calificacionRepository.existsByMensajeId(mensajeId)).thenReturn(false);

        assertThrows(InyeccionDetectada.class,
            () -> calificacionService.calificar(mensajeId, SESION, true, "<script>robar()</script>"));

        verify(calificacionRepository, never()).saveAndFlush(any());
        verifyNoInteractions(moderacion);
    }

    // Un mensaje ajeno sigue siendo 404 aunque el comentario traiga inyeccion:
    // la revision va despues de comprobar que la respuesta es suya.
    @Test
    void calificar_mensajeAjenoConComentarioMalicioso_sigueSiendo404() {
        when(mensajeRepository.findByIdAndConversacionIdentificadorSesion(mensajeId, SESION))
            .thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> calificacionService.calificar(mensajeId, SESION, true, "' OR '1'='1"));

        assertEquals(404, error.getStatusCode().value());
        verifyNoInteractions(moderacion);
    }

    private Mensaje mensajeConRemitente(Remitente remitente) {
        Mensaje mensaje = mock(Mensaje.class);
        when(mensaje.getRemitente()).thenReturn(remitente);
        return mensaje;
    }

    // Respuesta del bot con lo necesario para buscar su pregunta original.
    private Mensaje respuestaDelBotEnConversacion() {
        Conversacion conversacion = mock(Conversacion.class);
        when(conversacion.getId()).thenReturn(UUID.randomUUID());
        Mensaje respuesta = mensajeConRemitente(Remitente.BOT);
        when(respuesta.getConversacion()).thenReturn(conversacion);
        when(respuesta.getFechaEnvio()).thenReturn(Instant.now());
        return respuesta;
    }
}
