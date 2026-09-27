package nexus.inventario.api;

import nexus.inventario.aplicacion.TransferenciaSinBloqueoException;
import nexus.inventario.aplicacion.CatalogoNoDisponibleException;
import nexus.inventario.aplicacion.ClaveDeEntregaReutilizadaException;
import nexus.inventario.aplicacion.ParteNoCoincideException;
import nexus.inventario.aplicacion.ProductoIncompletoException;
import nexus.inventario.aplicacion.CriterioBusquedaInvalidoException;
import nexus.inventario.aplicacion.IdentidadRequeridaException;
import nexus.inventario.aplicacion.IdentificadorHistoricoException;
import nexus.inventario.aplicacion.InventarioAjenoException;
import nexus.inventario.aplicacion.ProductoInexistenteException;
import nexus.inventario.aplicacion.ProductoNoEncontradoException;
import nexus.inventario.aplicacion.ProductoSuspendidoException;
import nexus.inventario.aplicacion.ProgresionNoDisponibleException;
import nexus.inventario.aplicacion.TipoNoCoincideException;
import nexus.inventario.dominio.HeroeEnMisionException;
import nexus.inventario.dominio.NoEsUnHeroeException;
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
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
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

    /** 1.6.0 (B9): el heroe esta en una mision (seccion 7.8.10). */
    @ExceptionHandler(HeroeEnMisionException.class)
    public ProblemDetail heroeEnMision(HeroeEnMisionException error) {
        return problema(HttpStatus.CONFLICT, "Heroe en mision", error.getMessage());
    }

    /** 1.6.0 (B9): solo un heroe sale de mision. */
    @ExceptionHandler(NoEsUnHeroeException.class)
    public ProblemDetail noEsUnHeroe(NoEsUnHeroeException error) {
        return problema(HttpStatus.BAD_REQUEST, "No es un heroe", error.getMessage());
    }

    /**
     * 1.6.0 (B9): hay experiencia que sumar y heroes no respondio; no se aplica
     * nada y el heroe sigue bloqueado hasta el reintento.
     */
    @ExceptionHandler(ProgresionNoDisponibleException.class)
    public ProblemDetail progresionNoDisponible(ProgresionNoDisponibleException error) {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "Progresion no disponible",
                "No se pudo calcular el nivel del heroe. Intenta nuevamente.");
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

    /** B4: la parte de una armadura la decide el catalogo; como el tipo, 400. */
    @ExceptionHandler(ParteNoCoincideException.class)
    public ProblemDetail parteNoCoincide(ParteNoCoincideException error) {
        return problema(HttpStatus.BAD_REQUEST, "Parte no coincide", error.getMessage());
    }

    /**
     * B4: la misma {@code Idempotency-Key} con otro cuerpo. 409: reintentar con
     * esa clave va a fallar igual; la entrega que nombra ya es otra.
     */
    @ExceptionHandler(ClaveDeEntregaReutilizadaException.class)
    public ProblemDetail claveReutilizada(ClaveDeEntregaReutilizadaException error) {
        return problema(HttpStatus.CONFLICT, "Clave de idempotencia reutilizada", error.getMessage());
    }

    /** B4: el catalogo describe una armadura sin parte; no hay ranura donde equiparla. */
    @ExceptionHandler(ProductoIncompletoException.class)
    public ProblemDetail productoIncompleto(ProductoIncompletoException error) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Producto incompleto", error.getMessage());
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

    /**
     * B4: una entrega sin {@code Idempotency-Key}, o con una vacia o de mas de
     * cien caracteres. Sin clave no hay forma de reintentar sin duplicar, asi
     * que no se procesa.
     */
    @ExceptionHandler({MissingRequestHeaderException.class, HandlerMethodValidationException.class})
    public ProblemDetail solicitudSinClaveValida(Exception error) {
        return problema(HttpStatus.BAD_REQUEST, "Solicitud invalida",
                "Revisa los datos de la solicitud y la cabecera Idempotency-Key (1 a 100 caracteres).");
    }

    private ProblemDetail problema(HttpStatus estado, String titulo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        return problema;
    }
}
