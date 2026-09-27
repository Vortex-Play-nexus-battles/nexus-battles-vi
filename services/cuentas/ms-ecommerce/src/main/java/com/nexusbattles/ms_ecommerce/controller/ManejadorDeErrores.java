package com.nexusbattles.ms_ecommerce.controller;

import com.nexusbattles.ms_ecommerce.catalogo.CatalogoNoDisponibleException;
import com.nexusbattles.ms_ecommerce.compra.CompraRechazadaException;
import com.nexusbattles.ms_ecommerce.compra.OrdenDto;
import com.nexusbattles.ms_ecommerce.compra.OrdenInexistenteException;
import com.nexusbattles.ms_ecommerce.compra.pago.DatosDePagoInvalidosException;
import com.nexusbattles.ms_ecommerce.precios.Moneda;
import com.nexusbattles.ms_ecommerce.precios.MonedaNoDisponibleException;
import com.nexusbattles.ms_ecommerce.service.CantidadNoPermitidaException;
import com.nexusbattles.ms_ecommerce.service.LineaInexistenteException;
import com.nexusbattles.ms_ecommerce.service.ProductoNoAgregableException;
import com.nexusbattles.ms_ecommerce.service.ProductoNoEncontradoException;
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
 *
 * <p>B5 — los rechazos de la compra que ya crearon una orden la devuelven en
 * las propiedades {@code ordenId}, {@code estado} y {@code motivo} (contrato
 * 1.4.0): la interfaz sabe que paso y con que orden sin otra peticion.
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

    /** Tras un pago en curso: lo que tarda, como mucho, una compra en terminar sus pasos. */
    static final String SEGUNDOS_PARA_CONSULTAR_LA_COMPRA = "5";

    /** El catalogo maestro no responde: 503 con {@code Retry-After}, nunca un 500 ni un 504 del borde. */
    @ExceptionHandler(CatalogoNoDisponibleException.class)
    ResponseEntity<ProblemDetail> catalogoNoDisponible(CatalogoNoDisponibleException excepcion,
                                                      HttpServletRequest peticion) {
        log.warn("Catalogo maestro no disponible: {}", excepcion.getMessage());
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "catalogo-no-disponible", "Catálogo no disponible",
                "El catálogo de productos no responde en este momento. Vuelve a intentarlo en unos segundos.",
                peticion, reintentarEn(SEGUNDOS_PARA_REINTENTAR));
    }

    /**
     * El producto no puede entrar al carrito (o comprarse). 422 cuando la
     * peticion no se puede cumplir con ese producto tal como es (no existe, no
     * tiene precio en moneda real); 409 cuando es su estado actual en el
     * catalogo el que lo impide (suspendido, agotado) y podria cambiar.
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

    /** Lista de deseos: el catalogo no tiene el producto de la ruta (404, contrato 1.3.0). */
    @ExceptionHandler(ProductoNoEncontradoException.class)
    ResponseEntity<ProblemDetail> productoNoEncontrado(ProductoNoEncontradoException excepcion,
                                                       HttpServletRequest peticion) {
        return problema(HttpStatus.NOT_FOUND, "producto-inexistente", "Producto inexistente",
                excepcion.getMessage(), peticion, HttpHeaders.EMPTY);
    }

    /** 1.4.0: USD o EUR sin tasa de cambio. */
    @ExceptionHandler(MonedaNoDisponibleException.class)
    ResponseEntity<ProblemDetail> monedaNoDisponible(MonedaNoDisponibleException excepcion,
                                                     HttpServletRequest peticion) {
        ResponseEntity<ProblemDetail> respuesta = problema(HttpStatus.UNPROCESSABLE_CONTENT, "moneda-no-disponible",
                "Moneda no disponible", excepcion.getMessage(), peticion, HttpHeaders.EMPTY);
        respuesta.getBody().setProperty("monedasDisponibles",
                excepcion.disponibles().stream().map(Moneda::name).toList());
        return respuesta;
    }

    /** 1.4.0: la cantidad de una linea (rango, tope por linea, tiraje). */
    @ExceptionHandler(CantidadNoPermitidaException.class)
    ResponseEntity<ProblemDetail> cantidadNoPermitida(CantidadNoPermitidaException excepcion,
                                                      HttpServletRequest peticion) {
        String detalle = excepcion.getMessage();
        return switch (excepcion.motivo()) {
            case FUERA_DE_RANGO -> problema(HttpStatus.BAD_REQUEST, "cantidad-fuera-de-rango",
                    "Cantidad fuera de rango", detalle, peticion, HttpHeaders.EMPTY);
            case MAXIMA_POR_LINEA -> problema(HttpStatus.CONFLICT, "cantidad-maxima-por-linea",
                    "Cantidad máxima por línea", detalle, peticion, HttpHeaders.EMPTY);
            case TIRAJE_INSUFICIENTE -> {
                ResponseEntity<ProblemDetail> respuesta = problema(HttpStatus.CONFLICT, "tiraje-insuficiente",
                        "No quedan tantas unidades", detalle, peticion, HttpHeaders.EMPTY);
                respuesta.getBody().setProperty("disponibles", excepcion.disponibles());
                yield respuesta;
            }
        };
    }

    /** 1.4.0: la linea no esta en el carrito de quien la pide. */
    @ExceptionHandler(LineaInexistenteException.class)
    ResponseEntity<ProblemDetail> lineaInexistente(LineaInexistenteException excepcion, HttpServletRequest peticion) {
        return problema(HttpStatus.NOT_FOUND, "linea-inexistente", "Línea inexistente", excepcion.getMessage(),
                peticion, HttpHeaders.EMPTY);
    }

    /** 1.4.0: el formulario de pago; dice el campo, nunca el valor. */
    @ExceptionHandler(DatosDePagoInvalidosException.class)
    ResponseEntity<ProblemDetail> datosDePagoInvalidos(DatosDePagoInvalidosException excepcion,
                                                       HttpServletRequest peticion) {
        ResponseEntity<ProblemDetail> respuesta = problema(HttpStatus.BAD_REQUEST, "datos-de-pago-invalidos",
                "Datos de pago inválidos", excepcion.getMessage(), peticion, HttpHeaders.EMPTY);
        respuesta.getBody().setProperty("campo", excepcion.campo());
        return respuesta;
    }

    /** 1.4.0: una orden que no existe o no es del jugador. */
    @ExceptionHandler(OrdenInexistenteException.class)
    ResponseEntity<ProblemDetail> ordenInexistente(OrdenInexistenteException excepcion, HttpServletRequest peticion) {
        return problema(HttpStatus.NOT_FOUND, "orden-inexistente", "Orden inexistente", excepcion.getMessage(),
                peticion, HttpHeaders.EMPTY);
    }

    /** 1.4.0: los rechazos de la compra, con la orden si llego a crearse. */
    @ExceptionHandler(CompraRechazadaException.class)
    ResponseEntity<ProblemDetail> compraRechazada(CompraRechazadaException excepcion, HttpServletRequest peticion) {
        String detalle = excepcion.getMessage();
        ResponseEntity<ProblemDetail> respuesta = switch (excepcion.motivo()) {
            case CLAVE_INVALIDA -> problema(HttpStatus.BAD_REQUEST, "clave-de-idempotencia-requerida",
                    "Falta la clave de idempotencia", detalle, peticion, HttpHeaders.EMPTY);
            case CLAVE_REUTILIZADA -> problema(HttpStatus.CONFLICT, "clave-de-idempotencia-reutilizada",
                    "Clave de idempotencia reutilizada", detalle, peticion, HttpHeaders.EMPTY);
            case COMPRA_NO_DISPONIBLE -> problema(HttpStatus.SERVICE_UNAVAILABLE, "compra-no-disponible",
                    "Compra no disponible", detalle, peticion, reintentarEn(SEGUNDOS_PARA_REINTENTAR));
            case CARRITO_VACIO -> problema(HttpStatus.BAD_REQUEST, "carrito-vacio", "Carrito vacío", detalle,
                    peticion, HttpHeaders.EMPTY);
            case COMPRA_EN_CURSO -> problema(HttpStatus.CONFLICT, "compra-en-curso", "Compra en curso", detalle,
                    peticion, reintentarEn(SEGUNDOS_PARA_CONSULTAR_LA_COMPRA));
            case PAGO_RECHAZADO -> problema(HttpStatus.PAYMENT_REQUIRED, "pago-rechazado", "Pago rechazado",
                    detalle, peticion, HttpHeaders.EMPTY);
            case PASARELA_NO_DISPONIBLE -> problema(HttpStatus.SERVICE_UNAVAILABLE, "pasarela-no-disponible",
                    "Pasarela no disponible", detalle, peticion, reintentarEn(SEGUNDOS_PARA_REINTENTAR));
            case COMPRA_REEMBOLSADA -> problema(HttpStatus.CONFLICT, "compra-reembolsada", "Compra reembolsada",
                    detalle, peticion, HttpHeaders.EMPTY);
        };
        OrdenDto orden = excepcion.orden();
        if (orden != null) {
            respuesta.getBody().setProperty("ordenId", orden.id().toString());
            respuesta.getBody().setProperty("estado", orden.estado());
            if (orden.motivo() != null) {
                respuesta.getBody().setProperty("motivo", orden.motivo());
            }
        }
        return respuesta;
    }

    private static HttpHeaders reintentarEn(String segundos) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.set(HttpHeaders.RETRY_AFTER, segundos);
        return cabeceras;
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
