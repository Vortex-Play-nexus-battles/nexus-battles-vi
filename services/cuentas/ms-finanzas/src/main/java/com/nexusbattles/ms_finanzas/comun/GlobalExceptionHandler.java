package com.nexusbattles.ms_finanzas.comun;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.nexusbattles.ms_finanzas.common.exception.ReservaNoEncontradaException;
import com.nexusbattles.ms_finanzas.common.exception.ReservaYaLiberadaException;
import com.nexusbattles.ms_finanzas.common.exception.SaldoInsuficienteException;
import com.nexusbattles.ms_finanzas.partidas.PartidaYaProcesadaException;
import com.nexusbattles.ms_finanzas.transacciones.TransaccionYaRegistradaException;

/**
 * Traduce las excepciones del servicio a respuestas RFC 7807 (regla 4 de
 * plataforma). {@code spring.mvc.problem-details.enabled=true} ya activa el
 * formato Problem Details a nivel Spring; este handler solo se encarga de
 * las excepciones propias del dominio y de darles un {@code type} URI
 * consistente con la convención del proyecto.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String BASE_TYPE = "https://nexusbattles.upb.edu.co/errors/";

    @ExceptionHandler(TransaccionYaRegistradaException.class)
    public ProblemDetail manejarTransaccionDuplicada(TransaccionYaRegistradaException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
        problema.setType(URI.create(BASE_TYPE + "transaccion-ya-registrada"));
        problema.setTitle("Transacción ya registrada");
        problema.setProperty("refId", ex.getRefId());
        return problema;
    }

    /**
     * HU-PAG-001, ms-subastas (Andrés) — cuando un jugador no tiene créditos
     * suficientes para una reserva o débito. Antes caía al catch-all y se
     * devolvía 500 "inténtalo más tarde", que es un rechazo que va a fallar
     * idéntico siempre. Con 422 + type URI estable el llamador puede tratarlo
     * como respuesta de negocio, no como avería.
     */
    @ExceptionHandler(SaldoInsuficienteException.class)
    public ProblemDetail manejarSaldoInsuficiente(SaldoInsuficienteException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problema.setType(URI.create(BASE_TYPE + "saldo-insuficiente"));
        problema.setTitle("Saldo insuficiente");
        return problema;
    }

    /**
     * HU-PAG-001, ms-subastas — cuando se referencia una reserva por id que no
     * existe. Se distingue del 404 de Spring por rutas inexistentes (que sale
     * sin `type`) precisamente por el `type` URI: el llamador filtra por type,
     * no por status, para saber si es un estado real de negocio o un bug de
     * URL. Detalle señalado por Andrés al probar el servicio en vivo.
     */
    @ExceptionHandler(ReservaNoEncontradaException.class)
    public ProblemDetail manejarReservaNoEncontrada(ReservaNoEncontradaException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
        problema.setType(URI.create(BASE_TYPE + "reserva-no-encontrada"));
        problema.setTitle("Reserva no encontrada");
        return problema;
    }

    /**
     * HU-PAG-001 — se intenta consumir una reserva que ya fue liberada.
     * Antes del arreglo de Juan Diego (mensaje del 16/sep sobre el bug de
     * dinero) el servicio seguía adelante y volvía a debitar; ahora
     * {@code CreditoService.consumir()} lanza {@link ReservaYaLiberadaException}
     * al detectar el estado LIBERADA. Se mapea a 409 con {@code type} URI
     * estable para que ms-subastas pueda distinguir este caso ("es tarde,
     * la reserva ya se te devolvió") del 422 de saldo insuficiente y del
     * 404 de reserva no encontrada.
     */
    @ExceptionHandler(ReservaYaLiberadaException.class)
    public ProblemDetail manejarReservaYaLiberada(ReservaYaLiberadaException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
        problema.setType(URI.create(BASE_TYPE + "reserva-ya-liberada"));
        problema.setTitle("Reserva ya liberada");
        return problema;
    }

    /**
     * HU-JUE-012 — el resultado de una partida se reintenta con el mismo
     * {@code partidaId} (por ejemplo, ms-salas-partidas perdió la respuesta
     * y reintentó). El servicio garantiza que no se acredite dos veces y
     * responde 409 con {@code type} URI estable, análogo al 409 de
     * transacción ya registrada. El {@code partidaId} viaja como propiedad
     * estructurada para que el llamador lo pueda reconciliar.
     */
    @ExceptionHandler(PartidaYaProcesadaException.class)
    public ProblemDetail manejarPartidaYaProcesada(PartidaYaProcesadaException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
        problema.setType(URI.create(BASE_TYPE + "partida-ya-procesada"));
        problema.setTitle("Partida ya procesada");
        problema.setProperty("partidaId", ex.getPartidaId());
        return problema;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail manejarArgumentoInvalido(IllegalArgumentException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage());
        problema.setType(URI.create(BASE_TYPE + "argumento-invalido"));
        problema.setTitle("Argumento inválido");
        return problema;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail manejarInesperada(Exception ex) {
        // Se registra la excepción real en el log pero al cliente se le
        // devuelve un mensaje genérico — no queremos filtrar detalles de
        // implementación en el cuerpo de la respuesta. Se agrega la clase de
        // la excepción como propiedad estructurada para que un llamador que
        // integre contra este servicio pueda distinguir "excepción X no
        // mapeada" de "excepción Y no mapeada" sin depender del texto libre
        // ni obligar a leer los logs del servidor.
        log.error("Error no controlado en ms-finanzas", ex);
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error inesperado. Inténtalo de nuevo más tarde.");
        problema.setType(URI.create(BASE_TYPE + "error-interno"));
        problema.setTitle("Error interno del servidor");
        problema.setProperty("excepcion", ex.getClass().getSimpleName());
        return problema;
    }
}
