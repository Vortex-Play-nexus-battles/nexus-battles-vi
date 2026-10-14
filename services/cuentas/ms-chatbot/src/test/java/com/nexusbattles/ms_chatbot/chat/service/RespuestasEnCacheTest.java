package com.nexusbattles.ms_chatbot.chat.service;

import com.nexusbattles.ms_chatbot.chat.consultas.MotorConsultasAsistidas;
import com.nexusbattles.ms_chatbot.chat.entrada.RevisionDeEntrada;
import com.nexusbattles.ms_chatbot.chat.identidad.IdentidadDelChat;
import com.nexusbattles.ms_chatbot.chat.identidad.SesionAnonima;
import com.nexusbattles.ms_chatbot.chat.limite.LimitadorDeFrecuencia;
import com.nexusbattles.ms_chatbot.chat.model.Conversacion;
import com.nexusbattles.ms_chatbot.chat.model.Mensaje;
import com.nexusbattles.ms_chatbot.chat.model.Remitente;
import com.nexusbattles.ms_chatbot.chat.moderacion.ModeracionDeContenido;
import com.nexusbattles.ms_chatbot.chat.motor.BaseEnProduccion;
import com.nexusbattles.ms_chatbot.chat.motor.CacheDeRespuestas;
import com.nexusbattles.ms_chatbot.chat.motor.MotorRespuestas;
import com.nexusbattles.ms_chatbot.chat.motor.ResultadoMotor;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import com.nexusbattles.ms_chatbot.chat.preferencias.IdiomaPreferido;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 7.4.9 (ms-chatbot.yaml 1.9.0): la cache de respuestas vista desde
 * {@link ChatService}, con el motor de verdad. Solo guarda lo que sale de la
 * base de conocimiento; las consultas en vivo (privadas del jugador) y los
 * seguimientos nunca entran, y una pregunta escalada que sale de la cache se
 * sigue registrando como brecha.
 */
@ExtendWith(MockitoExtension.class)
class RespuestasEnCacheTest {

    private static final UUID PRODUCCION = UUID.fromString("00000000-0000-4000-8000-0000000000aa");
    private static final UUID UID = UUID.fromString("11111111-2222-4333-8444-555555555555");

    @Mock
    private ConversacionRepository conversacionRepository;
    @Mock
    private MensajeRepository mensajeRepository;
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
    @Mock
    private com.nexusbattles.ms_chatbot.chat.catalogo.ConsultaDeCatalogo consultaDeCatalogo;

    private final SimpleMeterRegistry metricas = new SimpleMeterRegistry();
    private CacheDeRespuestas cache;
    private ChatService chatService;

    private final IdentidadDelChat jugador = IdentidadDelChat.usuario(UID, "token-del-jugador");
    private final IdentidadDelChat visitante = IdentidadDelChat.visitante(
        new SesionAnonima("huella", Instant.parse("2026-10-14T10:00:00Z"), Duration.ofHours(24)));

    private static TemaConocimiento tema(String clave, Categoria categoria, String titulo, String variantes,
                                         String respuesta) {
        return new TemaConocimiento(null, clave, categoria, TipoRespuesta.DIRECTA, titulo, variantes, null,
            respuesta, null, 0, true);
    }

    @BeforeEach
    void configurar() {
        List<TemaConocimiento> temas = List.of(
            tema("publicar-subasta", Categoria.SUBASTA_Y_COMERCIO, "Cómo publicar una subasta",
                "publicar subasta, vender en subasta", "Desde la sección Subasta, con «Publicar»."),
            tema("crear-cuenta", Categoria.CUENTA_Y_REGISTRO, "Cómo crear una cuenta",
                "crear cuenta, registrarme", "Desde «Crear cuenta» en la portada."));
        cache = new CacheDeRespuestas(500, 30, Clock.systemUTC(), metricas);
        MotorRespuestas motor = new MotorRespuestas(() -> new BaseEnProduccion(PRODUCCION, temas), cache);
        chatService = new ChatService(conversacionRepository, mensajeRepository, motor, motorConsultasAsistidas,
            brechaConocimientoService, limitador, moderacion, registro, preferenciasService,
            new RevisionDeEntrada(new RedactorDeDatosSensibles(), metricas), consultaDeCatalogo,
            org.mockito.Mockito.mock(com.nexusbattles.ms_chatbot.chat.consultas.ConsultaDeSubastas.class),
            new com.nexusbattles.ms_chatbot.chat.consultas.NavegacionPublica(),
            org.mockito.Mockito.mock(com.nexusbattles.ms_chatbot.chat.soporte.TicketSoporteService.class));
        lenient().when(preferenciasService.efectivasDe(any())).thenReturn(PreferenciasDeRespuesta.POR_DEFECTO);
        lenient().when(registro.guardarIntercambio(any(), anyString(), any(), any(), anyString(), any(), anyInt()))
            .thenAnswer(inv -> {
                ResultadoMotor resultado = inv.getArgument(5);
                Mensaje bot = new Mensaje(mock(Conversacion.class), Remitente.BOT, inv.getArgument(4), null);
                bot.registrarDatosDeRespuesta(resultado.temaClave(), resultado.categoria(),
                    resultado.requiereEscalamiento(), inv.getArgument(6));
                return bot;
            });
    }

