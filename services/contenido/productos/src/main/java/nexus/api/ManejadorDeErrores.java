package nexus.api;

import java.net.URI;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import nexus.dominio.ClaveDeIdempotenciaReutilizadaException;
import nexus.dominio.ModificacionProductoInvalidaException;
import nexus.dominio.ProductoNoEncontradoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class ManejadorDeErrores {

        private static final Logger BITACORA = LoggerFactory.getLogger(ManejadorDeErrores.class);

        @ExceptionHandler(MethodArgumentNotValidException.class)
        ResponseEntity<ProblemDetail> manejarValidacion(
                        MethodArgumentNotValidException excepcion,
                        HttpServletRequest solicitud) {

                String detalle = excepcion.getBindingResult()
                        .getAllErrors()
                        .stream()
                        .map(error -> {
                                String mensaje = Objects.requireNonNullElse(
                                        error.getDefaultMessage(),
                                        "Valor inválido");
                                if (error instanceof FieldError campo) {
                                        return campo.getField() + ": " + mensaje;
                                }
                                return mensaje;
                        })
                        .distinct()
                        .collect(Collectors.joining("; "));

                return respuesta(
                        HttpStatus.BAD_REQUEST,
                        "Solicitud inválida",
                        detalle,
                        "urn:nexus:problema:solicitud-invalida",
                        solicitud);
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        ResponseEntity<ProblemDetail> manejarJsonInvalido(
                        HttpMessageNotReadableException excepcion,
                        HttpServletRequest solicitud) {

                return respuesta(
                        HttpStatus.BAD_REQUEST,
                        "Solicitud inválida",
                        "El cuerpo JSON está incompleto, mal formado o contiene un valor no permitido",
                        "urn:nexus:problema:solicitud-invalida",
                        solicitud);
        }

        // R16 — parametros de consulta del listado (GET /api/v1/productos).
        //
        // Sin estos dos manejadores, un `size=51` o un `tipo=POCION` caian en
        // el `Exception.class` de abajo y respondian 500, cuando el contrato
        // 1.2.0 promete 400 con Problem Details. El primero recoge la
        // validacion de Bean Validation sobre los @RequestParam (page >= 0,
        // size entre 1 y 50); el segundo, un valor que ni siquiera se puede
        // convertir al tipo del parametro (un numero mal escrito o un valor
        // fuera de la enumeracion).
        @ExceptionHandler(HandlerMethodValidationException.class)
        ResponseEntity<ProblemDetail> manejarParametrosInvalidos(
                        HandlerMethodValidationException excepcion,
                        HttpServletRequest solicitud) {

                String detalle = excepcion.getAllErrors()
                        .stream()
                        .map(error -> Objects.requireNonNullElse(
                                error.getDefaultMessage(),
                                "Valor inválido"))
                        .distinct()
                        .collect(Collectors.joining("; "));

                return respuesta(
                        HttpStatus.BAD_REQUEST,
                        "Solicitud inválida",
                        detalle,
                        "urn:nexus:problema:solicitud-invalida",
                        solicitud);
        }

        @ExceptionHandler(MethodArgumentTypeMismatchException.class)
        ResponseEntity<ProblemDetail> manejarParametroConFormatoInvalido(
                        MethodArgumentTypeMismatchException excepcion,
                        HttpServletRequest solicitud) {

                // No se repite el valor recibido: se nombra el parametro y, si es
                // una enumeracion, los valores que si admite.
                Class<?> tipoEsperado = excepcion.getRequiredType();
                String detalle = tipoEsperado != null && tipoEsperado.isEnum()
                        ? excepcion.getName() + ": valor no permitido; se admite "
                                + Arrays.stream(tipoEsperado.getEnumConstants())
                                        .map(Object::toString)
                                        .collect(Collectors.joining(", "))
                        : excepcion.getName() + ": valor con formato inválido";

                return respuesta(
                        HttpStatus.BAD_REQUEST,
                        "Solicitud inválida",
                        detalle,
                        "urn:nexus:problema:solicitud-invalida",
                        solicitud);
        }

        @ExceptionHandler(ModificacionProductoInvalidaException.class)
        ResponseEntity<ProblemDetail> manejarModificacionInvalida(
                        ModificacionProductoInvalidaException excepcion,
                        HttpServletRequest solicitud) {

                return respuesta(
                        HttpStatus.BAD_REQUEST,
                        "Solicitud inválida",
                        excepcion.getMessage(),
                        "urn:nexus:problema:solicitud-invalida",
                        solicitud);
        }

        @ExceptionHandler(ProductoNoEncontradoException.class)
        ResponseEntity<ProblemDetail> manejarProductoNoEncontrado(
                        ProductoNoEncontradoException excepcion,
                        HttpServletRequest solicitud) {

                return respuesta(
                        HttpStatus.NOT_FOUND,
                        "Producto no encontrado",
                        excepcion.getMessage(),
                        "urn:nexus:problema:producto-no-encontrado",
                        solicitud);
        }

        // B4 — suspender y reactivar trabajan con el dominio de disponibilidad,
        // que tiene su propia excepcion de producto inexistente: mismo 404 y
        // mismo mensaje que la consulta, para que el cliente no distinga por
        // donde entro.
        @ExceptionHandler(nexus.productos.dominio.ProductoNoEncontradoException.class)
        ResponseEntity<ProblemDetail> manejarProductoNoEncontradoEnDisponibilidad(
                        nexus.productos.dominio.ProductoNoEncontradoException excepcion,
                        HttpServletRequest solicitud) {

                return manejarProductoNoEncontrado(new ProductoNoEncontradoException(), solicitud);
        }

        // B4 — bloqueo optimista (Producto.version es @Version): otro escribio el
        // producto entre la lectura y el guardado. No se aplico nada; releer y
        // volver a intentar es lo correcto.
        @ExceptionHandler(OptimisticLockingFailureException.class)
        ResponseEntity<ProblemDetail> manejarConflictoDeVersion(
                        OptimisticLockingFailureException excepcion,
                        HttpServletRequest solicitud) {

                return respuesta(
                        HttpStatus.CONFLICT,
                        "Conflicto de edicion",
                        "El producto cambio mientras se editaba. Vuelve a cargarlo y aplica el cambio de nuevo.",
                        "urn:nexus:problema:conflicto-de-version",
                        solicitud);
        }

        @ExceptionHandler(ClaveDeIdempotenciaReutilizadaException.class)
        ResponseEntity<ProblemDetail> manejarClaveReutilizada(
                        ClaveDeIdempotenciaReutilizadaException excepcion,
                        HttpServletRequest solicitud) {

                return respuesta(
                        HttpStatus.CONFLICT,
                        "Clave de idempotencia reutilizada",
                        excepcion.getMessage(),
                        "urn:nexus:problema:clave-de-idempotencia-reutilizada",
                        solicitud);
        }

        @ExceptionHandler(MissingRequestHeaderException.class)
        ResponseEntity<ProblemDetail> manejarCabeceraAusente(
                        MissingRequestHeaderException excepcion,
                        HttpServletRequest solicitud) {

                return respuesta(
                        HttpStatus.BAD_REQUEST,
                        "Solicitud inválida",
                        "Falta la cabecera " + excepcion.getHeaderName(),
                        "urn:nexus:problema:solicitud-invalida",
                        solicitud);
        }

        @ExceptionHandler(Exception.class)
        ResponseEntity<ProblemDetail> manejarErrorInesperado(
                        Exception excepcion,
                        HttpServletRequest solicitud) {

                // HU-PRD-014: este 500 salio en DEV en cada inicio de sesion y la
                // bitacora no decia nada. Al cliente le basta el mensaje generico;
                // la causa, con su traza y la ruta, va a stdout para docker logs.
                BITACORA.error(
                        "Error inesperado en {} {}",
                        solicitud.getMethod(),
                        solicitud.getRequestURI(),
                        excepcion);

                return respuesta(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Error interno",
                        "No fue posible procesar la solicitud",
                        "urn:nexus:problema:error-interno",
                        solicitud);
        }

        private ResponseEntity<ProblemDetail> respuesta(
                        HttpStatus estado,
                        String titulo,
                        String detalle,
                        String tipo,
                        HttpServletRequest solicitud) {

                ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
                problema.setTitle(titulo);
                problema.setType(URI.create(tipo));
                problema.setInstance(URI.create(solicitud.getRequestURI()));

                return ResponseEntity
                        .status(estado)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body(problema);
        }
}
