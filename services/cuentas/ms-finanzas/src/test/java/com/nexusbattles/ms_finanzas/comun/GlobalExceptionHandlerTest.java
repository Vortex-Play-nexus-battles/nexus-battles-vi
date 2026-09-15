package com.nexusbattles.ms_finanzas.comun;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import com.nexusbattles.ms_finanzas.transacciones.TransaccionYaRegistradaException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void transaccionYaRegistrada_esProblemDetail409ConRefIdEnBody() {
        TransaccionYaRegistradaException ex = new TransaccionYaRegistradaException("ref-42");

        ProblemDetail respuesta = handler.manejarTransaccionDuplicada(ex);

        assertThat(respuesta.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(respuesta.getTitle()).isEqualTo("Transacción ya registrada");
        assertThat(respuesta.getDetail()).contains("ref-42");
        assertThat(respuesta.getType().toString()).contains("transaccion-ya-registrada");
        assertThat(respuesta.getProperties()).containsEntry("refId", "ref-42");
    }

    @Test
    void argumentoInvalido_esProblemDetail400() {
        IllegalArgumentException ex = new IllegalArgumentException("monto no puede ser negativo");

        ProblemDetail respuesta = handler.manejarArgumentoInvalido(ex);

        assertThat(respuesta.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(respuesta.getTitle()).isEqualTo("Argumento inválido");
        assertThat(respuesta.getDetail()).contains("monto");
    }

    @Test
    void excepcionGenerica_esProblemDetail500ConMensajeGenerico() {
        Exception ex = new RuntimeException("detalle interno que NO debe filtrarse");

        ProblemDetail respuesta = handler.manejarInesperada(ex);

        assertThat(respuesta.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(respuesta.getTitle()).isEqualTo("Error interno del servidor");
        // El detalle real de la excepción no debe llegar al cliente; se registra
        // en el log del servidor pero afuera se devuelve un mensaje neutro.
        assertThat(respuesta.getDetail()).doesNotContain("detalle interno");
        assertThat(respuesta.getDetail()).contains("inesperado");
    }
}
