package com.nexusbattles.plataforma.notificaciones.bandeja;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Identidad provisional de cada conexion WebSocket, tomada del handshake.
 *
 * <p>Sin un Principal por conexion, el broker no puede resolver la cola
 * privada /usuario/cola/notificaciones y ningun aviso llega en tiempo real,
 * por mas que el dominio y el servicio hagan bien su parte. OAuth2 esta
 * diferido en todo el monorepo, asi que mientras el resource server no
 * exista, el cliente declara quien es en la URL del handshake:
 * {endpoint}?usuario={usuarioId}&amp;sesion={sesionId}. Cuando entre OAuth2
 * la identidad saldra del token y esta clase se reemplaza sin mover el
 * contrato.
 *
 * <p>El identificador estable de la sesion viaja tambien aqui, y no solo en
 * el alta por STOMP, para que la desconexion pueda cerrarla aunque el
 * cliente se caiga sin despedirse, que es justo el escenario de reconexion
 * de la historia.
 */
class AsignadorDeIdentidadDelHandshake extends DefaultHandshakeHandler {

    static final String ATRIBUTO_USUARIO = "usuarioId";
    static final String ATRIBUTO_SESION = "sesionId";

    @Override
    protected Principal determineUser(ServerHttpRequest solicitud, WebSocketHandler manejador,
            Map<String, Object> atributos) {
        var parametros = UriComponentsBuilder.fromUri(solicitud.getURI()).build().getQueryParams();
        String usuario = parametros.getFirst("usuario");
        String sesion = parametros.getFirst("sesion");
        if (usuario == null || usuario.isBlank()) {
            return null;
        }
        atributos.put(ATRIBUTO_USUARIO, usuario);
        if (sesion != null && !sesion.isBlank()) {
            atributos.put(ATRIBUTO_SESION, sesion);
        }
        return new UsuarioDelCanal(usuario);
    }

    /** Principal minimo: solo el nombre, que es lo unico que usa el broker. */
    record UsuarioDelCanal(String name) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }
}
