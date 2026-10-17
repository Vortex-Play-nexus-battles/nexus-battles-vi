package com.nexusbattles.ms_chatbot.chat.tiemporeal;

import com.nexusbattles.comun.seguridad.ConversorRolesJwt;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.ms_chatbot.chat.api.ManejadorErroresDelChat;
import com.nexusbattles.ms_chatbot.chat.api.MensajeResponse;
import com.nexusbattles.ms_chatbot.chat.enriquecido.VistaDelChat;
import com.nexusbattles.ms_chatbot.chat.entrada.DetectorDeInyeccion;
import com.nexusbattles.ms_chatbot.chat.entrada.InyeccionDetectada;
import com.nexusbattles.ms_chatbot.chat.identidad.IdentidadDelChat;
import com.nexusbattles.ms_chatbot.chat.identidad.ResolutorDeIdentidad;
import com.nexusbattles.ms_chatbot.chat.identidad.SesionAnonima;
import com.nexusbattles.ms_chatbot.chat.limite.LimiteDeFrecuenciaExcedido;
import com.nexusbattles.ms_chatbot.chat.model.Conversacion;
import com.nexusbattles.ms_chatbot.chat.model.Mensaje;
import com.nexusbattles.ms_chatbot.chat.model.Remitente;
import com.nexusbattles.ms_chatbot.chat.moderacion.ContenidoBloqueado;
import com.nexusbattles.ms_chatbot.chat.moderacion.ModeracionNoDisponible;
import com.nexusbattles.ms_chatbot.chat.service.ChatService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 7.4.9 (contracts/websocket/chatbot.yaml 1.0.0): una pregunta por el canal,
// el mismo camino que POST /chat/mensajes y sus respuestas solo a esa conexion.
@DisplayName("Chatbot · una pregunta por el canal en tiempo real (7.4.9)")
class ConversacionEnVivoTest {

    private static final UUID UID = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final ConversacionEnVivo.Destinatario ANA =
        new ConversacionEnVivo.Destinatario(UID.toString(), "sesion-ana");
    private static final long ESPERA_MS = 5000;

    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();
    private final ChatService chatService = mock(ChatService.class);
    private final ResolutorDeIdentidad resolutor = mock(ResolutorDeIdentidad.class);
    private final SimpMessagingTemplate mensajeria = mock(SimpMessagingTemplate.class);
    private final Map<String, Object> atributos = new ConcurrentHashMap<>();
    private final IdentidadDelChat jugador = IdentidadDelChat.usuario(UID, "token");
    private ConversacionEnVivo conversacion;

    @BeforeEach
    void configurar() {
        conversacion = new ConversacionEnVivo(chatService, resolutor, new ManejadorErroresDelChat(),
            emisor.decodificador(), new ConversorRolesJwt(), mensajeria, JsonMapper.builder().build(), 2);
        atributos.put(OrigenDelCanal.ORIGEN, "203.0.113.7");
        atributos.put(OrigenDelCanal.EN_VUELO, new AtomicInteger());
        when(resolutor.resolverOEmitir(any(), any(), any())).thenReturn(new ResolutorDeIdentidad.Resultado(jugador, null));
    }

    @AfterEach
    void cerrar() {
        conversacion.destroy();
    }

    private static byte[] json(String texto) {
        return texto.getBytes(StandardCharsets.UTF_8);
    }

    private static Mensaje respuestaDelBot(String texto) {
        return new Mensaje(mock(Conversacion.class), Remitente.BOT, texto, null);
    }

