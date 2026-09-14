package com.nexusbattles.plataforma.comentarios.publicacion;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexusbattles.plataforma.comentarios.Comentario;

/**
 * Endpoint de publicacion de comentarios de HU-COM-001, segun el contrato
 * publicado en contracts/openapi/comentarios.yaml.
 *
 * <p>La retencion y el rechazo llevan respuestas distintas a proposito, como
 * pedia la propuesta tecnica del issue: publicado responde 201, retenido por el
 * filtro responde 202, y los rechazos salen como problem details por el
 * manejador de errores. No es lo mismo decirle al jugador que su comentario no
 * se publico que decirle que esta en revision.
 *
 * <p>El autor y su apodo llegan en el cuerpo mientras se acuerda con el modulo
 * de identidad que claim del token los aporta. Cuando eso se confirme, el
 * resource server entra igual que en moderacion-sanciones y el contrato abre
 * version nueva.
 */
@RestController
@RequestMapping("/api/v1/products/{productId}/comments")
public class ComentariosController {

    private final ServicioDePublicacionDeComentarios servicio;

    public ComentariosController(ServicioDePublicacionDeComentarios servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    public ResponseEntity<ComentarioResponse> publicar(
            @PathVariable String productId,
            @RequestBody PublicacionComentarioRequest request) {

        Comentario comentario = servicio.publicar(
                productId,
                request.autorId(),
                request.apodoAutor(),
                request.texto(),
                request.imagenes(),
                request.estrellas());

        HttpStatus estado = comentario.estaPublicado() ? HttpStatus.CREATED : HttpStatus.ACCEPTED;
        return ResponseEntity.status(estado).body(ComentarioResponse.desde(comentario));
    }

    /** Cuerpo de la solicitud segun el contrato. */
    public record PublicacionComentarioRequest(
            String autorId,
            String apodoAutor,
            String texto,
            List<String> imagenes,
            Integer estrellas) {
    }

    /** Respuesta del contrato, con las estrellas ausentes si ya habia calificado. */
    public record ComentarioResponse(
            String id,
            String productoId,
            String autorId,
            String apodoAutor,
            String texto,
            List<String> imagenes,
            Integer estrellas,
            Instant fechaPublicacion,
            String estado) {

        static ComentarioResponse desde(Comentario comentario) {
            return new ComentarioResponse(
                    comentario.id(),
                    comentario.productoId(),
                    comentario.autorId(),
                    comentario.apodoAutor(),
                    comentario.texto(),
                    comentario.imagenes(),
                    comentario.calificacion().orElse(null),
                    comentario.fechaPublicacion(),
                    comentario.estado().name());
        }
    }
}
