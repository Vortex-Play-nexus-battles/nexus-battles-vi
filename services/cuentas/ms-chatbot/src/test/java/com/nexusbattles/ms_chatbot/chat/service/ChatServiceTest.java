package com.nexusbattles.ms_chatbot.chat.service;

import com.nexusbattles.ms_chatbot.chat.consultas.MotorConsultasAsistidas;
import com.nexusbattles.ms_chatbot.chat.identidad.IdentidadDelChat;
import com.nexusbattles.ms_chatbot.chat.identidad.SesionAnonima;
import com.nexusbattles.ms_chatbot.chat.limite.LimitadorDeFrecuencia;
import com.nexusbattles.ms_chatbot.chat.limite.LimiteDeFrecuenciaExcedido;
import com.nexusbattles.ms_chatbot.chat.model.Conversacion;
import com.nexusbattles.ms_chatbot.chat.model.Mensaje;
import com.nexusbattles.ms_chatbot.chat.model.Remitente;
import com.nexusbattles.ms_chatbot.chat.moderacion.ContenidoBloqueado;
import com.nexusbattles.ms_chatbot.chat.moderacion.ModeracionDeContenido;
import com.nexusbattles.ms_chatbot.chat.motor.MotorRespuestas;
import com.nexusbattles.ms_chatbot.chat.motor.ResultadoMotor;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import com.nexusbattles.ms_chatbot.chat.repository.ConversacionRepository;
import com.nexusbattles.ms_chatbot.chat.repository.MensajeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ConversacionRepository conversacionRepository;
    @Mock
    private MensajeRepository mensajeRepository;
    @Mock
    private MotorRespuestas motorRespuestas;
    @Mock
    private MotorConsultasAsistidas motorConsultasAsistidas;
    @Mock
    private BrechaConocimientoService brechaConocimientoService;
    @Mock
    private LimitadorDeFrecuencia limitador;
    @Mock
    private ModeracionDeContenido moderacion;
    @Mock
    private RegistroDeConversaciones registro;

    private ChatService chatService;

    private static final UUID UID = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private final IdentidadDelChat usuario = IdentidadDelChat.usuario(UID, "token-de-prueba");
    private final IdentidadDelChat visitante = IdentidadDelChat.visitante(
        new SesionAnonima("huella", Instant.parse("2026-10-01T10:00:00Z"), Duration.ofHours(24)));

    @BeforeEach
    void configurar() {
        chatService = new ChatService(conversacionRepository, mensajeRepository, motorRespuestas,
            motorConsultasAsistidas, brechaConocimientoService, limitador, moderacion, registro);
    }

    private void registroDevuelveLaRespuesta() {
        when(registro.guardarIntercambio(any(), anyString(), any(), any(), anyString(), any(), anyInt()))
            .thenAnswer(inv -> {
                ResultadoMotor resultado = inv.getArgument(5);
                Mensaje bot = new Mensaje(mock(Conversacion.class), Remitente.BOT, inv.getArgument(4), null);
                bot.registrarDatosDeRespuesta(resultado.temaClave(), resultado.categoria(),
                    resultado.requiereEscalamiento(), inv.getArgument(6));
                return bot;
            });
    }

    @Test
    void enviarMensaje_compruebaLimiteYListaNegraAntesDeResponderYGuardaElIntercambio() {
        registroDevuelveLaRespuesta();
        when(motorRespuestas.generarRespuesta(anyString()))
            .thenReturn(ResultadoMotor.deTema("Respuesta de prueba", Categoria.FAQ_GENERAL, TipoRespuesta.DIRECTA));

        Mensaje respuesta = chatService.enviarMensaje(visitante, "Hola", null);

        verify(limitador).exigir(LimitadorDeFrecuencia.Regla.MENSAJES, visitante.claveDeLimite());
        verify(moderacion).verificar("Hola");
        verify(registro).guardarIntercambio(eq(visitante), eq("Hola"), any(), any(), eq("Respuesta de prueba"), any(), anyInt());
        assertEquals(Remitente.BOT, respuesta.getRemitente());
        verifyNoInteractions(motorConsultasAsistidas);
    }

    // 7.4.8: un mensaje bloqueado por la lista negra no se procesa ni se guarda.
    @Test
    void enviarMensaje_bloqueado_noProcesaNiGuarda() {
        doThrow(new ContenidoBloqueado()).when(moderacion).verificar("algo feo");

        assertThrows(ContenidoBloqueado.class, () -> chatService.enviarMensaje(visitante, "algo feo", null));

        verifyNoInteractions(motorRespuestas, motorConsultasAsistidas, registro);
    }

    // 7.4.8: por encima del limite, ni se modera ni se guarda.
    @Test
    void enviarMensaje_porEncimaDelLimite_noHaceNadaMas() {
        doThrow(new LimiteDeFrecuenciaExcedido("Demasiadas", 30))
            .when(limitador).exigir(LimitadorDeFrecuencia.Regla.MENSAJES, usuario.claveDeLimite());

        assertThrows(LimiteDeFrecuenciaExcedido.class, () -> chatService.enviarMensaje(usuario, "hola", null));

        verifyNoInteractions(moderacion, motorRespuestas, motorConsultasAsistidas, registro);
    }

    // HU-CHA-008, tarea tecnica 4: un visitante (no autenticado) nunca debe
    // disparar el motor de consultas asistidas, ni siquiera cuando su
    // mensaje se parece a una pregunta de cuenta (ej. "mi inventario").
    @Test
    void visitanteConMensajeQueParecePreguntaDeCuenta_noConsultaElMotorAsistido() {
        registroDevuelveLaRespuesta();
        when(motorRespuestas.generarRespuesta(anyString()))
            .thenReturn(ResultadoMotor.deTema("No tengo esa informacion sin que inicies sesion.", Categoria.FAQ_GENERAL, TipoRespuesta.DIRECTA));

        chatService.enviarMensaje(visitante, "cual es mi inventario", null);

        verifyNoInteractions(motorConsultasAsistidas);
        verify(motorRespuestas).generarRespuesta("cual es mi inventario");
    }

    // El motor asistido recibe el token y el uid del PROPIO usuario (ADR-001):
    // nunca una credencial de servicio.
    @Test
    void usuarioAutenticadoConRespuestaDelMotorAsistido_noConsultaMotorRespuestas() {
        registroDevuelveLaRespuesta();
        when(motorConsultasAsistidas.generarRespuesta("cual es mi inventario", "token-de-prueba", UID.toString()))
            .thenReturn(Optional.of(ResultadoMotor.deTema("Tienes 3 elementos en tu inventario.", null, TipoRespuesta.CONTEXTUAL)));

        Mensaje respuesta = chatService.enviarMensaje(usuario, "cual es mi inventario", null);

        verifyNoInteractions(motorRespuestas);
        assertTrue(respuesta.getContenido().contains("Tienes 3 elementos en tu inventario."));
    }

    @Test
    void usuarioAutenticadoSinIntencionDeCuenta_caeEnLaBaseDeConocimiento() {
        registroDevuelveLaRespuesta();
        when(motorConsultasAsistidas.generarRespuesta(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(motorRespuestas.generarRespuesta(anyString()))
            .thenReturn(ResultadoMotor.deTema("Respuesta de prueba", Categoria.FAQ_GENERAL, TipoRespuesta.DIRECTA));

        chatService.enviarMensaje(usuario, "Segunda pregunta", null);

        verify(motorRespuestas).generarRespuesta("Segunda pregunta");
    }

    // Caso defensivo: autenticado pero sin token (no deberia pasar), no se
    // consulta el motor asistido.
    @Test
    void usuarioAutenticadoSinTokenBearer_noConsultaElMotorAsistido() {
        registroDevuelveLaRespuesta();
        when(motorRespuestas.generarRespuesta(anyString()))
            .thenReturn(ResultadoMotor.deTema("Respuesta de prueba", Categoria.FAQ_GENERAL, TipoRespuesta.DIRECTA));

        chatService.enviarMensaje(IdentidadDelChat.usuario(UID, null), "hola", null);

        verifyNoInteractions(motorConsultasAsistidas);
    }

    // Dos primeros mensajes simultaneos: el segundo choca con el indice unico
    // de la conversacion y se reintenta una vez en la que gano.
    @Test
    void enviarMensaje_carreraAlCrearLaConversacion_reintentaUnaVez() {
        when(motorRespuestas.generarRespuesta(anyString()))
            .thenReturn(ResultadoMotor.deTema("Respuesta de prueba", Categoria.FAQ_GENERAL, TipoRespuesta.DIRECTA));
        Mensaje bot = new Mensaje(mock(Conversacion.class), Remitente.BOT, "Respuesta de prueba", null);
        when(registro.guardarIntercambio(any(), anyString(), any(), any(), anyString(), any(), anyInt()))
            .thenThrow(new DataIntegrityViolationException("uk_conversaciones_identificador_sesion"))
            .thenReturn(bot);

        assertEquals(bot, chatService.enviarMensaje(visitante, "Hola", null));
        verify(registro, times(2)).guardarIntercambio(any(), anyString(), any(), any(), anyString(), any(), anyInt());
    }

    // HU-CHA-011: una pregunta que MotorRespuestas escala es una "pregunta
    // no cubierta" y debe registrarse como brecha de conocimiento.
    @Test
    void enviarMensaje_registraBrecha_cuandoLaRespuestaSeEscala() {
        registroDevuelveLaRespuesta();
        when(motorRespuestas.generarRespuesta(anyString()))
            .thenReturn(ResultadoMotor.escalado("No estoy seguro de haber entendido tu consulta.", List.of("Registro")));

        Mensaje respuesta = chatService.enviarMensaje(visitante, "como hago un portal interdimensional", null);

        verify(brechaConocimientoService).registrarEscalamiento("como hago un portal interdimensional");
        assertTrue(respuesta.getContenido().contains("Preguntas relacionadas que podrían ayudarte: Registro"));
        assertTrue(respuesta.getEscalado());
        assertNull(respuesta.getTemaClave());
    }

    @Test
    void enviarMensaje_noRegistraBrecha_cuandoLaRespuestaNoSeEscala() {
        registroDevuelveLaRespuesta();
        when(motorRespuestas.generarRespuesta(anyString()))
            .thenReturn(ResultadoMotor.deTema("Respuesta de prueba", Categoria.CUENTA_Y_REGISTRO,
                TipoRespuesta.PASO_A_PASO, "clave-registro"));

        Mensaje respuesta = chatService.enviarMensaje(visitante, "como me registro", null);

        verifyNoInteractions(brechaConocimientoService);
        assertEquals("clave-registro", respuesta.getTemaClave());
        assertEquals(Categoria.CUENTA_Y_REGISTRO, respuesta.getCategoria());
        assertFalse(respuesta.getEscalado());
        assertTrue(respuesta.getTiempoRespuestaMs() >= 0);
    }

    // HU-CHA-011: si registrar la brecha falla, el usuario igual recibe su
    // respuesta. El chat debe funcionar 24/7 (HU-CHA-001).
    @Test
    void enviarMensaje_respondeIgual_cuandoRegistrarLaBrechaFalla() {
        registroDevuelveLaRespuesta();
        when(motorRespuestas.generarRespuesta(anyString()))
            .thenReturn(ResultadoMotor.escalado("No estoy seguro de haber entendido tu consulta.", List.of()));
        doThrow(new RuntimeException("choque con el indice unico"))
            .when(brechaConocimientoService).registrarEscalamiento(anyString());

        Mensaje respuesta = chatService.enviarMensaje(visitante, "pregunta rara", null);

        assertEquals(Remitente.BOT, respuesta.getRemitente());
    }

    @Test
    void obtenerHistorial_devuelveListaVacia_cuandoNoExisteConversacionParaEsaIdentidad() {
        when(conversacionRepository.findByIdentificadorSesion(visitante.claveDeConversacion())).thenReturn(Optional.empty());

        assertTrue(chatService.obtenerHistorial(visitante).isEmpty());
    }

    @Test
    void obtenerHistorial_devuelveLosMensajesDeLaConversacion_cuandoExiste() {
        UUID idConversacion = UUID.randomUUID();
        Conversacion conversacion = mock(Conversacion.class);
        when(conversacion.getId()).thenReturn(idConversacion);
        Mensaje mensaje = new Mensaje(conversacion, Remitente.USUARIO, "Hola", null);
        when(conversacionRepository.findByIdentificadorSesion(UID.toString())).thenReturn(Optional.of(conversacion));
        when(mensajeRepository.findByConversacionIdOrderByFechaEnvioAsc(idConversacion)).thenReturn(List.of(mensaje));

        List<Mensaje> historial = chatService.obtenerHistorial(usuario);

        assertEquals(1, historial.size());
        assertEquals("Hola", historial.get(0).getContenido());
    }

    @Test
    void limpiarHistorial_borraLosMensajes_cuandoExisteConversacion() {
        UUID idConversacion = UUID.randomUUID();
        Conversacion conversacion = mock(Conversacion.class);
        when(conversacion.getId()).thenReturn(idConversacion);
        when(conversacionRepository.findByIdentificadorSesion(visitante.claveDeConversacion()))
            .thenReturn(Optional.of(conversacion));

        chatService.limpiarHistorial(visitante);

        verify(mensajeRepository).deleteByConversacionId(idConversacion);
    }

    @Test
    void limpiarHistorial_noHaceNada_cuandoNoExisteConversacion() {
        when(conversacionRepository.findByIdentificadorSesion(anyString())).thenReturn(Optional.empty());

        chatService.limpiarHistorial(visitante);

        verify(mensajeRepository, never()).deleteByConversacionId(any());
    }
}
