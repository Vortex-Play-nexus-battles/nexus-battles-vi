package com.nexusbattles.ms_chatbot.chat.api;

import com.nexusbattles.ms_chatbot.chat.limite.LimiteDeFrecuenciaExcedido;
import com.nexusbattles.ms_chatbot.chat.moderacion.ContenidoBloqueado;
import com.nexusbattles.ms_chatbot.chat.moderacion.ModeracionNoDisponible;
import com.nexusbattles.ms_chatbot.chat.soporte.SesionRequeridaException;
import com.nexusbattles.ms_chatbot.chat.soporte.TicketAbiertoException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Regla 4: problem details. Los errores nuevos del chat (ms-chatbot.yaml
// 1.2.0) llevan ademas `motivo`, para que el frontend decida por el motivo y
// no por el texto.
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

    private static ProblemDetail problema(HttpStatus estado, String titulo, String detalle, String motivo) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        problema.setProperty("motivo", motivo);
        return problema;
    }
}
