package com.nexusbattles.ms_chatbot.chat.tiemporeal;

import com.nexusbattles.ms_chatbot.chat.identidad.ResolutorDeIdentidad;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.Objects;

/**
 * 7.4.9 — quien entra al canal del chatbot y que puede hacer
 * ({@code contracts/websocket/chatbot.yaml} 1.0.0). Decide frame a frame,
 * antes de que nada llegue al broker ni a {@link ChatEnVivoController}.
 *
 * <h2>CONNECT: las mismas reglas que la API</h2>
 *
 * Por que no es {@code AutenticacionStomp} (plataforma-seguridad) tal cual:
 * ese rechaza todo CONNECT sin token valido, y el chat del asistente es 24/7
 * para visitantes (HU-CHA-001). Aqui la identidad se resuelve como en
 * {@code /chat/**} ({@code ResolutorDeIdentidad}, B11 y 1.4.0):
 *
 * <ul>
 *   <li>{@code Authorization: Bearer <JWT>} valido, con {@code uid}: un
 *       jugador. Con el MISMO decodificador (el JWKS de ms-identidad) y el
 *       mismo conversor de roles que la cadena HTTP;</li>
 *   <li>un token invalido, vencido, firmado por otro o sin {@code uid} se
 *       trata como si no viniera, igual que {@code JwtInvalidoComoVisitanteFilter}
 *       en la API: degrada a visitante, no corta;</li>
 *   <li>un visitante necesita {@code X-Id-Sesion-Anonima}: emitida y vigente,
 *       o declarada por el navegador;</li>
 *   <li>sin ninguna de las dos, la conexion se rechaza (frame ERROR).</li>
 * </ul>
 *
 * <p>El usuario de la conexion ({@link UsuarioDelCanal}) lo arma el servidor:
 * nada del cliente se convierte en un nombre. En los atributos de la sesion
 * quedan el token (solo si era valido) y la sesion de visitante, para volver
 * a resolver la identidad en cada mensaje con las reglas de la API
 * ({@link ConversacionEnVivo}): si el token vence mientras tanto, el mensaje
 * es de visitante, como en {@code POST /chat/mensajes}.
 *
 * <h2>SUBSCRIBE y SEND: solo lo que el contrato declara</h2>
 *
 * Un SUBSCRIBE solo a los cuatro destinos de usuario, escritos tal cual
 * ({@link DestinosDelCanal#SUSCRIPCIONES}); un SEND solo a
 * {@code /app/chat/mensajes}. Todo lo demas —{@code /user/<otro>/...},
 * {@code /queue/...} directo (donde vive la cola real de cada conexion),
 * {@code /topic/...}, comodines— recibe ERROR y la conexion se cierra. Los
 * destinos de usuario los resuelve Spring con la conexion de quien se
 * suscribe, asi que nadie recibe la cola de otro.
 */
public class PoliticaDelCanal implements ChannelInterceptor {

    static final String CABECERA_AUTORIZACION = "Authorization";

    /** Atributos de la sesion que lee {@link ConversacionEnVivo} en cada mensaje. */
    static final String TOKEN = "chatbot.token";
    static final String SESION_ANONIMA = "chatbot.sesionAnonima";

    private final TokenDelCanal tokens;
    private final ResolutorDeIdentidad resolutor;

    public PoliticaDelCanal(TokenDelCanal tokens, ResolutorDeIdentidad resolutor) {
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.resolutor = Objects.requireNonNull(resolutor, "resolutor");
    }

    @Override
    public Message<?> preSend(Message<?> mensaje, MessageChannel canal) {
        StompHeaderAccessor cabeceras = MessageHeaderAccessor.getAccessor(mensaje, StompHeaderAccessor.class);
        if (cabeceras == null || cabeceras.getCommand() == null) {
            return mensaje;
        }
        return switch (cabeceras.getCommand()) {
            case CONNECT, STOMP -> identificar(mensaje, cabeceras);
            case SUBSCRIBE -> comprobarSuscripcion(mensaje, cabeceras);
            case SEND -> comprobarEnvio(mensaje, cabeceras);
            default -> mensaje;
        };
    }

    private Message<?> identificar(Message<?> mensaje, StompHeaderAccessor cabeceras) {
        String token = TokenDelCanal.deLaCabecera(cabeceras.getFirstNativeHeader(CABECERA_AUTORIZACION));
        Authentication autenticacion = tokens.autenticar(token);
        String sesion = cabeceras.getFirstNativeHeader(DestinosDelCanal.CABECERA_SESION_ANONIMA);
        ResolutorDeIdentidad.EnElCanal quien = resolutor.enElCanal(autenticacion, sesion)
            .orElseThrow(() -> new AccessDeniedException(
                "El canal del chatbot necesita un token de acceso valido o una sesion de visitante "
                    + "(X-Id-Sesion-Anonima)."));
        Map<String, Object> atributos = cabeceras.getSessionAttributes();
        if (atributos != null) {
            if (quien.jugador()) {
                atributos.put(TOKEN, token);
            }
            if (sesion != null && !sesion.isBlank()) {
                atributos.put(SESION_ANONIMA, sesion);
            }
        }
        cabeceras.setUser(new UsuarioDelCanal(quien.nombre(), quien.jugador()));
        return mensaje;
    }

    private Message<?> comprobarSuscripcion(Message<?> mensaje, StompHeaderAccessor cabeceras) {
        exigirConexion(cabeceras);
        String destino = cabeceras.getDestination();
        // Set.of no admite contains(null): un SUBSCRIBE sin destino tambien es un destino que no existe.
        if (destino == null || !DestinosDelCanal.SUSCRIPCIONES.contains(destino)) {
            throw new AccessDeniedException("Ese destino no existe en el canal del chatbot.");
        }
        return mensaje;
    }

    private Message<?> comprobarEnvio(Message<?> mensaje, StompHeaderAccessor cabeceras) {
        exigirConexion(cabeceras);
        if (!DestinosDelCanal.ENVIAR_MENSAJE.equals(cabeceras.getDestination())) {
            throw new AccessDeniedException("El canal del chatbot solo acepta preguntas en "
                + DestinosDelCanal.ENVIAR_MENSAJE + ".");
        }
        return mensaje;
    }

    private static void exigirConexion(StompHeaderAccessor cabeceras) {
        if (!(cabeceras.getUser() instanceof UsuarioDelCanal)) {
            throw new AccessDeniedException("La conexion no se identifico.");
        }
    }
}
