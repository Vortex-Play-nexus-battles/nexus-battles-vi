package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.canal;

import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.EnviarMensajeDirecto;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.MensajeDirectoRechazado;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.MotivoDeRechazo;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.api.RemitenteDelToken;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

/**
 * Envio de un mensaje privado por STOMP — B6, canal {@code enviarMensajeDirecto}
 * de {@code contracts/websocket/mensajes-directos.yaml}.
 *
 * <p>FEEDBACK DEL PROFESOR, no requisito del documento: el 7.6 pide chat en
 * las salas y en la vista general. Va sobre el mismo broker y la misma
 * autenticacion del CONNECT que el chat; no es un servicio nuevo.
 *
 * <p>El remitente es SIEMPRE el principal de la sesion; el destino solo dice a
 * quien. Las reglas las aplica {@link EnviarMensajeDirecto}, la misma clase que
 * atiende el envio de respaldo por REST.
 *
 * <p>Los rechazos no son un {@code ERROR} de STOMP (eso cerraria la conexion):
 * vuelven solo a la sesion que escribio, por su cola
 * {@code /usuario/cola/mensajes-directos}, como {@code {tipo: RECHAZO, motivo,
 * idCliente}}. El texto para la persona lo pone la interfaz a partir del motivo.
 */
@Controller
public class MensajesDirectosStompController {

    private final EnviarMensajeDirecto enviarMensajeDirecto;

    public MensajesDirectosStompController(EnviarMensajeDirecto enviarMensajeDirecto) {
        this.enviarMensajeDirecto = enviarMensajeDirecto;
    }

    @MessageMapping("/mensajes-directos/{uidDestino}")
    public void enviar(@DestinationVariable String uidDestino,
                       @Payload(required = false) MensajeSalienteRequest cuerpo,
                       Principal principal) {
        String idCliente = cuerpo == null ? null : cuerpo.idCliente();
        if (cuerpo == null) {
            throw new MensajeDirectoRechazado(MotivoDeRechazo.TEXTO_INVALIDO, null);
        }
        enviarMensajeDirecto.enviar(RemitenteDelToken.de(principal), destinoDe(uidDestino, idCliente),
                cuerpo.texto(), idCliente);
    }

    @MessageExceptionHandler(MensajeDirectoRechazado.class)
    @SendToUser(destinations = EntregaStomp.COLA, broadcast = false)
    public EnvioRechazadoPayload rechazado(MensajeDirectoRechazado rechazo) {
        return EnvioRechazadoPayload.de(rechazo);
    }

    /** Un cuerpo que no es el JSON del contrato es un texto invalido, no un fallo del servidor. */
    @MessageExceptionHandler(MessageConversionException.class)
    @SendToUser(destinations = EntregaStomp.COLA, broadcast = false)
    public EnvioRechazadoPayload ilegible(MessageConversionException error) {
        return new EnvioRechazadoPayload(EnvioRechazadoPayload.TIPO, MotivoDeRechazo.TEXTO_INVALIDO.name(), null);
    }

    /** Un destino que no es un uid no nombra a nadie: no existe. */
    private static UUID destinoDe(String uidDestino, String idCliente) {
        try {
            return UUID.fromString(uidDestino);
        } catch (IllegalArgumentException malFormado) {
            throw new MensajeDirectoRechazado(MotivoDeRechazo.DESTINATARIO_INEXISTENTE, idCliente);
        }
    }
}