    /** Lo que se mando por el canal: destino, cuerpo y cabeceras. */
    private record Enviado(String usuario, String destino, Object cuerpo, SimpMessageHeaderAccessor cabeceras) {
        String nativa(String nombre) {
            return cabeceras.getFirstNativeHeader(nombre);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Enviado> enviados(int cuantos) {
        ArgumentCaptor<String> usuarios = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> destinos = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> cuerpos = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Map<String, Object>> cabeceras = ArgumentCaptor.forClass(Map.class);
        verify(mensajeria, timeout(ESPERA_MS).times(cuantos))
            .convertAndSendToUser(usuarios.capture(), destinos.capture(), cuerpos.capture(), cabeceras.capture());
        List<Enviado> todos = new ArrayList<>();
        for (int i = 0; i < cuantos; i++) {
            SimpMessageHeaderAccessor accesor = MessageHeaderAccessor.getAccessor(
                (MessageHeaders) cabeceras.getAllValues().get(i), SimpMessageHeaderAccessor.class);
            todos.add(new Enviado(usuarios.getAllValues().get(i), destinos.getAllValues().get(i),
                cuerpos.getAllValues().get(i), accesor));
        }
        return todos;
    }

    private ErrorDelCanal unicoError() {
        List<Enviado> enviados = enviados(1);
        assertThat(enviados.get(0).destino()).isEqualTo(DestinosDelCanal.COLA_ERRORES);
        return (ErrorDelCanal) enviados.get(0).cuerpo();
    }

    @Test
    void laPreguntaVaPorChatServiceYLaConexionRecibeEscribiendoYLaRespuestaConSuCorrelacion() {
        when(chatService.enviarMensaje(eq(jugador), eq("hola"), isNull(), eq(VistaDelChat.SUBASTAS),
            eq("203.0.113.7"))).thenReturn(respuestaDelBot("¡Hola!"));

        conversacion.recibir(json("{\"contenido\":\"hola\",\"vista\":\"SUBASTAS\",\"idCorrelacion\":\"p-1\","
            + "\"adjuntoUrl\":\"http://x/y.png\"}"), ANA, atributos);

        List<Enviado> enviados = enviados(2);
        Enviado estado = enviados.get(0);
        assertThat(estado.usuario()).isEqualTo(UID.toString());
        assertThat(estado.destino()).isEqualTo(DestinosDelCanal.COLA_ESTADO);
        assertThat(estado.cuerpo()).isEqualTo(new EstadoDeLaRespuesta("ESCRIBIENDO", "p-1"));
        assertThat(estado.cabeceras().getSessionId()).as("solo a esa conexion").isEqualTo("sesion-ana");
        assertThat(estado.nativa("id-correlacion")).isEqualTo("p-1");

        Enviado respuesta = enviados.get(1);
        assertThat(respuesta.destino()).isEqualTo(DestinosDelCanal.COLA_RESPUESTAS);
        assertThat(respuesta.cuerpo()).isInstanceOf(MensajeResponse.class);
        assertThat(((MensajeResponse) respuesta.cuerpo()).contenido()).isEqualTo("¡Hola!");
        assertThat(respuesta.cabeceras().getSessionId()).isEqualTo("sesion-ana");
        assertThat(respuesta.nativa("id-correlacion")).isEqualTo("p-1");
        // Una traza por pregunta (regla 5), la misma en el estado y en la respuesta.
        assertThat(respuesta.nativa("traceparent")).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
        assertThat(respuesta.nativa("traceparent").substring(3, 35))
            .isEqualTo(estado.nativa("traceparent").substring(3, 35));
        assertThat(respuesta.nativa("X-Id-Sesion-Anonima")).isNull();
        assertThat(((AtomicInteger) atributos.get(OrigenDelCanal.EN_VUELO)).get()).isZero();
    }

    @Test
    void laIdentidadSeVuelveAResolverEnCadaMensajeYUnTokenVencidoNoCuenta() {
        atributos.put(PoliticaDelCanal.TOKEN, emisor.tokenCaducado("ana", UID));
        atributos.put(PoliticaDelCanal.SESION_ANONIMA, "visitante-0123456789abcdef");
        when(chatService.enviarMensaje(any(), anyString(), any(), any(), any())).thenReturn(respuestaDelBot("ok"));

        conversacion.recibir(json("{\"contenido\":\"hola\"}"), ANA, atributos);

        enviados(2);
        verify(resolutor).resolverOEmitir(isNull(), eq("visitante-0123456789abcdef"), eq("203.0.113.7"));
    }

    @Test
    void conUnTokenVigenteSeResuelveComoJugador() {
        atributos.put(PoliticaDelCanal.TOKEN, emisor.tokenDeJugador("ana", UID));
        when(chatService.enviarMensaje(any(), anyString(), any(), any(), any())).thenReturn(respuestaDelBot("ok"));

        conversacion.recibir(json("{\"contenido\":\"hola\"}"), ANA, atributos);

        enviados(2);
        ArgumentCaptor<Authentication> autenticacion = ArgumentCaptor.forClass(Authentication.class);
        verify(resolutor).resolverOEmitir(autenticacion.capture(), isNull(), eq("203.0.113.7"));
        assertThat(autenticacion.getValue().getName()).isEqualTo(UID.toString());
    }

    @Test
    void siHuboQueEmitirUnaSesionViajaEnLaRespuestaYLaConexionLaUsaDesdeEntonces() {
        String nueva = "anon_" + "z".repeat(43);
        IdentidadDelChat visitante = IdentidadDelChat.visitante(
            new SesionAnonima("huella", Instant.parse("2026-10-14T10:00:00Z"), Duration.ofHours(24)));
        when(resolutor.resolverOEmitir(any(), any(), any())).thenReturn(new ResolutorDeIdentidad.Resultado(visitante, nueva));
        when(chatService.enviarMensaje(any(), anyString(), any(), any(), any())).thenReturn(respuestaDelBot("ok"));

        conversacion.recibir(json("{\"contenido\":\"hola\"}"), ANA, atributos);

        assertThat(enviados(2).get(1).nativa("X-Id-Sesion-Anonima")).isEqualTo(nueva);
        assertThat(atributos).containsEntry(PoliticaDelCanal.SESION_ANONIMA, nueva);
    }

    @Test
    void comoMuchoDosPreguntasSinResponderPorConexion() throws Exception {
        CountDownLatch libera = new CountDownLatch(1);
        CountDownLatch dentro = new CountDownLatch(2);
        when(chatService.enviarMensaje(any(), anyString(), any(), any(), any())).thenAnswer(invocacion -> {
            dentro.countDown();
            libera.await(ESPERA_MS, TimeUnit.MILLISECONDS);
            return respuestaDelBot("ok");
        });

        conversacion.recibir(json("{\"contenido\":\"uno\",\"idCorrelacion\":\"a\"}"), ANA, atributos);
        conversacion.recibir(json("{\"contenido\":\"dos\",\"idCorrelacion\":\"b\"}"), ANA, atributos);
        assertThat(dentro.await(ESPERA_MS, TimeUnit.MILLISECONDS)).isTrue();
        conversacion.recibir(json("{\"contenido\":\"tres\",\"idCorrelacion\":\"c\"}"), ANA, atributos);

        // Dos ESCRIBIENDO y, en el acto, el 429 de la tercera.
        List<Enviado> hastaAhora = enviados(3);
        Enviado rechazo = hastaAhora.get(2);
        assertThat(rechazo.destino()).isEqualTo(DestinosDelCanal.COLA_ERRORES);
        ErrorDelCanal error = (ErrorDelCanal) rechazo.cuerpo();
        assertThat(error.status()).isEqualTo(429);
        assertThat(error.motivo()).isEqualTo("LIMITE_DE_FRECUENCIA");
        assertThat(error.reintentarEn()).isEqualTo(1L);
        assertThat(error.detail()).isEqualTo(ConversacionEnVivo.PREGUNTA_ANTERIOR_SIN_RESPONDER);
        assertThat(error.idCorrelacion()).isEqualTo("c");

        libera.countDown();
        enviados(5);
        verify(chatService, times(2)).enviarMensaje(any(), anyString(), any(), any(), any());
        assertThat(((AtomicInteger) atributos.get(OrigenDelCanal.EN_VUELO)).get()).isZero();
    }

    @Test
    void losErroresSonLosDeLaApiConSuMotivo() {
        Map<RuntimeException, Object[]> casos = Map.of(
            new LimiteDeFrecuenciaExcedido("Demasiadas.", 42), new Object[] {429, "LIMITE_DE_FRECUENCIA", 42L},
            new ContenidoBloqueado(), new Object[] {422, "CONTENIDO_BLOQUEADO", null},
            new InyeccionDetectada(DetectorDeInyeccion.Tipo.SQL), new Object[] {422, "INYECCION_DETECTADA", null},
            new ModeracionNoDisponible(), new Object[] {503, "MODERACION_NO_DISPONIBLE", null},
            new ResponseStatusException(HttpStatus.BAD_REQUEST, "El mensaje esta vacio."), new Object[] {400, null, null},
            new IllegalStateException("se cayo algo"), new Object[] {500, null, null});
        for (Map.Entry<RuntimeException, Object[]> caso : casos.entrySet()) {
            var respuesta = conversacion.problemaDe(caso.getKey());
            ErrorDelCanal error = ErrorDelCanal.desde(respuesta, "x");
            assertThat(error.status()).as(caso.getKey().toString()).isEqualTo(caso.getValue()[0]);
            assertThat(error.motivo()).as(caso.getKey().toString()).isEqualTo(caso.getValue()[1]);
            assertThat(error.reintentarEn()).as(caso.getKey().toString()).isEqualTo(caso.getValue()[2]);
            assertThat(error.instance()).isEqualTo("/app/chat/mensajes");
            assertThat(error.title()).isNotBlank();
        }
        assertThat(ErrorDelCanal.desde(conversacion.problemaDe(new IllegalStateException("x")), null).detail())
            .doesNotContain("se cayo algo");
    }

    @Test
    void unErrorAlResponderVuelvePorLaColaDeErroresYLaConexionSigue() {
        when(chatService.enviarMensaje(any(), anyString(), any(), any(), any())).thenThrow(new ContenidoBloqueado());

        conversacion.recibir(json("{\"contenido\":\"algo feo\",\"idCorrelacion\":\"e-1\"}"), ANA, atributos);

        Enviado error = enviados(2).get(1);
        assertThat(error.destino()).isEqualTo(DestinosDelCanal.COLA_ERRORES);
        assertThat(((ErrorDelCanal) error.cuerpo()).motivo()).isEqualTo("CONTENIDO_BLOQUEADO");
        assertThat(error.nativa("id-correlacion")).isEqualTo("e-1");
        assertThat(((AtomicInteger) atributos.get(OrigenDelCanal.EN_VUELO)).get()).isZero();
    }

    @Test
    void unaPreguntaSinLaFormaDelContratoEsUn400QueTambienSeContesta() {
        String largo = "x".repeat(PreguntaEnVivo.MAXIMO_DE_CARACTERES + 1);
        Object[][] casos = {
            {"no es json", null},
            {"[\"contenido\"]", null},
            {"{\"contenido\":\"hola\",\"idCorrelacion\":\"con espacios\"}", null},
            {"{\"contenido\":\"hola\",\"idCorrelacion\":42}", null},
            {"{\"idCorrelacion\":\"v-1\"}", "v-1"},
            {"{\"contenido\":7,\"idCorrelacion\":\"v-2\"}", "v-2"},
            {"{\"contenido\":\"" + largo + "\",\"idCorrelacion\":\"v-3\"}", "v-3"},
            {"{\"contenido\":\"hola\",\"vista\":\"COCINA\",\"idCorrelacion\":\"v-4\"}", "v-4"},
            {"{\"contenido\":\"hola\",\"vista\":3,\"idCorrelacion\":\"v-5\"}", "v-5"},
        };
        for (Object[] caso : casos) {
            clearInvocations(mensajeria);
            conversacion.recibir(json((String) caso[0]), ANA, atributos);
            ErrorDelCanal error = unicoError();
            assertThat(error.status()).as((String) caso[0]).isEqualTo(400);
            assertThat(error.idCorrelacion()).as((String) caso[0]).isEqualTo(caso[1]);
        }
        conversacion.recibir(null, ANA, atributos);
        verify(chatService, never()).enviarMensaje(any(), any(), any(), any(), any());
    }

    @Test
    void sinAtributosDeSesionTambienResponde() {
        when(chatService.enviarMensaje(any(), anyString(), any(), any(), any())).thenReturn(respuestaDelBot("ok"));

        conversacion.recibir(json("{\"contenido\":\"hola\",\"vista\":null,\"idCorrelacion\":null}"), ANA, null);

        assertThat(enviados(2).get(1).destino()).isEqualTo(DestinosDelCanal.COLA_RESPUESTAS);
        verify(resolutor).resolverOEmitir(isNull(), isNull(), isNull());
    }

    @Test
    void alApagarseElServicioLaPreguntaRecibeUn503() {
        conversacion.destroy();

        conversacion.recibir(json("{\"contenido\":\"hola\",\"idCorrelacion\":\"t\"}"), ANA, atributos);

        List<Enviado> enviados = enviados(2);
        ErrorDelCanal error = (ErrorDelCanal) enviados.get(1).cuerpo();
        assertThat(error.status()).isEqualTo(503);
        assertThat(((AtomicInteger) atributos.get(OrigenDelCanal.EN_VUELO)).get()).isZero();
    }
}
