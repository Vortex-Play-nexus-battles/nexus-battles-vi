package nexus.api;

import java.net.URI;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import nexus.dominio.ProductoNoEncontradoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class ManejadorDeErrores {

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

        @ExceptionHandler(Exception.class)
        ResponseEntity<ProblemDetail> manejarErrorInesperado(
                        Exception excepcion,
                        HttpServletRequest solicitud) {

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
