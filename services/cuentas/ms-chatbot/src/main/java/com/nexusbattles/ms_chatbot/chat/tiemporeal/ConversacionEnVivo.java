package com.nexusbattles.ms_chatbot.chat.tiemporeal;

import com.nexusbattles.comun.observabilidad.FiltroDeTraza;
import com.nexusbattles.comun.seguridad.ConversorRolesJwt;
import com.nexusbattles.ms_chatbot.chat.api.ManejadorErroresDelChat;
import com.nexusbattles.ms_chatbot.chat.api.MensajeResponse;
import com.nexusbattles.ms_chatbot.chat.entrada.InyeccionDetectada;
import com.nexusbattles.ms_chatbot.chat.identidad.ResolutorDeIdentidad;
import com.nexusbattles.ms_chatbot.chat.limite.LimiteDeFrecuenciaExcedido;
import com.nexusbattles.ms_chatbot.chat.model.Mensaje;
import com.nexusbattles.ms_chatbot.chat.moderacion.ContenidoBloqueado;
import com.nexusbattles.ms_chatbot.chat.moderacion.ModeracionNoDisponible;
import com.nexusbattles.ms_chatbot.chat.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import tools.jackson.databind.json.JsonMapper;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 7.4.9 — una pregunta que llega por el canal en tiempo real, de principio a
 * fin ({@code contracts/websocket/chatbot.yaml} 1.0.0).
 *
 * <h2>El mismo camino que la API</h2>
 *
 * Nada de lo que decide el chat se repite aqui: la pregunta va a
 * {@link ChatService#enviarMensaje} con la identidad de
 * {@link ResolutorDeIdentidad#resolverOEmitir} y el origen del handshake,
 * exactamente como en {@code ChatController.enviarMensaje}. Limites por
 * identidad y por direccion, revision de la entrada (422 de inyeccion, datos
 * tapados), lista negra, motores, cache, brechas y guardado son los de
 * siempre, y la respuesta es el {@link MensajeResponse} de la API. Los
 * errores se traducen con {@link ManejadorErroresDelChat}, asi que llevan el
 * mismo titulo, estado y {@code motivo} que en HTTP.
 *
 * <h2>Lo propio del canal</h2>
 *
 * <ol>
 *   <li>La forma del mensaje ({@link PreguntaEnVivo}): lo que la API valida
 *       con {@code @Valid} se valida aqui, y un 400 tambien se contesta.</li>
 *   <li>Como mucho {@code chatbot.ws.mensajes-en-vuelo} (2) preguntas sin
 *       responder por conexion: la siguiente recibe 429 en el acto. Sin
 *       esto, una conexion que escribiera en bucle llenaria la memoria de
 *       preguntas esperando turno (provisional, D-CHA-09).</li>
 *   <li>{@code ESCRIBIENDO} en cuanto se acepta la pregunta, y despues la
 *       respuesta o el error, solo a la conexion que pregunto (destinos de
 *       usuario con su id de sesion) y con su {@code id-correlacion}.</li>
 *   <li>La identidad se vuelve a resolver en cada mensaje con lo que trajo el
 *       CONNECT ({@link PoliticaDelCanal}): un token que vencio deja de ser un
 *       jugador, como en la API. Si para responder hubo que emitir una sesion
 *       de visitante, viaja en la cabecera {@code X-Id-Sesion-Anonima} de la
 *       respuesta y la conexion la usa desde entonces.</li>
 *   <li>Cada pregunta abre su propia traza (regla 5): las llamadas a la lista
 *       negra y a las consultas en vivo llevan su {@code traceparent}, y las
 *       respuestas tambien.</li>
 * </ol>
 *
 * <p>Se responde en un hilo virtual por pregunta: la respuesta puede esperar
 * a otros servicios (hasta ~2 s, 7.4.11) y los hilos del canal de entrada son
 * pocos y los comparten todas las conexiones.
 */
@Component
public class ConversacionEnVivo implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ConversacionEnVivo.class);
    private static final SecureRandom AZAR = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    static final String PREGUNTA_ANTERIOR_SIN_RESPONDER =
        "Espera la respuesta a tu pregunta anterior antes de enviar otra.";

    private final ChatService chatService;
    private final ResolutorDeIdentidad resolutor;
    private final ManejadorErroresDelChat errores;
    private final TokenDelCanal tokens;
    private final SimpMessagingTemplate mensajeria;
    private final JsonMapper json;
    private final int maximoEnVuelo;
    private final ExecutorService hilos =
        Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("chatbot-canal-", 0).factory());

    public ConversacionEnVivo(ChatService chatService, ResolutorDeIdentidad resolutor,
                              ManejadorErroresDelChat errores, JwtDecoder decodificador, ConversorRolesJwt conversor,
                              SimpMessagingTemplate mensajeria, JsonMapper json,
                              @Value("${chatbot.ws.mensajes-en-vuelo:2}") int maximoEnVuelo) {
        this.chatService = chatService;
        this.resolutor = resolutor;
        this.errores = errores;
        this.tokens = new TokenDelCanal(decodificador, conversor);
        this.mensajeria = mensajeria;
        this.json = json;
        this.maximoEnVuelo = Math.max(1, maximoEnVuelo);
    }

    /** A quien se contesta: el usuario de la conexion y la conexion misma. */
    record Destinatario(String usuario, String sesionId) {
    }

    /**
     * Recibe una pregunta del canal. Vuelve enseguida: la respuesta se
     * calcula y se manda en otro hilo.
     *
     * @param cuerpo    el frame SEND, sin convertir
     * @param quien     la conexion (su usuario lo fijo {@link PoliticaDelCanal})
     * @param atributos los atributos de la sesion WebSocket (origen, token,
     *                  sesion de visitante, preguntas en vuelo)
     */
    public void recibir(byte[] cuerpo, Destinatario quien, Map<String, Object> atributos) {
        PreguntaEnVivo pregunta;
        try {
            pregunta = PreguntaEnVivo.leer(cuerpo, json);
        } catch (PreguntaEnVivo.Invalida invalida) {
            responderError(quien, ResponseEntity.badRequest()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, invalida.getMessage())),
                invalida.idCorrelacion(), null);
            return;
        }
        AtomicInteger enVuelo = atributos == null ? null : (AtomicInteger) atributos.get(OrigenDelCanal.EN_VUELO);
        if (enVuelo != null && enVuelo.incrementAndGet() > maximoEnVuelo) {
            enVuelo.decrementAndGet();
            responderError(quien, errores.limite(new LimiteDeFrecuenciaExcedido(PREGUNTA_ANTERIOR_SIN_RESPONDER, 1)),
                pregunta.idCorrelacion(), null);
            return;
        }
        String traza = nuevoIdentificador(16);
        enviar(quien, DestinosDelCanal.COLA_ESTADO, EstadoDeLaRespuesta.escribiendo(pregunta.idCorrelacion()),
            pregunta.idCorrelacion(), traza, Map.of());
        try {
            hilos.execute(() -> responder(quien, pregunta, atributos, enVuelo, traza));
        } catch (RejectedExecutionException apagando) {
            if (enVuelo != null) {
                enVuelo.decrementAndGet();
            }
            responderError(quien, ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                        "El asistente se esta reiniciando. Intentalo de nuevo en un momento.")),
                pregunta.idCorrelacion(), traza);
        }
    }

    private void responder(Destinatario quien, PreguntaEnVivo pregunta, Map<String, Object> atributos,
                           AtomicInteger enVuelo, String traza) {
        MDC.put(FiltroDeTraza.CLAVE_MDC, traza);
        try {
            String origen = atributo(atributos, OrigenDelCanal.ORIGEN);
            Authentication autenticacion = tokens.autenticar(atributo(atributos, PoliticaDelCanal.TOKEN));
            ResolutorDeIdentidad.Resultado identidad = resolutor.resolverOEmitir(autenticacion,
                atributo(atributos, PoliticaDelCanal.SESION_ANONIMA), origen);
            Map<String, String> extra = Map.of();
            if (identidad.sesionEmitida() != null) {
                // La sesion anterior vencio: desde ahora la conexion usa la nueva.
                if (atributos != null) {
                    atributos.put(PoliticaDelCanal.SESION_ANONIMA, identidad.sesionEmitida());
                }
                extra = Map.of(DestinosDelCanal.CABECERA_SESION_ANONIMA, identidad.sesionEmitida());
            }
            // 1.3.0: adjuntoUrl esta obsoleto; como en la API, no llega al servicio.
            Mensaje respuesta = chatService.enviarMensaje(identidad.identidad(), pregunta.contenido(), null,
                pregunta.vista(), origen);
            enviar(quien, DestinosDelCanal.COLA_RESPUESTAS, MensajeResponse.desde(respuesta),
                pregunta.idCorrelacion(), traza, extra);
        } catch (RuntimeException error) {
            responderError(quien, problemaDe(error), pregunta.idCorrelacion(), traza);
        } finally {
            if (enVuelo != null) {
                enVuelo.decrementAndGet();
            }
            MDC.remove(FiltroDeTraza.CLAVE_MDC);
        }
    }

    // Los mismos errores que POST /chat/mensajes, con el mismo manejador.
    ResponseEntity<ProblemDetail> problemaDe(RuntimeException error) {
        return switch (error) {
            case LimiteDeFrecuenciaExcedido limite -> errores.limite(limite);
            case ContenidoBloqueado bloqueado -> errores.bloqueado(bloqueado);
            case InyeccionDetectada inyeccion -> errores.inyeccion(inyeccion);
            case ModeracionNoDisponible sinModeracion -> errores.sinModeracion(sinModeracion);
            case ErrorResponse respuesta -> ResponseEntity.status(respuesta.getStatusCode()).body(respuesta.getBody());
            default -> {
                // Sin el texto de la pregunta: la bitacora no guarda lo que escribe nadie (7.4.8).
                log.error("No se pudo responder una pregunta del canal en tiempo real del chatbot.", error);
                ProblemDetail interno = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo responder tu mensaje. Intentalo de nuevo.");
                interno.setTitle("Error interno");
                yield ResponseEntity.internalServerError().body(interno);
            }
        };
    }

    private void responderError(Destinatario quien, ResponseEntity<ProblemDetail> problema, String idCorrelacion,
                                String traza) {
        enviar(quien, DestinosDelCanal.COLA_ERRORES, ErrorDelCanal.desde(problema, idCorrelacion), idCorrelacion,
            traza, Map.of());
    }

    // Solo a ESA conexion: el destino de usuario con su id de sesion. Spring
    // la busca entre las del usuario y, si no la encuentra, no manda nada.
    private void enviar(Destinatario quien, String destino, Object cuerpo, String idCorrelacion, String traza,
                        Map<String, String> extra) {
        SimpMessageHeaderAccessor cabeceras = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        cabeceras.setSessionId(quien.sesionId());
        if (idCorrelacion != null) {
            cabeceras.setNativeHeader(DestinosDelCanal.CABECERA_CORRELACION, idCorrelacion);
        }
        if (traza != null) {
            cabeceras.setNativeHeader(FiltroDeTraza.CABECERA, "00-" + traza + "-" + nuevoIdentificador(8) + "-01");
        }
        extra.forEach(cabeceras::setNativeHeader);
        cabeceras.setLeaveMutable(true);
        mensajeria.convertAndSendToUser(quien.usuario(), destino, cuerpo, cabeceras.getMessageHeaders());
    }

    private static String atributo(Map<String, Object> atributos, String nombre) {
        return atributos != null && atributos.get(nombre) instanceof String valor ? valor : null;
    }

    private static String nuevoIdentificador(int bytes) {
        byte[] datos = new byte[bytes];
        AZAR.nextBytes(datos);
        return HEX.formatHex(datos);
    }

    @Override
    public void destroy() {
        hilos.shutdown();
    }
}
