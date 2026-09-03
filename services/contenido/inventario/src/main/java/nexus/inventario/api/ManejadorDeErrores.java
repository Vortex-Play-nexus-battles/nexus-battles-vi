package nexus.inventario.api;

import nexus.inventario.aplicacion.IdentidadRequeridaException;
import nexus.inventario.aplicacion.InventarioAjenoException;
import nexus.inventario.dominio.ElementoNoEncontradoException;
import nexus.inventario.dominio.ElementoNoEquipableException;
import nexus.inventario.dominio.ElementoYaEquipadoException;
import nexus.inventario.dominio.FalloPersistenciaInventarioException;
import nexus.inventario.dominio.LimiteEquipamientoException;
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

    @ExceptionHandler(LimiteEquipamientoException.class)
    public ProblemDetail limiteEquipamiento(LimiteEquipamientoException error) {
        return problema(HttpStatus.CONFLICT, "Limite de equipamiento", error.getMessage());
    }

    @ExceptionHandler(ElementoYaEquipadoException.class)
    public ProblemDetail elementoYaEquipado(ElementoYaEquipadoException error) {
        return problema(HttpStatus.CONFLICT, "Elemento ya equipado", error.getMessage());
    }

    @ExceptionHandler(ElementoNoEquipableException.class)
    public ProblemDetail elementoNoEquipable(ElementoNoEquipableException error) {
        return problema(HttpStatus.BAD_REQUEST, "Elemento no equipable", error.getMessage());
    }

    @ExceptionHandler(FalloPersistenciaInventarioException.class)
    public ProblemDetail persistenciaNoDisponible() {
        return problema(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Inventario no disponible",
                "No fue posible completar la escritura. Intenta nuevamente.");
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
