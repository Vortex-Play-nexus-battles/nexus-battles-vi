package com.nexusbattles.ms_chatbot.chat.api;

import com.nexusbattles.ms_chatbot.chat.entrada.InyeccionDetectada;
import com.nexusbattles.ms_chatbot.chat.limite.LimiteDeFrecuenciaExcedido;
import com.nexusbattles.ms_chatbot.chat.moderacion.ContenidoBloqueado;
import com.nexusbattles.ms_chatbot.chat.moderacion.ModeracionNoDisponible;
import com.nexusbattles.ms_chatbot.chat.soporte.CapturaDemasiadoGrandeException;
import com.nexusbattles.ms_chatbot.chat.soporte.CapturaNoAdmitidaException;
import com.nexusbattles.ms_chatbot.chat.soporte.CapturasCompletasException;
import com.nexusbattles.ms_chatbot.chat.soporte.SesionRequeridaException;
import com.nexusbattles.ms_chatbot.chat.soporte.SinConversacionException;
import com.nexusbattles.ms_chatbot.chat.soporte.TicketAbiertoException;
import com.nexusbattles.ms_chatbot.chat.soporte.TicketCerradoException;
import com.nexusbattles.ms_chatbot.chat.soporte.TicketNoEncontradoException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

// Regla 4: problem details. Los errores nuevos del chat (ms-chatbot.yaml
// 1.2.0) llevan ademas `motivo`, para que el frontend decida por el motivo y
// no por el texto.
//
// Va antes que el manejador de problem details de Spring Boot (orden 0): de
// los errores propios de Spring MVC solo declara los de una subida (1.14.0,
// sin la parte «archivo» o de mas de 2 MB), que asi llevan su `motivo`; el
// resto (validacion, metodo, tipo de contenido...) los sigue resolviendo el
// de Spring Boot como hasta ahora.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.nexusbattles.ms_chatbot.chat.api")
public class ManejadorErroresDelChat {

    @ExceptionHandler(LimiteDeFrecuenciaExcedido.class)
    public ResponseEntity<ProblemDetail> limite(LimiteDeFrecuenciaExcedido ex) {
        ProblemDetail problema = problema(HttpStatus.TOO_MANY_REQUESTS, "Demasiadas peticiones", ex.getMessage(),
            "LIMITE_DE_FRECUENCIA");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.segundosParaReintentar()))
            .body(problema);
    }

    @ExceptionHandler(ContenidoBloqueado.class)
    public ResponseEntity<ProblemDetail> bloqueado(ContenidoBloqueado ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
            .body(problema(HttpStatus.UNPROCESSABLE_CONTENT, "Mensaje no permitido", ex.getMessage(),
                "CONTENIDO_BLOQUEADO"));
    }

    // 1.4.0 (7.4.8): un intento de inyeccion de codigo no se procesa ni se
    // guarda. El detalle no repite lo que escribio ni dice que regla salto.
    @ExceptionHandler(InyeccionDetectada.class)
    public ResponseEntity<ProblemDetail> inyeccion(InyeccionDetectada ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
            .body(problema(HttpStatus.UNPROCESSABLE_CONTENT, "Mensaje no permitido", ex.getMessage(),
                "INYECCION_DETECTADA"));
    }

    @ExceptionHandler(ModeracionNoDisponible.class)
    public ResponseEntity<ProblemDetail> sinModeracion(ModeracionNoDisponible ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(problema(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo revisar el mensaje", ex.getMessage(),
                "MODERACION_NO_DISPONIBLE"));
    }

    // 1.3.0 — tickets de soporte.
    @ExceptionHandler(SesionRequeridaException.class)
    public ResponseEntity<ProblemDetail> sinSesion(SesionRequeridaException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(problema(HttpStatus.UNAUTHORIZED, "Inicia sesion", ex.getMessage(), "SESION_REQUERIDA"));
    }

    @ExceptionHandler(TicketAbiertoException.class)
    public ResponseEntity<ProblemDetail> ticketAbierto(TicketAbiertoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(problema(HttpStatus.CONFLICT, "Ya tienes un ticket abierto", ex.getMessage(), "TICKET_ABIERTO"));
    }

    // ms-chatbot.yaml 1.8.0: solicitud desde una conversacion sin preguntas.
    @ExceptionHandler(SinConversacionException.class)
    public ResponseEntity<ProblemDetail> sinConversacion(SinConversacionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(problema(HttpStatus.CONFLICT, "No hay conversacion", ex.getMessage(), "SIN_CONVERSACION"));
    }

    // ms-chatbot.yaml 1.14.0 (§7.4.6): capturas de una solicitud.
    @ExceptionHandler(TicketNoEncontradoException.class)
    public ResponseEntity<ProblemDetail> solicitudNoEncontrada(TicketNoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(problema(HttpStatus.NOT_FOUND, "No encontramos esa solicitud", ex.getMessage(),
                "SOLICITUD_NO_ENCONTRADA"));
    }

    @ExceptionHandler(TicketCerradoException.class)
    public ResponseEntity<ProblemDetail> solicitudCerrada(TicketCerradoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(problema(HttpStatus.CONFLICT, "La solicitud ya no admite capturas", ex.getMessage(),
                "SOLICITUD_CERRADA"));
    }

    @ExceptionHandler(CapturasCompletasException.class)
    public ResponseEntity<ProblemDetail> capturasCompletas(CapturasCompletasException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(problema(HttpStatus.CONFLICT, "La solicitud ya tiene todas sus capturas", ex.getMessage(),
                "CAPTURAS_COMPLETAS"));
    }

    @ExceptionHandler(CapturaNoAdmitidaException.class)
    public ResponseEntity<ProblemDetail> capturaNoAdmitida(CapturaNoAdmitidaException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .body(problema(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Captura no admitida", ex.getMessage(),
                "CAPTURA_NO_ADMITIDA"));
    }

    @ExceptionHandler({CapturaDemasiadoGrandeException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<ProblemDetail> capturaDemasiadoGrande(RuntimeException ex) {
        String detalle = ex instanceof CapturaDemasiadoGrandeException
            ? ex.getMessage() : "La captura pesa mas de 2 MB.";
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
            .body(problema(HttpStatus.CONTENT_TOO_LARGE, "Captura demasiado grande", detalle,
                "CAPTURA_DEMASIADO_GRANDE"));
    }

    // Sin la parte «archivo», o un cuerpo multipart que no se puede leer.
    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public ResponseEntity<ProblemDetail> sinArchivo(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(problema(HttpStatus.BAD_REQUEST, "Falta la captura",
                "Manda la captura en la parte «archivo» de un formulario multipart.", "CAPTURA_AUSENTE"));
    }

    private static ProblemDetail problema(HttpStatus estado, String titulo, String detalle, String motivo) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        problema.setProperty("motivo", motivo);
        return problema;
    }
}
