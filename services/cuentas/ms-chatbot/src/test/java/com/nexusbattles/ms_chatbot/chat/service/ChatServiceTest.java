package com.nexusbattles.ms_chatbot.chat.service;

import com.nexusbattles.ms_chatbot.chat.consultas.MotorConsultasAsistidas;
import com.nexusbattles.ms_chatbot.chat.enriquecido.RespuestaEnriquecida;
import com.nexusbattles.ms_chatbot.chat.enriquecido.VistaDelChat;
import com.nexusbattles.ms_chatbot.chat.entrada.InyeccionDetectada;
import com.nexusbattles.ms_chatbot.chat.entrada.RevisionDeEntrada;
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
import com.nexusbattles.ms_chatbot.chat.preferencias.IdiomaPreferido;
import com.nexusbattles.ms_chatbot.chat.preferencias.NivelDeDetalle;
import com.nexusbattles.ms_chatbot.chat.preferencias.PreferenciasDeRespuesta;
import com.nexusbattles.ms_chatbot.chat.preferencias.PreferenciasService;
import com.nexusbattles.ms_chatbot.chat.privacidad.RedactorDeDatosSensibles;
import com.nexusbattles.ms_chatbot.chat.repository.ConversacionRepository;
import com.nexusbattles.ms_chatbot.chat.repository.MensajeRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

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
import static org.mockito.ArgumentMatchers.anyBoolean;
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
    @Mock
    private PreferenciasService preferenciasService;

    private ChatService chatService;

    private static final UUID UID = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private final IdentidadDelChat usuario = IdentidadDelChat.usuario(UID, "token-de-prueba");
    private final IdentidadDelChat visitante = IdentidadDelChat.visitante(
        new SesionAnonima("huella", Instant.parse("2026-10-01T10:00:00Z"), Duration.ofHours(24)));

    @BeforeEach
    void configurar() {
        chatService = new ChatService(conversacionRepository, mensajeRepository, motorRespuestas,
            motorConsultasAsistidas, brechaConocimientoService, limitador, moderacion, registro, preferenciasService,
            new RevisionDeEntrada(new RedactorDeDatosSensibles(), new SimpleMeterRegistry()));
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

    // 1.4.0 (7.4.8): la contrasena y la tarjeta no llegan a la lista negra, ni
    // a los motores, ni a la brecha, ni a la base; la respuesta avisa que se taparon.
    @Test
    void enviarMensaje_conDatosSensibles_soloCirculaElTextoRedactadoYSeAvisa() {
        registroDevuelveLaRespuesta();
        when(motorRespuestas.generarRespuesta(anyString()))
            .thenReturn(ResultadoMotor.escalado("No estoy seguro.", List.of()));

        Mensaje respuesta = chatService.enviarMensaje(visitante,
            "mi contraseña es Hunter2! y mi tarjeta 4111 1111 1111 1111 no funciona", null);

        org.mockito.ArgumentCaptor<String> pregunta = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(moderacion).verificar(pregunta.capture());
        String redactada = pregunta.getValue();
        assertFalse(redactada.contains("Hunter2!"));
        assertFalse(redactada.contains("4111 1111 1111 1111"));
        verify(motorRespuestas).generarRespuesta(redactada);
        verify(brechaConocimientoService).registrarEscalamiento(redactada);
        verify(registro).guardarIntercambio(eq(visitante), eq(redactada), any(), any(), anyString(), any(), anyInt());
        assertTrue(respuesta.getContenido().startsWith(ChatService.AVISO_DATO_TAPADO));
    }

    // 1.4.0 (7.4.8): un intento de inyeccion no se modera, no se responde ni se guarda.
    @Test
    void enviarMensaje_conInyeccion_noProcesaNiGuardaNada() {
        assertThrows(InyeccionDetectada.class,
            () -> chatService.enviarMensaje(usuario, "' OR '1'='1", null));

        verifyNoInteractions(moderacion, motorRespuestas, motorConsultasAsistidas, registro, brechaConocimientoService);
    }

    // 1.4.0 (7.4.8): tambien cuenta lo que escribe cada direccion, sumando sus sesiones.
    @Test
    void enviarMensaje_conOrigen_limitaTambienPorDireccion() {
        registroDevuelveLaRespuesta();
        when(motorRespuestas.generarRespuesta(anyString()))
            .thenReturn(ResultadoMotor.deTema("Respuesta de prueba", Categoria.FAQ_GENERAL, TipoRespuesta.DIRECTA));

        chatService.enviarMensaje(visitante, "Hola", null, null, "203.0.113.7");

        verify(limitador).exigir(LimitadorDeFrecuencia.Regla.MENSAJES, visitante.claveDeLimite());
        verify(limitador).exigir(LimitadorDeFrecuencia.Regla.MENSAJES_POR_ORIGEN, "203.0.113.7");
    }

    // Un mensaje que solo traia caracteres de control no llega a ningun lado.
    @Test
    void enviarMensaje_soloCaracteresDeControl_responde400SinGuardar() {
        org.springframework.web.server.ResponseStatusException error = assertThrows(
            org.springframework.web.server.ResponseStatusException.class,
            () -> chatService.enviarMensaje(visitante, "\u0000\u200B", null));

        assertEquals(400, error.getStatusCode().value());
        verifyNoInteractions(moderacion, motorRespuestas, registro);
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
        when(motorConsultasAsistidas.generarRespuesta("cual es mi inventario", "token-de-prueba", UID.toString(),
            IdiomaPreferido.AUTOMATICO))
            .thenReturn(Optional.of(ResultadoMotor.deTema("Tienes 3 elementos en tu inventario.", null, TipoRespuesta.CONTEXTUAL)));

        Mensaje respuesta = chatService.enviarMensaje(usuario, "cual es mi inventario", null);

        verifyNoInteractions(motorRespuestas);
        assertTrue(respuesta.getContenido().contains("Tienes 3 elementos en tu inventario."));
    }

    @Test
    void usuarioAutenticadoSinIntencionDeCuenta_caeEnLaBaseDeConocimiento() {
        registroDevuelveLaRespuesta();
        when(motorConsultasAsistidas.generarRespuesta(anyString(), anyString(), anyString(), any()))
            .thenReturn(Optional.empty());
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


    // 1.3.5: historial por paginas.
    @Test
    void obtenerHistorialPaginado_sinCursor_devuelveLosUltimosEnOrdenCronologico() {
        UUID idConversacion = UUID.randomUUID();
        Conversacion conversacion = mock(Conversacion.class);
        when(conversacion.getId()).thenReturn(idConversacion);
        Mensaje viejo = new Mensaje(conversacion, Remitente.USUARIO, "viejo", null);
        Mensaje nuevo = new Mensaje(conversacion, Remitente.BOT, "nuevo", null);
        when(conversacionRepository.findByIdentificadorSesion(UID.toString())).thenReturn(Optional.of(conversacion));
        when(mensajeRepository.findByConversacionIdOrderByFechaEnvioDescIdDesc(idConversacion, PageRequest.of(0, 2)))
            .thenReturn(List.of(nuevo, viejo));

        List<Mensaje> pagina = chatService.obtenerHistorial(usuario, null, 2);

        assertEquals(List.of("viejo", "nuevo"), pagina.stream().map(Mensaje::getContenido).toList());
    }

    @Test
    void obtenerHistorialPaginado_conCursor_traeLosAnterioresAlCursor() {
        UUID idConversacion = UUID.randomUUID();
        UUID idCursor = UUID.randomUUID();
        Conversacion conversacion = mock(Conversacion.class);
        when(conversacion.getId()).thenReturn(idConversacion);
        Mensaje cursor = new Mensaje(conversacion, Remitente.USUARIO, "cursor", null,
            Instant.parse("2026-09-28T10:00:00Z"));
        Mensaje anterior = new Mensaje(conversacion, Remitente.BOT, "anterior", null,
            Instant.parse("2026-09-28T09:59:00Z"));
        when(conversacionRepository.findByIdentificadorSesion(UID.toString())).thenReturn(Optional.of(conversacion));
        when(mensajeRepository.findByIdAndConversacionId(idCursor, idConversacion)).thenReturn(Optional.of(cursor));
        when(mensajeRepository.buscarAnteriores(idConversacion, Instant.parse("2026-09-28T10:00:00Z"), idCursor,
            PageRequest.of(0, 50))).thenReturn(List.of(anterior));

        List<Mensaje> pagina = chatService.obtenerHistorial(usuario, idCursor, 50);

        assertEquals(List.of("anterior"), pagina.stream().map(Mensaje::getContenido).toList());
    }

    @Test
    void obtenerHistorialPaginado_conCursorDeOtraConversacion_devuelveVacioSinBuscarMas() {
        UUID idConversacion = UUID.randomUUID();
        Conversacion conversacion = mock(Conversacion.class);
        when(conversacion.getId()).thenReturn(idConversacion);
        when(conversacionRepository.findByIdentificadorSesion(UID.toString())).thenReturn(Optional.of(conversacion));
        when(mensajeRepository.findByIdAndConversacionId(any(), eq(idConversacion))).thenReturn(Optional.empty());

        assertTrue(chatService.obtenerHistorial(usuario, UUID.randomUUID(), 10).isEmpty());

        verify(mensajeRepository, never()).buscarAnteriores(any(), any(), any(), any());
    }

    @Test
    void obtenerHistorialPaginado_sinConversacion_devuelveVacio() {
        when(conversacionRepository.findByIdentificadorSesion(visitante.claveDeConversacion())).thenReturn(Optional.empty());

        assertTrue(chatService.obtenerHistorial(visitante, null, 20).isEmpty());

        verifyNoInteractions(mensajeRepository);
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

    // 1.3.4: con vista, la base responde sabiendolo; y lo enriquecido llega
    // tal cual al registro para guardarse con la respuesta.
    @Test
    void enviarMensaje_conVista_laUsaYGuardaLoEnriquecido() {
        registroDevuelveLaRespuesta();
        RespuestaEnriquecida enriquecida = new RespuestaEnriquecida(List.of(), List.of(), List.of(),
            List.of("Otra"), true);
        when(motorRespuestas.generarRespuesta("algo raro", VistaDelChat.SUBASTAS))
            .thenReturn(ResultadoMotor.escalado("No entendi", List.of()).conEnriquecido(enriquecida));

        chatService.enviarMensaje(visitante, "algo raro", null, VistaDelChat.SUBASTAS);

        verify(registro).guardarIntercambio(any(), eq("algo raro"), any(), any(), anyString(),
            org.mockito.ArgumentMatchers.argThat(r -> r.enriquecido() == enriquecida), anyInt());
        verify(motorRespuestas, never()).generarRespuesta(anyString());
    }


    // 1.3.6: con preferencias propias, la base responde con ellas.
    @Test
    void enviarMensaje_conPreferenciasPropias_laBaseRespondeConEllas() {
        registroDevuelveLaRespuesta();
        PreferenciasDeRespuesta breveEnIngles = new PreferenciasDeRespuesta(IdiomaPreferido.EN, NivelDeDetalle.BREVE);
        when(preferenciasService.de(visitante)).thenReturn(breveEnIngles);
        when(motorRespuestas.generarRespuesta("torneo", null, breveEnIngles))
            .thenReturn(ResultadoMotor.deTema("Short.", Categoria.MODALIDAD_JUEGO, TipoRespuesta.DIRECTA));

        Mensaje respuesta = chatService.enviarMensaje(visitante, "torneo", null);

        assertEquals("Short.", respuesta.getContenido());
        verify(motorRespuestas, never()).generarRespuesta(anyString());
    }

    // 1.5.0: las consultas en vivo responden en el idioma preferido, y las
    // preferencias se leen una sola vez por mensaje.
    @Test
    void usuarioConPreferenciaEnIngles_laConsultaEnVivoLaRecibeYSeLeeUnaVez() {
        registroDevuelveLaRespuesta();
        when(preferenciasService.de(usuario))
            .thenReturn(new PreferenciasDeRespuesta(IdiomaPreferido.EN, NivelDeDetalle.NORMAL));
        when(motorConsultasAsistidas.generarRespuesta("que tengo en mi inventario", "token-de-prueba", UID.toString(),
            IdiomaPreferido.EN))
            .thenReturn(Optional.empty());
        when(motorRespuestas.generarRespuesta(eq("que tengo en mi inventario"), any(), any()))
            .thenReturn(ResultadoMotor.deTema("Inventory.", Categoria.FAQ_GENERAL, TipoRespuesta.DIRECTA));

        chatService.enviarMensaje(usuario, "que tengo en mi inventario", null);

        verify(preferenciasService, times(1)).de(usuario);
    }

    // 1.5.0 (7.4.8 + 7.4.2): el aviso de un dato tapado va en el idioma de la respuesta.
    @Test
    void enviarMensaje_conDatoSensibleYRespuestaEnIngles_avisaEnIngles() {
        registroDevuelveLaRespuesta();
        when(motorRespuestas.generarRespuesta(anyString()))
            .thenReturn(ResultadoMotor.deTema("To log in...", Categoria.CUENTA_Y_REGISTRO, TipoRespuesta.DIRECTA)
                .enIngles(true));

        Mensaje respuesta = chatService.enviarMensaje(visitante, "my password is Hunter2! how do I log in", null);

        assertTrue(respuesta.getContenido().startsWith(ChatService.AVISO_DATO_TAPADO_EN));
        assertFalse(respuesta.getContenido().contains("Hunter2"));
    }

    @Test
    void enviarMensaje_escaladoEnIngles_anadeLasPreguntasRelacionadasEnIngles() {
        registroDevuelveLaRespuesta();
        when(motorRespuestas.generarRespuesta(anyString()))
            .thenReturn(ResultadoMotor.escalado("I'm not sure.", List.of("Registro")).enIngles(true));

        Mensaje respuesta = chatService.enviarMensaje(visitante, "how do I fly", null);

        assertTrue(respuesta.getContenido().endsWith(ChatService.RELACIONADAS_EN + "Registro."));
    }

    // ---- 1.5.0: seguimiento de la respuesta anterior (gestion de dialogo) ----

    private Mensaje respuestaAnteriorDelBot(String temaClave) {
        Mensaje anterior = new Mensaje(mock(Conversacion.class), Remitente.BOT, "Respuesta anterior", null);
        anterior.registrarDatosDeRespuesta(temaClave, temaClave == null ? null : Categoria.CUENTA_Y_REGISTRO,
            false, 12);
        return anterior;
    }

    private void laRespuestaAnteriorFue(IdentidadDelChat identidad, String temaClave) {
        when(mensajeRepository.findFirstByConversacionIdentificadorSesionAndRemitenteOrderByFechaEnvioDesc(
            identidad.claveDeConversacion(), Remitente.BOT))
            .thenReturn(Optional.of(respuestaAnteriorDelBot(temaClave)));
    }

    @Test
    void seguimiento_masDetalle_respondeElTemaAnteriorConDetalleSinBuscarOtraVez() {
        registroDevuelveLaRespuesta();
        laRespuestaAnteriorFue(visitante, "crear-cuenta");
        when(motorRespuestas.responderTema("crear-cuenta",
            new PreferenciasDeRespuesta(IdiomaPreferido.AUTOMATICO, NivelDeDetalle.DETALLADO), false))
            .thenReturn(Optional.of(ResultadoMotor.deTema("Todo el detalle.", Categoria.CUENTA_Y_REGISTRO,
                TipoRespuesta.PASO_A_PASO, "crear-cuenta")));

        Mensaje respuesta = chatService.enviarMensaje(visitante, "Más detalle, por favor", null);

        assertEquals("Todo el detalle.", respuesta.getContenido());
        assertEquals("crear-cuenta", respuesta.getTemaClave());
        verify(motorRespuestas, never()).generarRespuesta(anyString());
        verifyNoInteractions(brechaConocimientoService);
    }

    @Test
    void seguimiento_enIngles_respondeElTemaAnteriorEnIngles() {
        registroDevuelveLaRespuesta();
        laRespuestaAnteriorFue(usuario, "crear-cuenta");
        when(motorRespuestas.responderTema("crear-cuenta",
            new PreferenciasDeRespuesta(IdiomaPreferido.EN, NivelDeDetalle.NORMAL), true))
            .thenReturn(Optional.of(ResultadoMotor.deTema("To create your account...", Categoria.CUENTA_Y_REGISTRO,
                TipoRespuesta.PASO_A_PASO, "crear-cuenta").enIngles(true)));

        Mensaje respuesta = chatService.enviarMensaje(usuario, "in english please", null);

        assertEquals("To create your account...", respuesta.getContenido());
        verifyNoInteractions(motorConsultasAsistidas);
    }

    @Test
    void seguimiento_enEspanolYRepetir_usanLasPreferenciasQueTocan() {
        registroDevuelveLaRespuesta();
        laRespuestaAnteriorFue(visitante, "crear-cuenta");
        when(motorRespuestas.responderTema(eq("crear-cuenta"), any(), anyBoolean()))
            .thenReturn(Optional.of(ResultadoMotor.deTema("Otra vez.", Categoria.CUENTA_Y_REGISTRO,
                TipoRespuesta.DIRECTA, "crear-cuenta")));

        chatService.enviarMensaje(visitante, "en español", null);
        chatService.enviarMensaje(visitante, "repítelo", null);

        verify(motorRespuestas).responderTema("crear-cuenta",
            new PreferenciasDeRespuesta(IdiomaPreferido.ES, NivelDeDetalle.NORMAL), false);
        verify(motorRespuestas).responderTema("crear-cuenta", PreferenciasDeRespuesta.POR_DEFECTO, false);
    }

    // «No me sirvio» cuenta como escalamiento (se ofrece soporte) pero no es
    // una pregunta sin cubrir: no va a las brechas de conocimiento.
    @Test
    void seguimiento_noMeSirvio_ofreceSoporteYNoRegistraBrecha() {
        registroDevuelveLaRespuesta();
        laRespuestaAnteriorFue(visitante, "crear-cuenta");
        RespuestaEnriquecida conSoporte = new RespuestaEnriquecida(List.of(), List.of(), List.of(),
            List.of("Recuperar mi contraseña"), true);
        when(motorRespuestas.relacionadosCon("crear-cuenta", ChatService.NO_SIRVIO, false))
            .thenReturn(Optional.of(new ResultadoMotor(ChatService.NO_SIRVIO, Categoria.CUENTA_Y_REGISTRO,
                TipoRespuesta.CONTEXTUAL, true, List.of(), null, conSoporte, false)));

        Mensaje respuesta = chatService.enviarMensaje(visitante, "no me sirvió", null);

        assertEquals(ChatService.NO_SIRVIO, respuesta.getContenido());
        assertTrue(respuesta.getEscalado());
        verifyNoInteractions(brechaConocimientoService);
    }

    @Test
    void seguimiento_noEntendiTrasUnEscalamiento_ofreceSoporteEnSuIdioma() {
        registroDevuelveLaRespuesta();
        laRespuestaAnteriorFue(visitante, null);

        Mensaje respuesta = chatService.enviarMensaje(visitante, "I don't understand", null);

        assertEquals(ChatService.NO_SIRVIO_EN, respuesta.getContenido());
        assertTrue(respuesta.getEscalado());
        verify(registro).guardarIntercambio(any(), anyString(), any(), any(), anyString(),
            org.mockito.ArgumentMatchers.argThat(r -> r.enriquecido().ofrecerSoporteHumano()), anyInt());
        verifyNoInteractions(motorRespuestas, brechaConocimientoService);
    }

    // Tras una consulta en vivo (sin tema de la base) no se adivina: se pide
    // la pregunta completa, y no se consulta nada en nombre del jugador.
    @Test
    void seguimiento_trasUnaConsultaEnVivo_pideLaPreguntaCompleta() {
        registroDevuelveLaRespuesta();
        laRespuestaAnteriorFue(usuario, null);

        Mensaje respuesta = chatService.enviarMensaje(usuario, "repite", null);

        assertEquals(ChatService.SIN_TEMA_ANTERIOR, respuesta.getContenido());
        assertFalse(respuesta.getEscalado());
        verifyNoInteractions(motorRespuestas, motorConsultasAsistidas, brechaConocimientoService);
    }

    @Test
    void seguimiento_conElTemaAnteriorYaRetirado_pideLaPreguntaCompletaEnSuIdioma() {
        registroDevuelveLaRespuesta();
        laRespuestaAnteriorFue(visitante, "tema-retirado");
        when(motorRespuestas.responderTema(eq("tema-retirado"), any(), anyBoolean())).thenReturn(Optional.empty());

        assertEquals(ChatService.SIN_TEMA_ANTERIOR_EN,
            chatService.enviarMensaje(visitante, "tell me more", null).getContenido());
    }

    // Sin respuesta anterior (primer mensaje o historial limpio), la frase es
    // una pregunta mas.
    @Test
    void seguimiento_sinRespuestaAnterior_sigueElCaminoNormal() {
        registroDevuelveLaRespuesta();
        when(mensajeRepository.findFirstByConversacionIdentificadorSesionAndRemitenteOrderByFechaEnvioDesc(
            visitante.claveDeConversacion(), Remitente.BOT)).thenReturn(Optional.empty());
        when(motorRespuestas.generarRespuesta("más detalle"))
            .thenReturn(ResultadoMotor.escalado("No estoy seguro.", List.of()));

        chatService.enviarMensaje(visitante, "más detalle", null);

        verify(motorRespuestas).generarRespuesta("más detalle");
        verify(motorRespuestas, never()).responderTema(any(), any(), anyBoolean());
    }

    // Una pregunta nueva que empieza como un seguimiento no lo es.
    @Test
    void preguntaNuevaQueEmpiezaComoSeguimiento_noBuscaLaRespuestaAnterior() {
        registroDevuelveLaRespuesta();
        when(motorRespuestas.generarRespuesta(anyString()))
            .thenReturn(ResultadoMotor.deTema("Para publicar...", Categoria.SUBASTA_Y_COMERCIO, TipoRespuesta.PASO_A_PASO,
                "publicar-subasta"));

        chatService.enviarMensaje(visitante, "explícame mejor cómo publicar una subasta", null);
        chatService.enviarMensaje(visitante, "quiero jugar de nuevo", null);

        verify(mensajeRepository, never())
            .findFirstByConversacionIdentificadorSesionAndRemitenteOrderByFechaEnvioDesc(anyString(), any());
    }

    // Si la lectura falla, se responde igual, con las de por defecto.
    @Test
    void enviarMensaje_siNoSePuedenLeerLasPreferencias_respondeConLasDePorDefecto() {
        registroDevuelveLaRespuesta();
        when(preferenciasService.de(visitante)).thenThrow(new IllegalStateException("base caida"));
        when(motorRespuestas.generarRespuesta("torneo"))
            .thenReturn(ResultadoMotor.deTema("Normal.", Categoria.MODALIDAD_JUEGO, TipoRespuesta.DIRECTA));

        assertEquals("Normal.", chatService.enviarMensaje(visitante, "torneo", null).getContenido());
    }
}
