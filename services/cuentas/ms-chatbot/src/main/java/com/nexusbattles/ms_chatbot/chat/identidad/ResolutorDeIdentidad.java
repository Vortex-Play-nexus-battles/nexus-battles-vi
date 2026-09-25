package com.nexusbattles.ms_chatbot.chat.identidad;

import com.nexusbattles.comun.seguridad.IdentidadDelToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

// B11 — quien habla con el chatbot (ms-chatbot.yaml 2.0.0).
//
// El fallo de la auditoria: ChatController.resolverIdentidad tomaba
// X-Id-Sesion-Anonima como la MISMA clave que el uid, asi que un visitante que
// mandara el uid de un jugador leia o borraba su historial. Ahora:
//
//   1. Con un JWT valido que identifica a un usuario, la identidad es el uid
//      del token y nada mas: la cabecera se ignora.
//   2. Sin el, la cabecera solo cuenta si es una sesion que emitio este
//      servidor y sigue vigente (SesionesAnonimas.validar). Un uid, un valor
//      inventado o una sesion vencida no identifican a nadie.
//   3. Solo al ENVIAR un mensaje se le emite una sesion nueva a quien no tenga
//      una valida; consultar o borrar sin sesion no crea nada.
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

    // Para enviar un mensaje: si no hay usuario ni sesion valida, se emite una
    // sesion nueva (limitada por origen) y se devuelve para que el cliente la
    // guarde.
    public Resultado resolverOEmitir(Authentication autenticacion, String cabeceraDeSesion, String origen) {
        Optional<IdentidadDelChat> conocida = resolver(autenticacion, cabeceraDeSesion);
        if (conocida.isPresent()) {
            return new Resultado(conocida.get(), null);
        }
        SesionesAnonimas.Emitida emitida = sesiones.emitir(origen);
        return new Resultado(IdentidadDelChat.visitante(emitida.sesion()), emitida.identificador());
    }

    // Un token que no identifica a un usuario (sin uid ni sujeto UUID, p. ej.
    // una credencial de servicio) se trata como si no viniera: el chat es de
    // personas, y degradar a visitante es lo mismo que ya se hace con un token
    // vencido (HU-CHA-008).
    private static Optional<IdentidadDelChat> usuarioDelToken(Authentication autenticacion) {
        if (!(autenticacion instanceof JwtAuthenticationToken jwt)) {
            return Optional.empty();
        }
        try {
            UUID uid = IdentidadDelToken.idDe(jwt.getToken());
            return Optional.of(IdentidadDelChat.usuario(uid, jwt.getToken().getTokenValue()));
        } catch (IllegalArgumentException | NullPointerException sinUsuario) {
            return Optional.empty();
        }
    }
}
