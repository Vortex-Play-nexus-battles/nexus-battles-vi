package com.nexusbattles.ms_subastas.panel.api;

import com.nexusbattles.ms_subastas.panel.service.OperacionRechazadaException;
import com.nexusbattles.ms_subastas.pujas.service.SubastaNoEncontradaException;
import com.nexusbattles.ms_subastas.seguridad.TokenInvalidoException;
import com.nexusbattles.ms_subastas.subastas.port.FinanzasPublicacionClientException;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClientException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Problem details (regla 4) del panel personal, conforme a
 * {@code ms-subastas-panel.yaml}: los rechazos de negocio con
 * {@code type .../errors/operacion-rechazada} y un {@code motivo} estable; una
 * dependencia caida, 503 sin detalles internos.
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)} por lo mismo que en los otros
 * manejadores del servicio: con problem-details habilitado, el de Spring Boot
 * ganaria sin avisar.
 */
@RestControllerAdvice(basePackages = "com.nexusbattles.ms_subastas.panel.api")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ManejadorDeErroresPanel {

    private static final Logger log = LoggerFactory.getLogger(ManejadorDeErroresPanel.class);
    private static final String BASE_TIPOS = "https://nexusbattles.upb.edu.co/errors/";

    @ExceptionHandler(OperacionRechazadaException.class)
    public ProblemDetail rechazada(OperacionRechazadaException ex, HttpServletRequest peticion) {
        ProblemDetail problema = problema(HttpStatusCode.valueOf(ex.getMotivo().estadoHttp()), "operacion-rechazada",
                "Operación rechazada", ex.getMessage(), peticion);
        problema.setProperty("motivo", ex.getMotivo().name());
        return problema;
    }

    @ExceptionHandler(SubastaNoEncontradaException.class)
    public ProblemDetail noEncontrada(SubastaNoEncontradaException ex, HttpServletRequest peticion) {
        return problema(HttpStatus.NOT_FOUND, "subasta-no-encontrada", "Subasta no encontrada", ex.getMessage(),
                peticion);
    }

    @ExceptionHandler(TokenInvalidoException.class)
    public ProblemDetail sinSesion(TokenInvalidoException ex, HttpServletRequest peticion) {
        return problema(HttpStatus.UNAUTHORIZED, "no-autenticado", "No autenticado",
                "Hace falta un token valido para usar tu panel de subastas.", peticion);
    }

    /** ms-finanzas o inventario no respondieron: no se cancelo ni se recogio nada. */
    @ExceptionHandler({FinanzasPublicacionClientException.class, InventarioClientException.class})
    public ProblemDetail dependencia(RuntimeException ex, HttpServletRequest peticion) {
        log.error("Dependencia no disponible en el panel de subastas: {}", ex.getMessage(), ex);
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "dependencia-no-disponible", "Servicio no disponible",
                "Un servicio necesario no respondió; no se hizo ningún cambio. Inténtalo de nuevo en un momento.",
                peticion);
    }

    private static ProblemDetail problema(HttpStatusCode estado, String tipo, String titulo, String detalle,
                                          HttpServletRequest peticion) {
        ProblemDetail problema = ProblemDetail.forStatus(estado);
        problema.setType(URI.create(BASE_TIPOS + tipo));
        problema.setTitle(titulo);
        problema.setDetail(detalle);
        problema.setInstance(URI.create(peticion.getRequestURI()));
        return problema;
    }
}
