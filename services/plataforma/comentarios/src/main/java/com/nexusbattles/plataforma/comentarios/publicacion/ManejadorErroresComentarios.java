package com.nexusbattles.plataforma.comentarios.publicacion;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.nexusbattles.plataforma.comentarios.HiloDeComentarios;
import com.nexusbattles.plataforma.comentarios.HiloDeComentarios.MotivoDeRechazo;

/**
 * Traduce los rechazos del dominio al formato de error estandar de la
 * plataforma (problem details), igual en los veinte modulos.
 *
 * <p>El issue pedia diferenciar el bloqueo por sancion del rechazo por formato
 * de imagen: aqui el silencio sale como 403 y la imagen no admitida como 422,
 * y en ambos casos el campo motivo le dice al cliente cual de los dos fue.
 */
@RestControllerAdvice
public class ManejadorErroresComentarios {

    @ExceptionHandler(HiloDeComentarios.PublicacionRechazada.class)
    public ProblemDetail manejarPublicacionRechazada(
            HiloDeComentarios.PublicacionRechazada ex) {
        HttpStatus estado = ex.motivo() == MotivoDeRechazo.AUTOR_SILENCIADO
                ? HttpStatus.FORBIDDEN
                : HttpStatus.UNPROCESSABLE_ENTITY;
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, ex.getMessage());
        problema.setProperty("motivo", ex.motivo().name());
        return problema;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail manejarSolicitudInvalida(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
