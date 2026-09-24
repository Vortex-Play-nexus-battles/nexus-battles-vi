package nexus.inventario.api;

import nexus.inventario.aplicacion.TransferenciaSinBloqueoException;
import nexus.inventario.aplicacion.CatalogoNoDisponibleException;
import nexus.inventario.aplicacion.CriterioBusquedaInvalidoException;
import nexus.inventario.aplicacion.IdentidadRequeridaException;
import nexus.inventario.aplicacion.IdentificadorHistoricoException;
import nexus.inventario.aplicacion.InventarioAjenoException;
import nexus.inventario.aplicacion.ProductoInexistenteException;
import nexus.inventario.aplicacion.ProductoNoEncontradoException;
import nexus.inventario.aplicacion.ProductoSuspendidoException;
import nexus.inventario.aplicacion.TipoNoCoincideException;
import nexus.inventario.dominio.ElementoNoEncontradoException;
import nexus.inventario.dominio.ElementoNoDisponibleException;
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

    @ExceptionHandler(CriterioBusquedaInvalidoException.class)
    public ProblemDetail criterioBusquedaInvalido(CriterioBusquedaInvalidoException error) {
        return problema(HttpStatus.BAD_REQUEST, "Criterio de busqueda invalido", error.getMessage());
    }

    @ExceptionHandler(IdentidadRequeridaException.class)
    public ProblemDetail identidadRequerida(IdentidadRequeridaException error) {
        return problema(HttpStatus.UNAUTHORIZED, "Identidad requerida", error.getMessage());
    }

    @ExceptionHandler(InventarioAjenoException.class)
    public ProblemDetail inventarioAjeno(InventarioAjenoException error) {
        return problema(HttpStatus.FORBIDDEN, "Inventario ajeno", error.getMessage());
    }

    @ExceptionHandler(IdentificadorHistoricoException.class)
    public ProblemDetail identificadorHistorico(IdentificadorHistoricoException error) {
        return problema(HttpStatus.CONFLICT, "Inventario pendiente de migracion", error.getMessage());
    }

    /**
     * 409 y no 403: la peticion viene de quien puede (ms-subastas, por azp), y
     * lo que falla es el estado del elemento. Distinguir "no tienes permiso" de
     * "ese elemento no esta en esa subasta" evita reintentar algo que fallara igual.
     */
    @ExceptionHandler(TransferenciaSinBloqueoException.class)
    public ProblemDetail transferenciaSinBloqueo(TransferenciaSinBloqueoException error) {
        return problema(HttpStatus.CONFLICT, "Transferencia sin bloqueo", error.getMessage());
    }

    @ExceptionHandler(ElementoNoEncontradoException.class)
    public ProblemDetail elementoNoEncontrado(ElementoNoEncontradoException error) {
        return problema(HttpStatus.NOT_FOUND, "Elemento no encontrado", error.getMessage());
    }

    @ExceptionHandler(ElementoNoDisponibleException.class)
    public ProblemDetail elementoNoDisponible(ElementoNoDisponibleException error) {
        return problema(HttpStatus.CONFLICT, "Producto no disponible", error.getMessage());
    }

    @ExceptionHandler(ProductoNoEncontradoException.class)
    public ProblemDetail productoNoEncontrado(ProductoNoEncontradoException error) {
        return problema(HttpStatus.NOT_FOUND, "Producto no encontrado", error.getMessage());
    }

    /**
     * 422 y no 404: la ruta de creacion existe; lo que no existe es el
     * producto que la peticion nombra. El 404 "Producto no encontrado" de
     * arriba sigue siendo el de consultar estadisticas de algo ya guardado.
     */
    @ExceptionHandler(ProductoInexistenteException.class)
    public ProblemDetail productoInexistente(ProductoInexistenteException error) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Producto inexistente", error.getMessage());
    }

    @ExceptionHandler(ProductoSuspendidoException.class)
    public ProblemDetail productoSuspendido(ProductoSuspendidoException error) {
        return problema(HttpStatus.CONFLICT, "Producto suspendido", error.getMessage());
    }

    @ExceptionHandler(TipoNoCoincideException.class)
    public ProblemDetail tipoNoCoincide(TipoNoCoincideException error) {
        return problema(HttpStatus.BAD_REQUEST, "Tipo no coincide", error.getMessage());
    }

    @ExceptionHandler(CatalogoNoDisponibleException.class)
    public ProblemDetail catalogoNoDisponible(CatalogoNoDisponibleException error) {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "Catalogo no disponible", error.getMessage());
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
