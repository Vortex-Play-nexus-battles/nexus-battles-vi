package com.nexusbattles.ms_chatbot.chat.service;

import com.nexusbattles.ms_chatbot.chat.consultas.MotorConsultasAsistidas;
import com.nexusbattles.ms_chatbot.chat.model.Conversacion;
import com.nexusbattles.ms_chatbot.chat.model.Mensaje;
import com.nexusbattles.ms_chatbot.chat.model.Remitente;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    private ChatService chatService;

    @BeforeEach
    void configurar() {
        chatService = new ChatService(conversacionRepository, mensajeRepository, motorRespuestas,
            motorConsultasAsistidas, brechaConocimientoService);
    }

    @Test
    void enviarMensaje_creaConversacionNueva_cuandoEsLaPrimeraVezDeEsaSesion() {
        String idSesion = "visitante-001";
        when(conversacionRepository.findByIdentificadorSesion(idSesion)).thenReturn(Optional.empty());
        when(conversacionRepository.save(any(Conversacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mensajeRepository.save(any(Mensaje.class))).thenAnswer(inv -> inv.getArgument(0));
        when(motorRespuestas.generarRespuesta(anyString()))
            .thenReturn(ResultadoMotor.deTema("Respuesta de prueba", Categoria.FAQ_GENERAL, TipoRespuesta.DIRECTA));

        Mensaje respuesta = chatService.enviarMensaje(idSesion, false, "Hola", null, null);

        verify(conversacionRepository).save(any(Conversacion.class));
        verify(mensajeRepository, times(2)).save(any(Mensaje.class)); // mensaje del usuario + respuesta del bot
        assertEquals(Remitente.BOT, respuesta.getRemitente());
        verifyNoInteractions(motorConsultasAsistidas);
    }

    @Test
    void enviarMensaje_reutilizaConversacionExistente_cuandoYaHayUnaParaEsaSesion() {
        String idSesion = "usuario-autenticado-123";
        Conversacion conversacionExistente = new Conversacion(idSesion, true);
        when(conversacionRepository.findByIdentificadorSesion(idSesion)).thenReturn(Optional.of(conversacionExistente));
        when(mensajeRepository.save(any(Mensaje.class))).thenAnswer(inv -> inv.getArgument(0));
        when(motorConsultasAsistidas.generarRespuesta(anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(motorRespuestas.generarRespuesta(anyString()))
            .thenReturn(ResultadoMotor.deTema("Respuesta de prueba", Categoria.FAQ_GENERAL, TipoRespuesta.DIRECTA));

        chatService.enviarMensaje(idSesion, true, "Segunda pregunta", null, "token-de-prueba");

        verify(conversacionRepository, never()).save(any(Conversacion.class));
        verify(mensajeRepository, times(2)).save(any(Mensaje.class));
    }

    // HU-CHA-008, tarea tecnica 4: un visitante (no autenticado) nunca debe
    // disparar el motor de consultas asistidas, ni siquiera cuando su
    // mensaje se parece a una pregunta de cuenta (ej. "mi inventario").
    // Esto evita que alguien sin JWT valido reciba, por error, datos que
    // solo deberian salir para el dueno autenticado de la cuenta.
    @Test
    void visitanteConMensajeQueParecePreguntaDeCuenta_noConsultaElMotorAsistido() {
        String idSesion = "visitante-004";
        when(conversacionRepository.findByIdentificadorSesion(idSesion)).thenReturn(Optional.empty());
        when(conversacionRepository.save(any(Conversacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mensajeRepository.save(any(Mensaje.class))).thenAnswer(inv -> inv.getArgument(0));
        when(motorRespuestas.generarRespuesta(anyString()))
            .thenReturn(ResultadoMotor.deTema("No tengo esa informacion sin que inicies sesion.", Categoria.FAQ_GENERAL, TipoRespuesta.DIRECTA));

        chatService.enviarMensaje(idSesion, false, "cual es mi inventario", null, null);

        verifyNoInteractions(motorConsultasAsistidas);
        verify(motorRespuestas).generarRespuesta("cual es mi inventario");
    }

    // Caso defensivo: si por alguna razon llega autenticado=true pero sin
    // token (no deberia pasar segun ChatController.resolverIdentidad, pero
    // el guard de ChatService existe justamente para este caso), tampoco se
    // debe consultar el motor asistido.
    @Test
    void usuarioAutenticadoSinTokenBearer_noConsultaElMotorAsistido() {
        String idSesion = "uid-sin-token";
        Conversacion conversacionExistente = new Conversacion(idSesion, true);
        when(conversacionRepository.findByIdentificadorSesion(idSesion)).thenReturn(Optional.of(conversacionExistente));
        when(mensajeRepository.save(any(Mensaje.class))).thenAnswer(inv -> inv.getArgument(0));
        when(motorRespuestas.generarRespuesta(anyString()))
            .thenReturn(ResultadoMotor.deTema("Respuesta de prueba", Categoria.FAQ_GENERAL, TipoRespuesta.DIRECTA));

        chatService.enviarMensaje(idSesion, true, "hola", null, null);

        verifyNoInteractions(motorConsultasAsistidas);
    }

    // Caso inverso: cuando el motor asistido SI reconoce la intencion de un
    // usuario autenticado, su respuesta manda y no se debe consultar
    // ademas el motor de conocimiento estatico.
    @Test
    void usuarioAutenticadoConRespuestaDelMotorAsistido_noConsultaMotorRespuestas() {
        String idSesion = "uid-999";
        Conversacion conversacionExistente = new Conversacion(idSesion, true);
        when(conversacionRepository.findByIdentificadorSesion(idSesion)).thenReturn(Optional.of(conversacionExistente));
        when(mensajeRepository.save(any(Mensaje.class))).thenAnswer(inv -> inv.getArgument(0));
        when(motorConsultasAsistidas.generarRespuesta(anyString(), anyString(), anyString()))
            .thenReturn(Optional.of(ResultadoMotor.deTema("Tienes 3 elementos en tu inventario.", null, TipoRespuesta.CONTEXTUAL)));

        Mensaje respuesta = chatService.enviarMensaje(idSesion, true, "cual es mi inventario", null, "token-de-prueba");

        verifyNoInteractions(motorRespuestas);
        assertTrue(respuesta.getContenido().contains("Tienes 3 elementos en tu inventario."));
    }

    // HU-CHA-011: una pregunta que MotorRespuestas escala es una "pregunta
    // no cubierta" y debe registrarse como brecha de conocimiento.
    @Test
    void enviarMensaje_registraBrecha_cuandoLaRespuestaSeEscala() {
        String idSesion = "visitante-005";
        when(conversacionRepository.findByIdentificadorSesion(idSesion)).thenReturn(Optional.empty());
        when(conversacionRepository.save(any(Conversacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mensajeRepository.save(any(Mensaje.class))).thenAnswer(inv -> inv.getArgument(0));
        when(motorRespuestas.generarRespuesta(anyString()))
            .thenReturn(ResultadoMotor.escalado("No estoy seguro de haber entendido tu consulta.", List.of()));

        chatService.enviarMensaje(idSesion, false, "como hago un portal interdimensional", null, null);

        verify(brechaConocimientoService).registrarEscalamiento("como hago un portal interdimensional");
    }

    // HU-CHA-011: una respuesta normal (no escalada) no es una brecha.
    @Test
    void enviarMensaje_noRegistraBrecha_cuandoLaRespuestaNoSeEscala() {
        String idSesion = "visitante-006";
        when(conversacionRepository.findByIdentificadorSesion(idSesion)).thenReturn(Optional.empty());
        when(conversacionRepository.save(any(Conversacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mensajeRepository.save(any(Mensaje.class))).thenAnswer(inv -> inv.getArgument(0));
        when(motorRespuestas.generarRespuesta(anyString()))
            .thenReturn(ResultadoMotor.deTema("Respuesta de prueba", Categoria.FAQ_GENERAL, TipoRespuesta.DIRECTA));

        chatService.enviarMensaje(idSesion, false, "como me registro", null, null);

        verifyNoInteractions(brechaConocimientoService);
    }

    // HU-CHA-011: si registrar la brecha falla, el usuario igual recibe su
    // respuesta. El chat debe funcionar 24/7 (HU-CHA-001); el contador de
    // brechas es secundario.
    @Test
    void enviarMensaje_respondeIgual_cuandoRegistrarLaBrechaFalla() {
        String idSesion = "visitante-007";
        when(conversacionRepository.findByIdentificadorSesion(idSesion)).thenReturn(Optional.empty());
        when(conversacionRepository.save(any(Conversacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mensajeRepository.save(any(Mensaje.class))).thenAnswer(inv -> inv.getArgument(0));
        when(motorRespuestas.generarRespuesta(anyString()))
            .thenReturn(ResultadoMotor.escalado("No estoy seguro de haber entendido tu consulta.", List.of()));
        doThrow(new RuntimeException("choque con el indice unico"))
            .when(brechaConocimientoService).registrarEscalamiento(anyString());

        Mensaje respuesta = chatService.enviarMensaje(idSesion, false, "pregunta rara", null, null);

        assertEquals(Remitente.BOT, respuesta.getRemitente());
        verify(mensajeRepository, times(2)).save(any(Mensaje.class));
    }

    @Test
    void obtenerHistorial_devuelveListaVacia_cuandoNoExisteConversacionParaEsaSesion() {
        when(conversacionRepository.findByIdentificadorSesion("desconocido")).thenReturn(Optional.empty());

        List<Mensaje> historial = chatService.obtenerHistorial("desconocido");

        assertTrue(historial.isEmpty());
    }

    @Test
    void obtenerHistorial_devuelveLosMensajesDeLaConversacion_cuandoExiste() {
        String idSesion = "visitante-002";
        UUID idConversacion = UUID.randomUUID();
        Conversacion conversacion = mock(Conversacion.class);
        when(conversacion.getId()).thenReturn(idConversacion);
        Mensaje mensaje = new Mensaje(conversacion, Remitente.USUARIO, "Hola", null);

        when(conversacionRepository.findByIdentificadorSesion(idSesion)).thenReturn(Optional.of(conversacion));
        when(mensajeRepository.findByConversacionIdOrderByFechaEnvioAsc(idConversacion))
            .thenReturn(List.of(mensaje));

        List<Mensaje> historial = chatService.obtenerHistorial(idSesion);

        assertEquals(1, historial.size());
        assertEquals("Hola", historial.get(0).getContenido());
    }

    @Test
    void limpiarHistorial_borraLosMensajes_cuandoExisteConversacion() {
        String idSesion = "visitante-003";
        UUID idConversacion = UUID.randomUUID();
        Conversacion conversacion = mock(Conversacion.class);
        when(conversacion.getId()).thenReturn(idConversacion);
        when(conversacionRepository.findByIdentificadorSesion(idSesion)).thenReturn(Optional.of(conversacion));

        chatService.limpiarHistorial(idSesion);

        verify(mensajeRepository).deleteByConversacionId(idConversacion);
    }

    @Test
    void limpiarHistorial_noHaceNada_cuandoNoExisteConversacion() {
        when(conversacionRepository.findByIdentificadorSesion("desconocido")).thenReturn(Optional.empty());

        chatService.limpiarHistorial("desconocido");

        verify(mensajeRepository, never()).deleteByConversacionId(any());
    }
}
