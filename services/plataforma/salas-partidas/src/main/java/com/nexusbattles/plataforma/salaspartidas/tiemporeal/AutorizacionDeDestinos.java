package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Quien puede suscribirse al canal de cada sala — HU-SAL-002.
 *
 * <p>Autenticado no significa dentro. {@code AutenticacionStomp} ya dejo en la
 * sesion quien es el jugador; aqui se decide, frame a frame de
 * {@code SUBSCRIBE}, si ese jugador tiene derecho al destino que pide:
 *
 * <ul>
 *   <li>{@code /tema/salas/{idSala}} y todo lo que cuelgue de ahi (por ejemplo
 *       el chat de la sala, {@code /tema/salas/{idSala}/chat}):
 *       <ul>
 *         <li>sala <b>publica</b>: cualquier jugador autenticado. Lo que viaja
 *             por ahi es lo mismo que muestra el listado de Batallas —quien
 *             entro y cuantos hay—, y el listado es publico por RF-JUE-002.</li>
 *         <li>sala <b>privada</b>: solo sus participantes. Una sala privada no
 *             se ve desde fuera, y su canal tampoco (criterio 3 de
 *             HU-SAL-001).</li>
 *         <li>sala inexistente: se rechaza, para no dejar suscripciones
 *             colgadas de un identificador que nadie va a publicar.</li>
 *       </ul>
 *   </li>
 *   <li>{@code /usuario/cola/**}: Spring ya lo resuelve por usuario; cada uno
 *       solo puede oir su propia cola.</li>
 *   <li>Cualquier otro tema del broker ({@code /tema/partidas/...},
 *       {@code /tema/chat/general}) pasa: no es de esta historia decidir sobre
 *       ellos y restringirlos sin requisito romperia el chat general.</li>
 * </ul>
 *
 * <p>Se rechaza lanzando {@link AccessDeniedException}: Spring la convierte en
 * un frame {@code ERROR} para ese cliente y no registra la suscripcion, sin
 * tumbar la conexion de nadie mas.
 *
 * <p>Solo mira {@code SUBSCRIBE}. Los {@code SEND} a {@code /app/...} los
 * atienden los controladores, que ya reciben el {@link Principal} y deciden
 * con el dominio; duplicar esa regla aqui seria tener dos sitios que decidan
 * lo mismo.
 */
public class AutorizacionDeDestinos implements ChannelInterceptor {

    private static final Pattern DESTINO_DE_SALA =
            Pattern.compile("^/tema/salas/([0-9a-fA-F-]{36})(?:/.*)?$");

    private final RepositorioDeSalas salas;

    public AutorizacionDeDestinos(RepositorioDeSalas salas) {
        this.salas = salas;
    }

    @Override
    public Message<?> preSend(Message<?> mensaje, MessageChannel canal) {
        StompHeaderAccessor cabeceras =
                MessageHeaderAccessor.getAccessor(mensaje, StompHeaderAccessor.class);
        if (cabeceras == null || !StompCommand.SUBSCRIBE.equals(cabeceras.getCommand())) {
            return mensaje;
        }

        String destino = cabeceras.getDestination();
        Matcher sala = destino == null ? null : DESTINO_DE_SALA.matcher(destino);
        if (sala == null || !sala.matches()) {
            return mensaje;
        }

        UUID idJugador = jugadorDe(cabeceras.getUser());
        UUID idSala = UUID.fromString(sala.group(1));

        Optional<Sala> encontrada = salas.buscarPorId(idSala);
        if (encontrada.isEmpty()) {
            throw new AccessDeniedException("La sala " + idSala + " no existe.");
        }
        Sala laSala = encontrada.get();
        if (laSala.privada() && !laSala.participantes().contains(idJugador)) {
            throw new AccessDeniedException(
                    "Esta sala es privada: solo sus participantes pueden seguir su canal.");
        }
        return mensaje;
    }

    /**
     * El identificador del jugador es el {@code sub} del JWT, que
     * {@code AutenticacionStomp} dejo como nombre del usuario de la sesion. Sin
     * usuario no hay suscripcion posible: la autenticacion va antes en la cadena
     * y ya habria cortado, pero no se confia en el orden para algo de seguridad.
     */
    private static UUID jugadorDe(Principal usuario) {
        if (usuario == null || usuario.getName() == null) {
            throw new AccessDeniedException("Hace falta estar identificado para suscribirse.");
        }
        try {
            return UUID.fromString(usuario.getName());
        } catch (IllegalArgumentException identidadRara) {
            throw new AccessDeniedException("La identidad de la sesion no es la de un jugador.");
        }
    }
}
