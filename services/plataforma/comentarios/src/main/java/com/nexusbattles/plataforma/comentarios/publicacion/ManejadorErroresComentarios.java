package com.nexusbattles.plataforma.comentarios.publicacion;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import com.nexusbattles.plataforma.comentarios.moderacion.ServicioDeModeracion;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.nexusbattles.plataforma.comentarios.HiloDeComentarios;
import com.nexusbattles.plataforma.comentarios.HiloDeComentarios.MotivoDeRechazo;

@RestControllerAdvice
public class ManejadorErroresComentarios {

    @ExceptionHandler(HiloDeComentarios.PublicacionRechazada.class)
    public ProblemDetail manejarPublicacionRechazada(
            HiloDeComentarios.PublicacionRechazada ex) {

        HttpStatus estado = switch (ex.motivo()) {
            case AUTOR_SILENCIADO -> HttpStatus.FORBIDDEN;
            case CALIFICACION_DUPLICADA -> HttpStatus.CONFLICT;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };

        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, ex.getMessage());
        problema.setProperty("motivo", ex.motivo().name());
        return problema;
    }

    /** HU-COM-004, CA-03: el comentario no esta en el hilo de ese producto. */
    @ExceptionHandler(HiloDeComentarios.ComentarioNoEncontrado.class)
    public ProblemDetail manejarComentarioNoEncontrado(HiloDeComentarios.ComentarioNoEncontrado ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problema.setType(java.net.URI.create("https://nexusbattles.local/errores/comentario-no-encontrado"));
        problema.setTitle("Comentario no encontrado");
        return problema;
    }

    /** HU-COM-004, CA-02: solo el autor retira su comentario. */
    @ExceptionHandler(HiloDeComentarios.ComentarioAjeno.class)
    public ProblemDetail manejarComentarioAjeno(HiloDeComentarios.ComentarioAjeno ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problema.setType(java.net.URI.create("https://nexusbattles.local/errores/comentario-ajeno"));
        problema.setTitle("Ese comentario no es tuyo");
        return problema;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail manejarCarreraDeCalificacion(DataIntegrityViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "otra solicitud simultanea ya guardo la calificacion de este autor"
                        + " para este producto; reintenta sin calificacion");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail manejarSolicitudInvalida(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** RF-USR-004 (HU-COM-001, CA-03): sin poder comprobar la sancion no se publica. */
    @ExceptionHandler(SancionesNoDisponibles.class)
    public ProblemDetail manejarSancionesNoDisponibles(SancionesNoDisponibles ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problema.setType(java.net.URI.create("https://nexusbattles.local/errores/sanciones-no-disponibles"));
        problema.setTitle("No se pudo comprobar tu estado para publicar");
        return problema;
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

    @ExceptionHandler(ServicioDeModeracion.MotivoRequerido.class)
    public ProblemDetail manejarMotivoRequerido(ServicioDeModeracion.MotivoRequerido ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
