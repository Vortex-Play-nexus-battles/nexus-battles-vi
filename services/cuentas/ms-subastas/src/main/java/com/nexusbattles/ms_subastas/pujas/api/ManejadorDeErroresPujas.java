package com.nexusbattles.ms_subastas.pujas.api;

import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClientException;
import com.nexusbattles.ms_subastas.pujas.service.PujaRechazadaException;
import com.nexusbattles.ms_subastas.pujas.service.SubastaNoEncontradaException;
import com.nexusbattles.ms_subastas.seguridad.TokenInvalidoException;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClientException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.EnumSet;
import java.util.Set;

/**
 * Traduce a problem+json (regla 4 de plataforma) lo que lanzan los endpoints de
 * HU-SUB-004, conforme a {@code contracts/openapi/ms-subastas-pujas.yaml}.
 *
 * <p>Es un manejador propio y no una ampliacion de
 * {@code ManejadorDeErroresSubastas}: ese esta acotado a {@code subastas.api}
 * por decision explicita de su autor, para que el listado y las pujas puedan
 * evolucionar sin pisarse. Este hace lo mismo del lado de {@code pujas.api}.
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)} por el mismo motivo documentado alli:
 * con problem-details habilitado, Spring Boot registra su propio manejador, y
 * sin prioridad explicita gana el suyo sin dar ningun error visible.
 */
@RestControllerAdvice(basePackages = "com.nexusbattles.ms_subastas.pujas.api")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ManejadorDeErroresPujas {

    private static final Logger log = LoggerFactory.getLogger(ManejadorDeErroresPujas.class);

    private static final String BASE_TIPOS = "https://nexusbattles.upb.edu.co/errors/";

    /**
     * Motivos que son una carrera perdida, no un error del jugador: cuando armo
     * la peticion era valida, y dejo de serlo porque otro se le adelanto. Van
     * con 409 porque el cliente puede releer la subasta y reintentar con datos
     * frescos; los demas motivos van con 422, porque reintentar tal cual
     * volveria a fallar igual.
     */
    private static final Set<PujaRechazadaException.Motivo> CARRERAS = EnumSet.of(
            PujaRechazadaException.Motivo.SUBASTA_NO_ACTIVA,
            PujaRechazadaException.Motivo.OFERTA_INSUFICIENTE);

    @ExceptionHandler(TokenInvalidoException.class)
    public ProblemDetail manejarTokenInvalido(TokenInvalidoException ex, HttpServletRequest peticion) {
        // Sin cuerpo del error original: decirle a quien prueba tokens si fallo
        // la firma, la expiracion o el claim uid es regalarle informacion.
        ProblemDetail problema = problema(HttpStatus.UNAUTHORIZED, "no-autenticado",
                "No autenticado", "Hace falta un token valido para participar en una subasta.", peticion);
        log.debug("Peticion rechazada por token invalido: {}", ex.getMessage());
        return problema;
    }

    @ExceptionHandler(SubastaNoEncontradaException.class)
    public ProblemDetail manejarSubastaNoEncontrada(SubastaNoEncontradaException ex, HttpServletRequest peticion) {
        return problema(HttpStatus.NOT_FOUND, "subasta-no-encontrada",
                "Subasta no encontrada", ex.getMessage(), peticion);
    }

    @ExceptionHandler(PujaRechazadaException.class)
    public ProblemDetail manejarPujaRechazada(PujaRechazadaException ex, HttpServletRequest peticion) {
        // UNPROCESSABLE_CONTENT y no UNPROCESSABLE_ENTITY: es el mismo 422, pero
        // la RFC 9110 le cambio el nombre y Spring 7 dejo el viejo obsoleto.
        HttpStatus estado = CARRERAS.contains(ex.getMotivo())
                ? HttpStatus.CONFLICT
                : HttpStatus.UNPROCESSABLE_CONTENT;

        ProblemDetail problema = problema(estado, "puja-rechazada",
                "Puja rechazada", ex.getMessage(), peticion);
        problema.setProperty("motivo", ex.getMotivo().name());
        return problema;
    }

    /**
     * Saldo insuficiente es una respuesta valida de ms-finanzas, no una averia:
     * sale como 422 con su motivo, igual que cualquier otro rechazo de negocio.
     * El resto de motivos (una reserva que no existe, o que ya se libero) son
     * inconsistencias entre los dos servicios: 500, y al log con la traza, que
     * es donde hay que mirarlo.
     */
    @ExceptionHandler(CreditoClientException.class)
    public ProblemDetail manejarFalloDeCreditos(CreditoClientException ex, HttpServletRequest peticion) {
        if (ex.getMotivo() == CreditoClientException.Motivo.SALDO_INSUFICIENTE) {
            ProblemDetail problema = problema(HttpStatus.UNPROCESSABLE_CONTENT, "puja-rechazada",
                    "Puja rechazada", "No tienes creditos suficientes para esta operacion.", peticion);
            problema.setProperty("motivo", CreditoClientException.Motivo.SALDO_INSUFICIENTE.name());
            return problema;
        }

        log.error("Inconsistencia con ms-finanzas ({}): {}", ex.getMotivo(), ex.getMessage(), ex);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "error-de-creditos",
                "Error al mover creditos",
                "No se pudo completar la operacion de creditos. Intentalo de nuevo en un momento.", peticion);
    }

    @ExceptionHandler(InventarioClientException.class)
    public ProblemDetail manejarFalloDeInventario(InventarioClientException ex, HttpServletRequest peticion) {
        log.error("Error en ms-inventario: {}", ex.getMessage(), ex);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "error-de-inventario",
                "Error en el inventario",
                "No se pudo completar la transferencia del producto. Intentalo de nuevo en un momento.", peticion);
    }

    private ProblemDetail problema(HttpStatus estado, String tipo, String titulo,
                                   String detalle, HttpServletRequest peticion) {
        ProblemDetail problema = ProblemDetail.forStatus(estado);
        problema.setType(URI.create(BASE_TIPOS + tipo));
        problema.setTitle(titulo);
        problema.setDetail(detalle);
        problema.setInstance(URI.create(peticion.getRequestURI()));
        return problema;
    }
}
