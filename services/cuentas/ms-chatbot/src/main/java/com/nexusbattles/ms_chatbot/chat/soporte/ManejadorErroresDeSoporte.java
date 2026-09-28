package com.nexusbattles.ms_chatbot.chat.soporte;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Regla 4 (problem details) para la bandeja de tickets del administrador:
// 409 con `motivo` TRANSICION_NO_PERMITIDA, igual que los motivos del chat.
@RestControllerAdvice(basePackages = "com.nexusbattles.ms_chatbot.chat.soporte")
public class ManejadorErroresDeSoporte {

    @ExceptionHandler(TransicionNoPermitidaException.class)
    public ResponseEntity<ProblemDetail> transicion(TransicionNoPermitidaException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problema.setTitle("Cambio no permitido");
        problema.setProperty("motivo", "TRANSICION_NO_PERMITIDA");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problema);
    }
}
