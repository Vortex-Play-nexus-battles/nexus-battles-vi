package com.nexusbattles.ms_ecommerce.controller;

import com.nexusbattles.ms_ecommerce.catalogo.CatalogoNoDisponibleException;
import com.nexusbattles.ms_ecommerce.service.ProductoNoAgregableException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Errores de la tienda como problem details (RFC 9457, regla 4 de
 * plataforma), con {@code type} {@code urn:nexus:problema:*} como el resto de
 * modulos.
 *
 * <p>Solo se ocupa de los errores propios de la tienda. Los de Spring
 * (validacion, cuerpo ilegible, parametro de tipo equivocado...) los sigue
 * resolviendo el manejador que Boot registra con
 * {@code spring.mvc.problem-details.enabled=true}, y siguen siendo 400: por
 * eso esta clase no extiende {@code ResponseEntityExceptionHandler}, que lo
 * desplazaria.
 */
@RestControllerAdvice
public class ManejadorDeErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorDeErrores.class);

    private static final String PREFIJO_DE_TIPO = "urn:nexus:problema:";

    /**
     * Cuanto pedirle al cliente que espere antes de reintentar. Coincide con la
     * vigencia de la copia del catalogo en la vitrina: antes de eso no hay
     * nada nuevo que ir a buscar.
     */
    static final String SEGUNDOS_PARA_REINTENTAR = "30";

    /** El catalogo maestro no responde: 503 con {@code Retry-After}, nunca un 500 ni un 504 del borde. */
    @ExceptionHandler(CatalogoNoDisponibleException.class)
    ResponseEntity<ProblemDetail> catalogoNoDisponible(CatalogoNoDisponibleException excepcion,
                                                      HttpServletRequest peticion) {
        log.warn("Catalogo maestro no disponible: {}", excepcion.getMessage());
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.set(HttpHeaders.RETRY_AFTER, SEGUNDOS_PARA_REINTENTAR);
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "catalogo-no-disponible", "Catálogo no disponible",
                "El catálogo de productos no responde en este momento. Vuelve a intentarlo en unos segundos.",
                peticion, cabeceras);
    }

    /**
     * El producto no puede entrar al carrito. 422 cuando la peticion no se
     * puede cumplir con ese producto tal como es (no existe, no tiene precio en
     * moneda real); 409 cuando es su estado actual en el catalogo el que lo
     * impide (suspendido, agotado) y podria cambiar.
     */
    @ExceptionHandler(ProductoNoAgregableException.class)
    ResponseEntity<ProblemDetail> productoNoAgregable(ProductoNoAgregableException excepcion,
                                                      HttpServletRequest peticion) {
        String detalle = excepcion.getMessage();
        return switch (excepcion.motivo()) {
            case INEXISTENTE -> problema(HttpStatus.UNPROCESSABLE_CONTENT, "producto-inexistente",
                    "Producto inexistente", detalle, peticion, HttpHeaders.EMPTY);
            case NO_DISPONIBLE -> problema(HttpStatus.CONFLICT, "producto-no-disponible",
                    "Producto no disponible", detalle, peticion, HttpHeaders.EMPTY);
            case AGOTADO -> problema(HttpStatus.CONFLICT, "producto-agotado",
                    "Producto agotado", detalle, peticion, HttpHeaders.EMPTY);
            case SIN_PRECIO_EN_MONEDA_REAL -> problema(HttpStatus.UNPROCESSABLE_CONTENT,
                    "producto-sin-precio-en-moneda-real", "Producto sin precio en moneda real", detalle, peticion,
                    HttpHeaders.EMPTY);
        };
    }

    private static ResponseEntity<ProblemDetail> problema(HttpStatus estado, String tipo, String titulo,
                                                          String detalle, HttpServletRequest peticion,
                                                          HttpHeaders cabeceras) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setType(URI.create(PREFIJO_DE_TIPO + tipo));
        problema.setTitle(titulo);
        problema.setInstance(URI.create(peticion.getRequestURI()));
        return ResponseEntity.status(estado)
                .headers(cabeceras)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problema);
    }
}
