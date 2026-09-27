package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import com.nexusbattles.comun.seguridad.IdentidadDelToken;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDePartidas;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.security.Principal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Quien puede enviar a que destino y quien puede seguir cada canal del broker
 * STOMP — HU-SAL-002, ampliado en B6 (mensajes privados) a todo el servicio.
 *
 * <p>Autenticado no significa autorizado. {@code AutenticacionStomp} ya dejo en
 * la sesion quien es el jugador; aqui se decide, frame a frame de {@code SEND}
 * y de {@code SUBSCRIBE}, si ese jugador tiene derecho al destino que pide.
 *
 * <h2>Por que hizo falta ampliarlo (auditoria de septiembre)</h2>
 *
 * Hasta B6 solo se miraba el {@code SUBSCRIBE} a {@code /tema/salas/{uuid}}.
 * Todo lo demas pasaba, y el broker simple lo atendia al pie de la letra:
 *
 * <ul>
 *   <li>Un {@code SEND} directo a {@code /tema/**} o a {@code /cola/**} lo
 *       reenviaba el broker a los suscritos <b>sin pasar por ningun
 *       controlador</b>: sin lista negra, sin sancion y sin quedar en el
 *       historial. Cualquiera podia escribir en el chat general, en el de una
 *       sala ajena o en el canal de una partida.</li>
 *   <li>El broker simple admite <b>suscripciones con patron</b>
 *       ({@code /tema/salas/**}, {@code /cola/**}): con una sola, un cliente
 *       oia todos los chats privados, o la cola ya resuelta de otra sesion
 *       ({@code /cola/mensajes-directos-user…}), que es por donde viajan los
 *       mensajes privados.</li>
 *   <li>El historial de una sala privada ({@code @SubscribeMapping}) se leia
 *       sin ser participante, y el {@code SEND} al chat de una sala privada no
 *       comprobaba nada.</li>
 *   <li>{@code /tema/partidas/{id}} lo seguia cualquiera.</li>
 * </ul>
 *
 * <h2>La regla, ahora</h2>
 *
 * <ol>
 *   <li><b>Forma canonica.</b> El destino es una ruta de segmentos
 *       {@code [A-Za-z0-9_-]}: ni comodines ({@code *}, {@code **}), ni
 *       {@code //}, ni {@code ..}, ni barra final. El enrutador de Spring
 *       ignora los segmentos vacios ({@code /app//salas/x/chat} llega a
 *       {@code /salas/{id}/chat}), asi que una ruta no canonica podia esquivar
 *       la regla de la sala y aterrizar en el mismo controlador. Ningun
 *       cliente legitimo manda otra cosa.</li>
 *   <li><b>SEND solo a {@code /app/**}</b>, que es donde estan los
 *       controladores con sus reglas. A {@code /tema/**}, {@code /cola/**} o
 *       {@code /usuario/**}: rechazado.</li>
 *   <li><b>SUBSCRIBE solo a lo que el servicio publica</b>: la cola propia
 *       ({@code /usuario/cola/**}, que Spring resuelve por sesion), el chat
 *       general y su historial, el canal de una sala y su historial, y el
 *       canal de una partida. Cualquier otro destino, rechazado.</li>
 *   <li><b>Sala privada:</b> solo sus participantes siguen
 *       {@code /tema/salas/{id}/**}, escriben en {@code /app/salas/{id}/chat}
 *       y leen su historial. Una sala publica no cambia: la sigue, lee y
 *       escribe cualquier jugador autenticado, como siempre. Una sala que no
 *       existe no admite nada.</li>
 *   <li><b>Partida:</b> solo sus participantes siguen
 *       {@code /tema/partidas/{id}}; una que no existe, nadie.</li>
 * </ol>
 *
 * <p>Se rechaza lanzando {@link AccessDeniedException}: Spring la convierte en
 * un frame {@code ERROR} para ese cliente y no registra la suscripcion ni
 * entrega el envio, sin tumbar la conexion de nadie mas. Los rechazos de
 * NEGOCIO (texto bloqueado, jugador sancionado, demasiado rapido) no son de
 * aqui: los decide cada controlador con el dominio y vuelven por la cola
 * privada del jugador, porque no son un intento de saltarse el protocolo.
 */
public class AutorizacionDeDestinos implements ChannelInterceptor {

    /** Segmentos sin comodines, sin vacios y sin puntos. */
    private static final Pattern FORMA_CANONICA = Pattern.compile("^(?:/[A-Za-z0-9_-]+)+$");

    private static final String PREFIJO_APLICACION = "/app/";
    private static final String PREFIJO_COLA_PROPIA = "/usuario/cola/";
    private static final String CHAT_GENERAL = "/tema/chat/general";
    private static final String HISTORIAL_GENERAL = "/app/chat/general/historial";

    private static final Pattern TEMA_DE_SALA = Pattern.compile("^/tema/salas/([^/]+)(?:/.*)?$");
    private static final Pattern CHAT_DE_SALA = Pattern.compile("^/app/salas/([^/]+)/chat$");
    private static final Pattern HISTORIAL_DE_SALA = Pattern.compile("^/app/salas/([^/]+)/chat/historial$");
    private static final Pattern TEMA_DE_PARTIDA = Pattern.compile("^/tema/partidas/([^/]+)(?:/.*)?$");

    private final RepositorioDeSalas salas;
    private final RepositorioDePartidas partidas;

    public AutorizacionDeDestinos(RepositorioDeSalas salas, RepositorioDePartidas partidas) {
        this.salas = Objects.requireNonNull(salas, "Sin salas no se puede decidir sobre sus canales.");
        this.partidas = Objects.requireNonNull(partidas, "Sin partidas no se puede decidir sobre sus canales.");
    }

    @Override
    public Message<?> preSend(Message<?> mensaje, MessageChannel canal) {
        StompHeaderAccessor cabeceras =
                MessageHeaderAccessor.getAccessor(mensaje, StompHeaderAccessor.class);
        if (cabeceras == null) {
            return mensaje;
        }
        StompCommand comando = cabeceras.getCommand();
        if (!StompCommand.SEND.equals(comando) && !StompCommand.SUBSCRIBE.equals(comando)) {
            return mensaje;
        }

        String destino = cabeceras.getDestination();
        if (destino == null || !FORMA_CANONICA.matcher(destino).matches()) {
            throw new AccessDeniedException("Ese destino no existe en este servicio.");
        }

        if (StompCommand.SEND.equals(comando)) {
            autorizarEnvio(destino, cabeceras.getUser());
        } else {
            autorizarSuscripcion(destino, cabeceras.getUser());
        }
        return mensaje;
    }

    /**
     * Un envio solo llega a un controlador: es ahi donde viven la lista negra,
     * la sancion y el historial. Lo unico que se decide aqui, ademas, es la
     * sala privada, para que el chat de una sala ajena no se pueda ni intentar.
     */
    private void autorizarEnvio(String destino, Principal usuario) {
        if (!destino.startsWith(PREFIJO_APLICACION)) {
            throw new AccessDeniedException(
                    "Solo se puede enviar a destinos de la aplicacion: el resto los publica el servidor.");
        }
        Matcher chat = CHAT_DE_SALA.matcher(destino);
        if (chat.matches()) {
            exigirAccesoASala(chat.group(1), usuario);
        }
    }

    private void autorizarSuscripcion(String destino, Principal usuario) {
        if (destino.startsWith(PREFIJO_COLA_PROPIA)
                || CHAT_GENERAL.equals(destino)
                || HISTORIAL_GENERAL.equals(destino)) {
            return;
        }
        Matcher sala = TEMA_DE_SALA.matcher(destino);
        if (sala.matches()) {
            exigirAccesoASala(sala.group(1), usuario);
            return;
        }
        Matcher historial = HISTORIAL_DE_SALA.matcher(destino);
        if (historial.matches()) {
            exigirAccesoASala(historial.group(1), usuario);
            return;
        }
        Matcher partida = TEMA_DE_PARTIDA.matcher(destino);
        if (partida.matches()) {
            exigirParticipacion(partida.group(1), usuario);
            return;
        }
        throw new AccessDeniedException("Ese canal no existe en este servicio.");
    }

    /**
     * La regla de HU-SAL-002, la misma para seguir el canal, leer el historial
     * y escribir en el chat: publica para cualquiera identificado, privada solo
     * para sus participantes, inexistente para nadie.
     */
    private void exigirAccesoASala(String idTexto, Principal usuario) {
        UUID idJugador = jugadorDe(usuario);
        UUID idSala = identificadorDe(idTexto, "La sala pedida no existe.");

        Optional<Sala> encontrada = salas.buscarPorId(idSala);
        if (encontrada.isEmpty()) {
            throw new AccessDeniedException("La sala " + idSala + " no existe.");
        }
        Sala laSala = encontrada.get();
        if (laSala.privada() && !laSala.participantes().contains(idJugador)) {
            throw new AccessDeniedException(
                    "Esta sala es privada: solo sus participantes pueden seguir su canal.");
        }
    }

    /** El avance del combate es de quienes combaten. */
    private void exigirParticipacion(String idTexto, Principal usuario) {
        UUID idJugador = jugadorDe(usuario);
        UUID idPartida = identificadorDe(idTexto, "La partida pedida no existe.");

        Partida laPartida = partidas.buscarPorId(idPartida)
                .orElseThrow(() -> new AccessDeniedException("La partida " + idPartida + " no existe."));
        boolean juega = laPartida.participantes().stream()
                .map(ParticipanteDePartida::idJugador)
                .anyMatch(idJugador::equals);
        if (!juega) {
            throw new AccessDeniedException("Solo quienes juegan esta partida pueden seguir su canal.");
        }
    }

    private static UUID identificadorDe(String texto, String siNoLoEs) {
        try {
            return UUID.fromString(texto);
        } catch (IllegalArgumentException malFormado) {
            throw new AccessDeniedException(siNoLoEs);
        }
    }

    /**
     * Identificador estable del jugador de la sesion STOMP.
     *
     * <p><b>Sale del claim {@code uid}, NO del nombre del principal.</b>
     * {@code AutenticacionStomp} deja un {@link JwtAuthenticationToken}; su
     * {@code getName()} es el {@code uid} cuando el token lo trae
     * ({@code ConversorRolesJwt}), pero el sujeto de {@code ms-identidad} es el
     * <i>apodo</i>, y leer el sujeto como UUID rechazaba a todo jugador real y
     * se llevaba por delante el canal de la partida abierto sobre la misma
     * conexion. {@link IdentidadDelToken} existe para no repetir ese error.
     *
     * <p>Se conserva el camino del nombre para principales que no son JWT.
     *
     * <p>Sin usuario no hay nada que autorizar: la autenticacion va antes en la
     * cadena y ya habria cortado, pero no se confia en el orden para algo de
     * seguridad.
     */
    private static UUID jugadorDe(Principal usuario) {
        if (usuario == null) {
            throw new AccessDeniedException("Hace falta estar identificado para usar este canal.");
        }
        if (usuario instanceof JwtAuthenticationToken token) {
            try {
                return IdentidadDelToken.idDe(token.getToken());
            } catch (IllegalArgumentException sinIdentificador) {
                throw new AccessDeniedException(
                        "El token de la sesion no trae un identificador de jugador.");
            }
        }
        if (usuario.getName() == null) {
            throw new AccessDeniedException("Hace falta estar identificado para usar este canal.");
        }
        try {
            return UUID.fromString(usuario.getName());
        } catch (IllegalArgumentException identidadRara) {
            throw new AccessDeniedException("La identidad de la sesion no es la de un jugador.");
        }
    }
}
