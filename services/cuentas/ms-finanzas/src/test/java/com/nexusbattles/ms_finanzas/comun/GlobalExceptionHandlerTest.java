package com.nexusbattles.ms_finanzas.comun;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import com.nexusbattles.ms_finanzas.common.exception.ReservaNoEncontradaException;
import com.nexusbattles.ms_finanzas.common.exception.ReservaYaLiberadaException;
import com.nexusbattles.ms_finanzas.common.exception.SaldoInsuficienteException;
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
    void saldoInsuficiente_esProblemDetail422ConTypeYTitle() {
        SaldoInsuficienteException ex = new SaldoInsuficienteException(
                "El jugador no tiene créditos suficientes para reservar 500");

        ProblemDetail respuesta = handler.manejarSaldoInsuficiente(ex);

        assertThat(respuesta.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(respuesta.getTitle()).isEqualTo("Saldo insuficiente");
        assertThat(respuesta.getDetail()).contains("créditos suficientes");
        assertThat(respuesta.getType().toString()).endsWith("/errors/saldo-insuficiente");
    }

    @Test
    void reservaYaLiberada_esProblemDetail409ConTypeYTitle() {
        ReservaYaLiberadaException ex = new ReservaYaLiberadaException(
                "La reserva ya fue liberada y no puede ser consumida.");

        ProblemDetail respuesta = handler.manejarReservaYaLiberada(ex);

        assertThat(respuesta.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(respuesta.getTitle()).isEqualTo("Reserva ya liberada");
        assertThat(respuesta.getDetail()).contains("liberada");
        // El type URI distingue este 409 del 409 de transaccion-ya-registrada,
        // para que Andrés pueda diferenciar los dos casos en su cliente.
        assertThat(respuesta.getType().toString()).endsWith("/errors/reserva-ya-liberada");
    }

    @Test
    void reservaNoEncontrada_esProblemDetail404ConTypeYTitle() {
        ReservaNoEncontradaException ex = new ReservaNoEncontradaException(
                "No existe la reserva con id abc-123");

        ProblemDetail respuesta = handler.manejarReservaNoEncontrada(ex);

        assertThat(respuesta.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(respuesta.getTitle()).isEqualTo("Reserva no encontrada");
        assertThat(respuesta.getDetail()).contains("abc-123");
        // El type URI distingue este 404 de negocio del 404 de Spring por
        // rutas inexistentes, que sale sin type.
        assertThat(respuesta.getType().toString()).endsWith("/errors/reserva-no-encontrada");
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
    void excepcionGenerica_esProblemDetail500ConMensajeGenericoYPropiedadExcepcion() {
        Exception ex = new NullPointerException("detalle interno que NO debe filtrarse");

        ProblemDetail respuesta = handler.manejarInesperada(ex);

        assertThat(respuesta.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(respuesta.getTitle()).isEqualTo("Error interno del servidor");
        // El detalle real de la excepción no debe llegar al cliente; se registra
        // en el log del servidor pero afuera se devuelve un mensaje neutro.
        assertThat(respuesta.getDetail()).doesNotContain("detalle interno");
        assertThat(respuesta.getDetail()).contains("inesperado");
        // El nombre de la clase de la excepción sí se expone como propiedad
        // estructurada para que quien integre pueda distinguir tipos de
        // excepciones no mapeadas sin depender del texto libre.
        assertThat(respuesta.getProperties()).containsEntry("excepcion", "NullPointerException");
    }
}
