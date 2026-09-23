package com.nexusbattles.plataforma.salaspartidas.chat.canal;

import com.nexusbattles.comun.error.ErrorDeNegocio;
import com.nexusbattles.plataforma.salaspartidas.chat.Canal;
import com.nexusbattles.plataforma.salaspartidas.chat.EnviarMensaje;
import com.nexusbattles.plataforma.salaspartidas.chat.HistorialDeChat;
import com.nexusbattles.plataforma.salaspartidas.chat.MensajeDeChat.Autor;
import com.nexusbattles.plataforma.resiliencia.parametros.LectorDeParametros;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import com.nexusbattles.comun.seguridad.IdentidadDelToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * Entradas STOMP del chat, con las direcciones del contrato AsyncAPI.
 *
 * <p>El historial se entrega al suscribirse a /app/.../historial, en una sola
 * respuesta a esa conexion, y no por HTTP: asi quien entra despues se pone al
 * dia en el mismo canal en el que va a conversar. Los errores vuelven por la
 * cola privada /usuario/cola/salas que ya declara el contrato, en el mismo
 * formato problem details que ManejadorDeErrores.
 */
@Controller
public class ChatController {

    /** D-16 / HU-JUE-015: cuantos mensajes se cargan al entrar a un chat. */
    static final String CLAVE_TAMANO = "chat.historial.tamano";

    private final EnviarMensaje enviarMensaje;
    private final HistorialDeChat historial;
    private final LectorDeParametros parametros;
    private final int respaldoTamano;

    /**
     * El tamano del historial es una decision de producto (D-16), no una
     * constante del servicio: vive en el catalogo de admin-parametros y
     * {@code CHAT_HISTORIAL_TAMANO} pasa a ser el <b>respaldo</b>, el valor que
     * se aplica cuando el catalogo no responde o el Product Owner no lo ha
     * fijado. Si el catalogo se cae, el chat sigue cargando su historial.
     */
    @Autowired
    public ChatController(EnviarMensaje enviarMensaje, HistorialDeChat historial,
            LectorDeParametros parametros,
            @Value("${chat.historial.tamano:50}") int respaldoTamano) {
        this.enviarMensaje = enviarMensaje;
        this.historial = historial;
        this.parametros = parametros;
        this.respaldoTamano = respaldoTamano;
    }

    /** Con un tamano fijo, sin catalogo: lo usan las pruebas. */
    public ChatController(EnviarMensaje enviarMensaje, HistorialDeChat historial, int tamanoHistorial) {
        this(enviarMensaje, historial, LectorDeParametros.soloRespaldo(), tamanoHistorial);
    }

    /**
     * El tamano vigente. Se pregunta en cada suscripcion —no al arrancar—
     * porque si no, cambiar el parametro exigiria reiniciar el servicio.
     * La lectura pasa por la cache del lector, asi que no es una llamada de
     * red por suscripcion.
     *
     * <p>Se acota por abajo a 1: un catalogo con 0 o un numero negativo no
     * puede convertir «carga los ultimos N» en una consulta sin sentido.
     */
    private int tamanoHistorial() {
        long vigente = parametros.entero(CLAVE_TAMANO, respaldoTamano);
        return (int) Math.min(Math.max(vigente, 1L), Integer.MAX_VALUE);
    }

    @MessageMapping("/salas/{idSala}/chat")
    public void enviarASala(@DestinationVariable UUID idSala, @Payload EnviarMensajeRequest cuerpo,
            Principal principal) {
        enviarMensaje.enviar(Canal.deSala(idSala), autorDe(principal), cuerpo.texto(), cuerpo.logro());
    }

    @MessageMapping("/chat/general")
    public void enviarAlGeneral(@Payload EnviarMensajeRequest cuerpo, Principal principal) {
        enviarMensaje.enviar(Canal.general(), autorDe(principal), cuerpo.texto(), cuerpo.logro());
    }

    @SubscribeMapping("/salas/{idSala}/chat/historial")
    public List<MensajeDeChatResponse> historialDeSala(@DestinationVariable UUID idSala) {
        return historial.ultimos(Canal.deSala(idSala), tamanoHistorial()).stream()
                .map(MensajeDeChatResponse::de).toList();
    }

    @SubscribeMapping("/chat/general/historial")
    public List<MensajeDeChatResponse> historialGeneral() {
        return historial.ultimos(Canal.general(), tamanoHistorial()).stream()
                .map(MensajeDeChatResponse::de).toList();
    }

    /** Mismo formato que ManejadorDeErrores, pero por la cola privada del jugador (errorDeCanal). */
    @MessageExceptionHandler(ErrorDeNegocio.class)
    @SendToUser(destinations = "/cola/salas", broadcast = false)
    public ProblemDetail errorDeNegocio(ErrorDeNegocio error) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.valueOf(error.estado()), error.detalle());
        problema.setType(error.tipo());
        problema.setTitle(error.titulo());
        return problema;
    }

    /** El jugador sale del mismo JWT que en SalasController: sujeto y claim del apodo. */
    static Autor autorDe(Principal principal) {
        if (!(principal instanceof JwtAuthenticationToken token)) {
            throw new AccessDeniedException("El chat necesita un jugador autenticado.");
        }
        Jwt jwt = token.getToken();
        // NO `UUID.fromString(jwt.getSubject())`: tras ADR-002 el sujeto de
        // ms-identidad es el APODO, no un UUID, y eso reventaba con un 500. Se
        // corrigio en SalasController (PR #404) y aqui se habia quedado el
        // fallo: la regla vive ahora en un solo sitio.
        return new Autor(IdentidadDelToken.idDe(jwt), IdentidadDelToken.apodoDe(jwt));
    }
}
