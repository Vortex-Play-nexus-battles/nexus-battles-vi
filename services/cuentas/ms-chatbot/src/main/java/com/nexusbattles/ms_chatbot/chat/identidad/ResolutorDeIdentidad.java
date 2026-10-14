package com.nexusbattles.ms_chatbot.chat.identidad;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

// B11 — quien habla con el chatbot (ms-chatbot.yaml 1.2.0).
//
// El fallo de la auditoria: ChatController.resolverIdentidad tomaba
// X-Id-Sesion-Anonima como la MISMA clave que el uid, asi que un visitante que
// mandara el uid de un jugador leia o borraba su historial. Ahora:
//
//   1. Con un JWT valido que identifica a un usuario, la identidad es el uid
//      del token y nada mas: la cabecera se ignora.
//   2. Sin el, la cabecera solo cuenta si es una sesion de visitante vigente
//      y registrada (SesionesAnonimas.validar): emitida por este servidor o
//      declarada por el navegador. Un uid, un valor sin forma o una sesion
//      vencida no identifican a nadie.
//   3. Solo al ENVIAR un mensaje se registra una sesion: la que declaro el
//      navegador (el asistente de la interfaz, #708, manda la suya siempre)
//      o, si no trae ninguna valida, una nueva que emite el servidor.
//      Consultar o borrar sin sesion no crea nada.
@Component
public class ResolutorDeIdentidad {

    private final SesionesAnonimas sesiones;

    public ResolutorDeIdentidad(SesionesAnonimas sesiones) {
        this.sesiones = sesiones;
    }

    /** Identidad para enviar un mensaje, con la sesion emitida si hizo falta una. */
    public record Resultado(IdentidadDelChat identidad, String sesionEmitida) {
    }

    // Usuario por su token, o visitante con una sesion valida. Vacio si no
    // hay ninguna de las dos: quien llama decide que responder (historial
    // vacio, nada que borrar, 404 al calificar).
    public Optional<IdentidadDelChat> resolver(Authentication autenticacion, String cabeceraDeSesion) {
        Optional<IdentidadDelChat> usuario = usuarioDelToken(autenticacion);
        if (usuario.isPresent()) {
            return usuario;
        }
        return sesiones.validar(cabeceraDeSesion).map(IdentidadDelChat::visitante);
    }

    // Para enviar un mensaje: si no hay usuario ni sesion valida, se registra
    // la que declaro el navegador (sin devolver nada: ya la tiene) o se emite
    // una nueva (limitada por origen) y se devuelve para que el cliente la
    // guarde.
    public Resultado resolverOEmitir(Authentication autenticacion, String cabeceraDeSesion, String origen) {
        Optional<IdentidadDelChat> conocida = resolver(autenticacion, cabeceraDeSesion);
        if (conocida.isPresent()) {
            return new Resultado(conocida.get(), null);
        }
        if (SesionesAnonimas.esDeclarada(cabeceraDeSesion)) {
            return new Resultado(IdentidadDelChat.visitante(sesiones.declarar(cabeceraDeSesion, origen)), null);
        }
        SesionesAnonimas.Emitida emitida = sesiones.emitir(origen);
        return new Resultado(IdentidadDelChat.visitante(emitida.sesion()), emitida.identificador());
    }

    /**
     * 7.4.9 (contracts/websocket/chatbot.yaml 1.0.0): quien abre el canal en
     * tiempo real, con las reglas de arriba y sin registrar nada. El nombre lo
     * pone el servidor, nunca el cliente (es el principal de la conexion):
     *
     * <ul>
     *   <li>un jugador, por el uid de su token: su nombre es el uid;</li>
     *   <li>si no, un visitante con una sesion emitida vigente o con la que
     *       declara el navegador (se registra con su primer mensaje, como en
     *       {@link #resolverOEmitir}): su nombre es {@code anonimo:} + la
     *       huella de la sesion, nunca la sesion;</li>
     *   <li>nadie mas: vacio, y el canal rechaza la conexion. A diferencia de
     *       {@link #resolverOEmitir} no se emite una sesion, porque en un
     *       CONNECT no hay respuesta HTTP donde devolverla.</li>
     * </ul>
     */
    public Optional<EnElCanal> enElCanal(Authentication autenticacion, String cabeceraDeSesion) {
        Optional<IdentidadDelChat> usuario = usuarioDelToken(autenticacion);
        if (usuario.isPresent()) {
            return Optional.of(new EnElCanal(usuario.get().uid(), true));
        }
        if (SesionesAnonimas.esDeclarada(cabeceraDeSesion) || sesiones.validar(cabeceraDeSesion).isPresent()) {
            return Optional.of(new EnElCanal(
                IdentidadDelChat.PREFIJO_VISITANTE + SesionesAnonimas.huella(cabeceraDeSesion), false));
        }
        return Optional.empty();
    }

    /** 7.4.9: el nombre de quien abre el canal y si es un jugador con sesion. */
    public record EnElCanal(String nombre, boolean jugador) {
    }

    // La identidad de un usuario es SOLO el claim `uid` (ADR-002): el `sub` es
    // su apodo y nunca se interpreta como uid (1.4.0; antes se aceptaba un
    // `sub` con forma de UUID cuando faltaba el uid, via IdentidadDelToken).
    // Un token sin uid valido (p. ej. una credencial de servicio) se trata
    // como si no viniera: el chat es de personas, y degradar a visitante es
    // lo mismo que ya se hace con un token vencido (HU-CHA-008).
    static final String CLAIM_UID = "uid";

    private static Optional<IdentidadDelChat> usuarioDelToken(Authentication autenticacion) {
        if (!(autenticacion instanceof JwtAuthenticationToken jwt)) {
            return Optional.empty();
        }
        Object claim = jwt.getToken().getClaims().get(CLAIM_UID);
        if (!(claim instanceof String uidDelToken) || uidDelToken.isBlank()) {
            return Optional.empty();
        }
        try {
            UUID uid = UUID.fromString(uidDelToken.strip());
            return Optional.of(IdentidadDelChat.usuario(uid, jwt.getToken().getTokenValue()));
        } catch (IllegalArgumentException uidSinForma) {
            return Optional.empty();
        }
    }
}
