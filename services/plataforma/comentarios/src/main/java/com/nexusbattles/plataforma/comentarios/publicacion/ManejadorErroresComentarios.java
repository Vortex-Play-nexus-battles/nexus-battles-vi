package com.nexusbattles.plataforma.comentarios.publicacion;

import java.net.URI;
import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.nexusbattles.plataforma.comentarios.HiloDeComentarios;
import com.nexusbattles.plataforma.comentarios.calificacion.CalificacionNoEncontrada;
import com.nexusbattles.plataforma.comentarios.calificacion.YaCalificado;
import com.nexusbattles.plataforma.comentarios.catalogo.CatalogoNoDisponible;
import com.nexusbattles.plataforma.comentarios.catalogo.ProductoInexistente;
import com.nexusbattles.plataforma.comentarios.imagenes.ArchivoAusente;
import com.nexusbattles.plataforma.comentarios.imagenes.ImagenDemasiadoGrande;
import com.nexusbattles.plataforma.comentarios.imagenes.ImagenNoAdmitida;
import com.nexusbattles.plataforma.comentarios.imagenes.ImagenNoEncontrada;
import com.nexusbattles.plataforma.comentarios.moderacion.ServicioDeModeracion;

/**
 * Los rechazos del servicio como problem details (regla 4 de plataforma), con
 * el {@code type} {@code https://nexusbattles.local/errores/<caso>} que ya usaba
 * este servicio y, donde el contrato lo declara, un {@code motivo} estable para
 * que el cliente decida sin interpretar el texto del detalle.
 *
 * <p><b>Por que {@code HIGHEST_PRECEDENCE}</b> (B3): Spring Boot registra su
 * propio manejador de problem details con orden 0, y ese se quedaba con el
 * {@link MaxUploadSizeExceededException} de una imagen de mas de 2 MB y lo
 * devolvia con {@code type: about:blank}. Una imagen demasiado grande tiene que
 * responder igual la corte Spring antes de llegar al controlador o la corte
 * el examinador despues. Ninguna excepcion de las de aqui la maneja tambien el
 * de Spring Boot, asi que subir la prioridad no le quita ningun caso.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ManejadorErroresComentarios {

    private static final String ERRORES = "https://nexusbattles.local/errores/";

    @ExceptionHandler(HiloDeComentarios.PublicacionRechazada.class)
    public ProblemDetail manejarPublicacionRechazada(HiloDeComentarios.PublicacionRechazada ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problema.setType(URI.create(ERRORES + "autor-silenciado"));
        problema.setTitle("Tu cuenta no puede publicar ahora");
        problema.setProperty("motivo", ex.motivo().name());
        return problema;
    }

    /** HU-COM-004, CA-03: el comentario no esta en el hilo de ese producto. */
    @ExceptionHandler(HiloDeComentarios.ComentarioNoEncontrado.class)
    public ProblemDetail manejarComentarioNoEncontrado(HiloDeComentarios.ComentarioNoEncontrado ex) {
        return problema(HttpStatus.NOT_FOUND, "comentario-no-encontrado", "Comentario no encontrado", ex);
    }

    /** HU-COM-004, CA-02: solo el autor retira su comentario. */
    @ExceptionHandler(HiloDeComentarios.ComentarioAjeno.class)
    public ProblemDetail manejarComentarioAjeno(HiloDeComentarios.ComentarioAjeno ex) {
        return problema(HttpStatus.FORBIDDEN, "comentario-ajeno", "Ese comentario no es tuyo", ex);
    }

    /**
     * Una violacion de integridad que ninguna regla atrapo antes. Desde B3 la
     * calificacion unica ya no llega aqui (se inserta con ON CONFLICT y el
     * choque se traduce a 409 {@code ya-calificado} o a
     * {@code calificacionDescartada}); queda como red por si otra restriccion
     * de la base salta en una carrera: 409 y nunca un 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail manejarCarrera(DataIntegrityViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "otra solicitud simultanea cambio lo mismo; vuelve a intentarlo");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail manejarSolicitudInvalida(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** RF-USR-004 (HU-COM-001, CA-03): sin poder comprobar la sancion no se publica. */
    @ExceptionHandler(SancionesNoDisponibles.class)
    public ProblemDetail manejarSancionesNoDisponibles(SancionesNoDisponibles ex) {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "sanciones-no-disponibles",
                "No se pudo comprobar tu estado para publicar", ex);
    }

    // --------------------------------------------------------------- B3: catalogo

    /** Contrato 1.4.0, respuesta {@code ProductoInexistente}. */
    @ExceptionHandler(ProductoInexistente.class)
    public ProblemDetail manejarProductoInexistente(ProductoInexistente ex) {
        return problema(HttpStatus.NOT_FOUND, "producto-inexistente", "Ese producto no existe", ex);
    }

    /** Sin catalogo no se escribe a ciegas: 503 y el cliente reintenta. */
    @ExceptionHandler(CatalogoNoDisponible.class)
    public ProblemDetail manejarCatalogoNoDisponible(CatalogoNoDisponible ex) {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "catalogo-no-disponible",
                "No se pudo comprobar el producto", ex);
    }

    // ----------------------------------------------------------- B3: calificacion

    /** Contrato 1.4.0: «la segunda responde 409 `ya-calificado`». */
    @ExceptionHandler(YaCalificado.class)
    public ProblemDetail manejarYaCalificado(YaCalificado ex) {
        ProblemDetail problema = problema(HttpStatus.CONFLICT, "ya-calificado", "Ya calificaste este producto", ex);
        problema.setProperty("motivo", "CALIFICACION_DUPLICADA");
        return problema;
    }

    @ExceptionHandler(CalificacionNoEncontrada.class)
    public ProblemDetail manejarCalificacionNoEncontrada(CalificacionNoEncontrada ex) {
        return problema(HttpStatus.NOT_FOUND, "calificacion-no-encontrada", "Todavia no calificaste este producto", ex);
    }

    // --------------------------------------------------------------- B3: imagenes

    @ExceptionHandler(HiloDeComentarios.ImagenesNoValidas.class)
    public ProblemDetail manejarImagenesNoValidas(HiloDeComentarios.ImagenesNoValidas ex) {
        return problema(HttpStatus.BAD_REQUEST, "imagenes-no-validas", "Esas imagenes no se pueden adjuntar", ex);
    }

    @ExceptionHandler(ArchivoAusente.class)
    public ProblemDetail manejarArchivoAusente(ArchivoAusente ex) {
        return problema(HttpStatus.BAD_REQUEST, "imagen-ausente", "Falta la imagen", ex);
    }

    @ExceptionHandler(ImagenDemasiadoGrande.class)
    public ProblemDetail manejarImagenDemasiadoGrande(ImagenDemasiadoGrande ex) {
        return problema(HttpStatus.CONTENT_TOO_LARGE, "imagen-demasiado-grande", "La imagen es demasiado grande", ex);
    }

    /** El cuerpo que Spring corta antes del controlador, con el mismo tipo que el que corta el examinador. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail manejarCuerpoDemasiadoGrande(MaxUploadSizeExceededException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE,
                "la imagen pesa mas de 2 MB");
        problema.setType(URI.create(ERRORES + "imagen-demasiado-grande"));
        problema.setTitle("La imagen es demasiado grande");
        return problema;
    }

    @ExceptionHandler(ImagenNoAdmitida.class)
    public ProblemDetail manejarImagenNoAdmitida(ImagenNoAdmitida ex) {
        ProblemDetail problema = problema(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "imagen-no-admitida",
                "No es una imagen JPEG, PNG o WebP valida", ex);
        problema.setProperty("motivo", "FORMATO_DE_IMAGEN_NO_ADMITIDO");
        return problema;
    }

    @ExceptionHandler(ImagenNoEncontrada.class)
    public ProblemDetail manejarImagenNoEncontrada(ImagenNoEncontrada ex) {
        return problema(HttpStatus.NOT_FOUND, "imagen-no-encontrada", "Imagen no encontrada", ex);
    }

    // ------------------------------------------------------------ R10.1

    /**
     * Los rechazos del flujo de moderacion, con su {@code motivo} estable.
     *
     * <p>El cliente decide el mensaje mirando el motivo, no interpretando el
     * texto del detalle: el texto puede cambiar y el motivo no. Es la misma
     * regla que ya seguian los rechazos de publicacion.
     */
    @ExceptionHandler(ServicioDeModeracion.ComentarioNoEncontrado.class)
    public ProblemDetail manejarComentarioDeModeracionNoEncontrado(
            ServicioDeModeracion.ComentarioNoEncontrado ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ServicioDeModeracion.ReporteDuplicado.class)
    public ProblemDetail manejarReporteDuplicado(ServicioDeModeracion.ReporteDuplicado ex) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problema.setProperty("motivo", "REPORTE_DUPLICADO");
        return problema;
    }

    /** Categoria ausente o descripcion de mas de 500 caracteres: 400, no una violacion de la base. */
    @ExceptionHandler(ServicioDeModeracion.ReporteInvalido.class)
    public ProblemDetail manejarReporteInvalido(ServicioDeModeracion.ReporteInvalido ex) {
        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "reporte-invalido",
                "El reporte no es valido", ex);
        problema.setProperty("motivo", "REPORTE_INVALIDO");
        return problema;
    }

    @ExceptionHandler(ServicioDeModeracion.LimiteDeReportesAgotado.class)
    public ProblemDetail manejarLimiteDeReportes(ServicioDeModeracion.LimiteDeReportesAgotado ex) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        problema.setProperty("motivo", "LIMITE_DE_REPORTES");
        return problema;
    }

    /**
     * El caso que CA-03 nombra: "comentario ya resuelto por otro moderador".
     * 409 y nada cambia — no se pisa la decision del que llego primero.
     */
    @ExceptionHandler(ServicioDeModeracion.TransicionInvalida.class)
    public ProblemDetail manejarTransicionInvalida(ServicioDeModeracion.TransicionInvalida ex) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problema.setProperty("motivo", "TRANSICION_INVALIDA");
        return problema;
    }

    /**
     * HU-COM-008, CA-02: el lote no se aplico y no cambio nada. Mismo 409 que
     * {@code TRANSICION_INVALIDA}, con {@code fallidos} para que el moderador
     * vea cuales comentarios lo impidieron y por que.
     */
    @ExceptionHandler(ServicioDeModeracion.LoteRechazado.class)
    public ProblemDetail manejarLoteRechazado(ServicioDeModeracion.LoteRechazado ex) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problema.setProperty("motivo", "LOTE_RECHAZADO");
        problema.setProperty("fallidos", ex.fallidos().stream()
                .map(f -> Map.of("comentarioId", f.comentarioId(),
                        "motivo", f.motivo().name(),
                        "detalle", f.detalle()))
                .toList());
        return problema;
    }

    /** Motivo ausente o fuera de 3..500, o EDITAR sin {@code textoNuevo} valido: 400. */
    @ExceptionHandler(ServicioDeModeracion.DecisionIncompleta.class)
    public ProblemDetail manejarDecisionIncompleta(ServicioDeModeracion.DecisionIncompleta ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    private static ProblemDetail problema(HttpStatus estado, String caso, String titulo, RuntimeException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, ex.getMessage());
        problema.setType(URI.create(ERRORES + caso));
        problema.setTitle(titulo);
        return problema;
    }
}
