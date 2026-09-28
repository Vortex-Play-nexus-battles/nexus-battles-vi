package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.api;

import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.BandejaDeMensajesDirectos;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.ConsultaInvalida;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.EnviarMensajeDirecto;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.MensajeDirecto;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.MensajeDirectoRechazado;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.MotivoDeRechazo;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.Remitente;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/**
 * Mensajes privados por REST — B6, {@code /mensajes-directos/**} de
 * {@code contracts/openapi/salas-partidas.yaml} 1.6.x.
 *
 * <p>FEEDBACK DEL PROFESOR, no requisito del documento (el 7.6 pide chat en las
 * salas y en la vista general).
 *
 * <p>El historial y las conversaciones se leen por aqui; el envio en vivo va
 * por STOMP y este {@code POST} es su respaldo cuando el WebSocket no esta
 * (degradacion controlada, riesgo #7 del Project Charter). El respaldo aplica
 * EXACTAMENTE las mismas reglas porque llama a la misma clase,
 * {@link EnviarMensajeDirecto}, y entrega igual: al destinatario por su cola
 * STOMP si esta conectado, y a las otras pestanas del remitente.
 *
 * <p><b>El {@code uid} propio sale del token, siempre.</b> La ruta solo nombra
 * al otro participante, asi que no hay forma de pedir la conversacion de otros
 * dos: {@code GET .../conversaciones/{B}/mensajes} con el token de C es la
 * conversacion C-B.
 */
@RestController
@RequestMapping("/api/v1/mensajes-directos")
public class MensajesDirectosController {

    private final EnviarMensajeDirecto enviarMensajeDirecto;
    private final BandejaDeMensajesDirectos bandeja;

    public MensajesDirectosController(EnviarMensajeDirecto enviarMensajeDirecto, BandejaDeMensajesDirectos bandeja) {
        this.enviarMensajeDirecto = enviarMensajeDirecto;
        this.bandeja = bandeja;
    }

    @GetMapping("/conversaciones")
    public List<ResumenDeConversacionResponse> misConversaciones(Authentication autenticacion) {
        UUID yo = RemitenteDelToken.de(autenticacion).id();
        return bandeja.conversacionesDe(yo).stream()
                .map(resumen -> ResumenDeConversacionResponse.de(resumen, yo))
                .toList();
    }

    @GetMapping("/conversaciones/{uidOtro}/mensajes")
    public List<MensajeDirectoResponse> mensajesDeConversacion(@PathVariable String uidOtro,
                                                               @RequestParam(required = false) String antesDe,
                                                               @RequestParam(required = false) Integer limite,
                                                               Authentication autenticacion) {
        UUID yo = RemitenteDelToken.de(autenticacion).id();
        return bandeja.historial(yo, uidDeConsulta(uidOtro), instanteDe(antesDe), limite).stream()
                .map(mensaje -> MensajeDirectoResponse.de(mensaje, yo))
                .toList();
    }

    /**
     * Envio de respaldo. Un reintento con el mismo {@code idCliente} devuelve
     * el mensaje ya guardado, tambien con 201: para quien reintenta, el efecto
     * es el mismo.
     */
    @PostMapping("/conversaciones/{uidOtro}/mensajes")
    public ResponseEntity<MensajeDirectoResponse> enviarMensajeDirecto(
            @PathVariable String uidOtro,
            @RequestBody(required = false) EnviarMensajeDirectoRequest cuerpo,
            Authentication autenticacion) {
        Remitente remitente = RemitenteDelToken.de(autenticacion);
        String idCliente = cuerpo == null ? null : cuerpo.idCliente();
        if (cuerpo == null) {
            throw new MensajeDirectoRechazado(MotivoDeRechazo.TEXTO_INVALIDO, null);
        }
        MensajeDirecto mensaje = enviarMensajeDirecto.enviar(remitente, uidDeDestino(uidOtro, idCliente),
                cuerpo.texto(), idCliente);
        return ResponseEntity.status(HttpStatus.CREATED).body(MensajeDirectoResponse.de(mensaje, remitente.id()));
    }

    @PostMapping("/conversaciones/{uidOtro}/leido")
    public ResponseEntity<Void> marcarConversacionLeida(@PathVariable String uidOtro,
                                                        Authentication autenticacion) {
        bandeja.marcarLeida(RemitenteDelToken.de(autenticacion).id(), uidDeConsulta(uidOtro));
        return ResponseEntity.noContent().build();
    }

    /**
     * Mismo problem details que el resto del servicio (regla 4), con el
     * {@code type} de cada motivo, y {@code Retry-After} cuando el limite dice
     * cuanto falta.
     */
    @ExceptionHandler(MensajeDirectoRechazado.class)
    ResponseEntity<ProblemDetail> rechazado(MensajeDirectoRechazado rechazo) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.valueOf(rechazo.estado()), rechazo.detalle());
        problema.setType(rechazo.tipo());
        problema.setTitle(rechazo.titulo());
        ResponseEntity.BodyBuilder respuesta = ResponseEntity.status(rechazo.estado());
        rechazo.reintentarEnSegundos().ifPresent(segundos ->
                respuesta.header(HttpHeaders.RETRY_AFTER, String.valueOf(segundos)));
        return respuesta.body(problema);
    }

    /** Un uid mal formado en una consulta es un parametro invalido (400 del contrato). */
    private static UUID uidDeConsulta(String uidOtro) {
        try {
            return UUID.fromString(uidOtro);
        } catch (IllegalArgumentException malFormado) {
            throw new ConsultaInvalida("uidOtro", "El otro jugador se nombra por su uid.");
        }
    }

    /** En un envio, un uid mal formado no nombra a nadie: igual que por STOMP. */
    private static UUID uidDeDestino(String uidOtro, String idCliente) {
        try {
            return UUID.fromString(uidOtro);
        } catch (IllegalArgumentException malFormado) {
            throw new MensajeDirectoRechazado(MotivoDeRechazo.DESTINATARIO_INEXISTENTE, idCliente);
        }
    }

    private static Instant instanteDe(String antesDe) {
        if (antesDe == null || antesDe.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(antesDe);
        } catch (DateTimeParseException malFormado) {
            throw new ConsultaInvalida("antesDe", "La fecha va en formato ISO-8601, por ejemplo 2026-09-25T18:00:00Z.");
        }
    }
}