    private double aciertos() {
        return metricas.counter("chatbot.cache.respuestas.aciertos").count();
    }

    @Test
    void unaPreguntaDeLaBaseRepetidaSeRespondeDesdeLaCacheYSeGuardaComoSiempre() {
        Mensaje primera = chatService.enviarMensaje(visitante, "¿Cómo publicar una subasta?", null);
        Mensaje segunda = chatService.enviarMensaje(visitante, "como publicar una SUBASTA", null);

        assertThat(segunda.getContenido()).isEqualTo(primera.getContenido()).contains("Publicar");
        assertThat(segunda.getTemaClave()).isEqualTo("publicar-subasta");
        assertThat(cache.tamano()).isEqualTo(1);
        assertThat(aciertos()).isEqualTo(1.0);
        // Cada pregunta se guarda en el historial y cuenta en las analiticas.
        verify(registro, times(2)).guardarIntercambio(eq(visitante), anyString(), any(), any(), anyString(), any(),
            anyInt());
    }

    // ChatService registra la brecha a partir del resultado, venga o no de la cache.
    @Test
    void unaPreguntaEscaladaQueSaleDeLaCacheSeSigueRegistrandoComoBrecha() {
        chatService.enviarMensaje(visitante, "quiero cambiar el color del dragon", null);
        chatService.enviarMensaje(visitante, "Quiero cambiar el color del dragón", null);

        assertThat(aciertos()).isEqualTo(1.0);
        verify(brechaConocimientoService).registrarEscalamiento("quiero cambiar el color del dragon");
        verify(brechaConocimientoService).registrarEscalamiento("Quiero cambiar el color del dragón");
    }

    // Lo privado del jugador (inventario, subastas, creditos...) nunca se guarda.
    @Test
    void unaConsultaEnVivoDelJugadorNuncaEntraEnLaCache() {
        when(motorConsultasAsistidas.generarRespuesta(anyString(), eq("token-del-jugador"), eq(UID.toString()),
            eq(IdiomaPreferido.AUTOMATICO), any()))
            .thenReturn(Optional.of(ResultadoMotor.deTema("Tienes 3 pociones y la Espada del Alba.",
                Categoria.PRODUCTO, TipoRespuesta.DIRECTA)));

        chatService.enviarMensaje(jugador, "que tengo en mi inventario", null);
        chatService.enviarMensaje(jugador, "que tengo en mi inventario", null);

        assertThat(cache.tamano()).isZero();
        assertThat(aciertos()).isZero();
        assertThat(metricas.counter("chatbot.cache.respuestas.fallos").count()).isZero();
        verify(motorConsultasAsistidas, times(2)).generarRespuesta(anyString(), anyString(), anyString(), any(), any());
    }

    // «mas detalle» responde otra vez el tema anterior: es un seguimiento, no una pregunta a la base.
    @Test
    void unSeguimientoDeLaRespuestaAnteriorNoEntraEnLaCache() {
        Mensaje anterior = new Mensaje(mock(Conversacion.class), Remitente.BOT, "Desde «Crear cuenta».", null);
        anterior.registrarDatosDeRespuesta("crear-cuenta", Categoria.CUENTA_Y_REGISTRO, false, 5);
        when(mensajeRepository.findFirstByConversacionIdentificadorSesionAndRemitenteOrderByFechaEnvioDesc(
            visitante.claveDeConversacion(), Remitente.BOT)).thenReturn(Optional.of(anterior));

        Mensaje detalle = chatService.enviarMensaje(visitante, "más detalle, por favor", null);

        assertThat(detalle.getTemaClave()).isEqualTo("crear-cuenta");
        assertThat(cache.tamano()).isZero();
    }
}
