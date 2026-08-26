package nexus.inventario.api;

import nexus.inventario.aplicacion.IdentidadRequeridaException;
import nexus.inventario.aplicacion.InventarioAjenoException;
import nexus.inventario.dominio.ElementoNoEncontradoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ManejadorDeErrores {

    @ExceptionHandler(IdentidadRequeridaException.class)
    public ProblemDetail identidadRequerida(IdentidadRequeridaException error) {
        return problema(HttpStatus.UNAUTHORIZED, "Identidad requerida", error.getMessage());
    }

    @ExceptionHandler(InventarioAjenoException.class)
    public ProblemDetail inventarioAjeno(InventarioAjenoException error) {
        return problema(HttpStatus.FORBIDDEN, "Inventario ajeno", error.getMessage());
    }

    @ExceptionHandler(ElementoNoEncontradoException.class)
    public ProblemDetail elementoNoEncontrado(ElementoNoEncontradoException error) {
        return problema(HttpStatus.NOT_FOUND, "Elemento no encontrado", error.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class
    })
    public ProblemDetail solicitudInvalida(Exception error) {
        return problema(HttpStatus.BAD_REQUEST, "Solicitud invalida", "Revisa los datos del elemento.");
    }

    private ProblemDetail problema(HttpStatus estado, String titulo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        return problema;
    }
}
