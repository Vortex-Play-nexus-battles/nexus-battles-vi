package com.nexusbattles.comun.seguridad.tiemporeal;

import org.springframework.core.convert.converter.Converter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.util.Objects;

/**
 * Identidad de la conexion STOMP a partir del mismo JWT de la API HTTP.
 *
 * <p>El navegador no puede mandar cabeceras en el handshake WebSocket, asi
 * que el token viaja en la cabecera {@code Authorization} del frame CONNECT.
 * Sin token valido no hay conexion: ningun canal de la plataforma admite
 * anonimos, porque cada mensaje se atribuye a un usuario y las sanciones son
 * por persona.
 *
 * <p><b>Por que vive aqui.</b> Nacio en {@code salas-partidas} para el chat
 * (HU-JUE-015) y la sala de batalla (HU-SAL-002). Cuando {@code notificaciones}
 * (HU-NOT-006) dejo de identificar al usuario por un parametro de la URL y paso
 * a exigir el mismo token, la pieza se volvio comun.
 *
 * <p>El usuario de la sesion se construye con el mismo conversor que usa la
 * cadena HTTP ({@code ConversorRolesJwt}), asi que el nombre del principal es
 * el identificador estable ({@code uid}) y las autoridades son los roles: lo
 * que un servicio decide por rol o por propietario en HTTP lo decide igual en
 * el canal. Un servicio que se suscriba a {@code /usuario/**} recibe lo suyo
 * porque el broker resuelve el destino por ese mismo nombre.
 */
public class AutenticacionStomp implements ChannelInterceptor {

    public static final String CABECERA = "Authorization";
    public static final String PREFIJO = "Bearer ";

    private final JwtDecoder decodificador;
    private final Converter<Jwt, ? extends AbstractAuthenticationToken> conversor;

    public AutenticacionStomp(JwtDecoder decodificador,
                              Converter<Jwt, ? extends AbstractAuthenticationToken> conversor) {
        this.decodificador = Objects.requireNonNull(decodificador, "decodificador");
        this.conversor = Objects.requireNonNull(conversor, "conversor");
    }

    @Override
    public Message<?> preSend(Message<?> mensaje, MessageChannel canal) {
        StompHeaderAccessor cabeceras = MessageHeaderAccessor.getAccessor(mensaje, StompHeaderAccessor.class);
        if (cabeceras == null || !StompCommand.CONNECT.equals(cabeceras.getCommand())) {
            return mensaje;
        }
        String valor = cabeceras.getFirstNativeHeader(CABECERA);
        if (valor == null || !valor.startsWith(PREFIJO)) {
            throw new AccessDeniedException("El canal necesita un token de acceso en la conexion.");
        }
        try {
            Jwt jwt = decodificador.decode(valor.substring(PREFIJO.length()).strip());
            AbstractAuthenticationToken usuario = conversor.convert(jwt);
            if (usuario == null) {
                throw new AccessDeniedException("El token de acceso no identifica a nadie.");
            }
            cabeceras.setUser(usuario);
        } catch (JwtException ex) {
            throw new AccessDeniedException("El token de acceso no es valido.");
        }
        return mensaje;
    }
}
