package com.nexusbattles.plataforma.comentarios.publicacion;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexusbattles.comun.seguridad.IdentidadDelToken;
import com.nexusbattles.plataforma.comentarios.Comentario;
import com.nexusbattles.plataforma.comentarios.moderacion.CategoriaDeReporte;
import com.nexusbattles.plataforma.comentarios.moderacion.ServicioDeModeracion;

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
 * <p><b>El autor sale del token, no del cuerpo.</b> Hasta la version 1.0.0 del
 * contrato {@code autorId} y {@code apodoAutor} viajaban en la solicitud
 * "mientras se acordaba con identidad que claim los aporta"; ADR-002 lo fijo
 * ({@code uid} estable y apodo), asi que desde la 1.1.0 esos campos se
 * aceptan por compatibilidad pero se ignoran: nadie puede publicar a nombre
 * de otro escribiendo su identificador. La cadena de seguridad garantiza que
 * aqui llega un usuario autenticado; {@link IdentidadDelToken} lee sus dos
 * caras en el mismo sitio que el resto de la plataforma.
 */
@RestController
@RequestMapping("/api/v1/products/{productId}/comments")
public class ComentariosController {

    private final ServicioDePublicacionDeComentarios servicio;
    private final ServicioDeModeracion moderacion;

    public ComentariosController(ServicioDePublicacionDeComentarios servicio,
            ServicioDeModeracion moderacion) {
        this.servicio = servicio;
        this.moderacion = moderacion;
    }

    /**
     * El hilo publico del producto (#438; proveedor de HU-INV-014).
     *
     * <p>Siempre 200: un producto sin comentarios es un hilo vacio, no un
     * recurso inexistente. El promedio va nulo cuando nadie califico, para que
     * la ficha diga «sin valoraciones» en vez de pintar un cero.
     */
    @GetMapping
    public HiloDeComentariosResponse consultar(@PathVariable String productId) {
        return HiloDeComentariosResponse.desde(servicio.consultarHilo(productId));
    }

    @PostMapping
    public ResponseEntity<ComentarioResponse> publicar(
            @PathVariable String productId,
            @AuthenticationPrincipal Jwt autor,
            @RequestBody PublicacionComentarioRequest request) {

        ServicioDePublicacionDeComentarios.Publicado publicado = servicio.publicar(
                productId,
                IdentidadDelToken.idDe(autor).toString(),
                IdentidadDelToken.apodoDe(autor),
                request.texto(),
                request.imagenes(),
                request.estrellas());

        Comentario comentario = publicado.comentario();
        HttpStatus estado = comentario.estaPublicado() ? HttpStatus.CREATED : HttpStatus.ACCEPTED;
        return ResponseEntity.status(estado)
                .body(ComentarioResponse.desde(comentario, publicado.calificacionDescartada()));
    }

    /**
     * Retira un comentario propio — HU-COM-004 (contrato 1.2.0).
     *
     * <p>Quien retira es el {@code uid} del token; el cuerpo no manda nada.
     * 204 tambien si ya estaba retirado (idempotente); 403 si es de otro;
     * 404 si no esta en el hilo del producto.
     */
    /**
     * Reportar un comentario — RF-COM-006 (contrato 1.3.0).
     *
     * <p>Vive aqui y no en el controlador de moderacion a proposito: reportar
     * lo hace un JUGADOR desde el hilo del producto, y su ruta es la del
     * comentario. La cola y las acciones son del moderador y viven en
     * {@code /api/v1/moderacion}. Mezclarlos daria una ruta donde la mitad de
     * los metodos son para cualquiera y la otra mitad para moderadores, que es
     * como se acaba abriendo una por descuido.
     *
     * <p>El reportante es el {@code uid} del token. El cuerpo no manda a nadie.
     */
    @PostMapping("/{commentId}/reportes")
    public ResponseEntity<ReporteCreadoResponse> reportar(
            @PathVariable String productId,
            @PathVariable String commentId,
            @AuthenticationPrincipal Jwt reportante,
            @RequestBody ReporteRequest peticion) {

        ServicioDeModeracion.Reportado reportado = moderacion.reportar(
                productId,
                commentId,
                IdentidadDelToken.idDe(reportante).toString(),
                peticion.categoria(),
                peticion.descripcion());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ReporteCreadoResponse.desde(reportado));
    }

    /** Cuerpo del reporte segun el contrato 1.3.0. */
    public record ReporteRequest(CategoriaDeReporte categoria, String descripcion) {
    }

    /** Lo que el jugador recibe al reportar. */
    public record ReporteCreadoResponse(String id, String comentarioId,
            CategoriaDeReporte categoria, String descripcion, String fecha,
            String estadoDelComentario, long reportesTotales) {

        static ReporteCreadoResponse desde(ServicioDeModeracion.Reportado r) {
            return new ReporteCreadoResponse(
                    r.reporte().id(),
                    r.reporte().comentarioId(),
                    r.reporte().categoria(),
                    r.reporte().descripcion(),
                    r.reporte().fecha().toString(),
                    r.comentario().estado().name(),
                    r.totales());
        }
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> eliminar(
            @PathVariable String productId,
            @PathVariable String commentId,
            @AuthenticationPrincipal Jwt autor) {
        servicio.eliminar(productId, commentId, IdentidadDelToken.idDe(autor).toString());
        return ResponseEntity.noContent().build();
    }

    /**
     * Cuerpo de la solicitud segun el contrato (1.1.0).
     *
     * <p>{@code autorId} y {@code apodoAutor} siguen en el esquema, marcados
     * como obsoletos, para que un cliente de la 1.0.0 no reciba 400 por
     * mandarlos; el servicio no los lee.
     */
    public record PublicacionComentarioRequest(
            @Deprecated String autorId,
            @Deprecated String apodoAutor,
            String texto,
            List<String> imagenes,
            Integer estrellas) {
    }

    /**
     * Respuesta del contrato, con las estrellas ausentes si ya habia calificado.
     *
     * <p>{@code calificacionDescartada} (1.2.0, RF-COM-002 / D-07) dice que
     * las estrellas que venian se descartaron porque el autor ya habia
     * calificado el producto: el comentario entro igual. Va a {@code false}
     * en el hilo, donde no hay solicitud que descartar.
     */
    public record ComentarioResponse(
            String id,
            String productoId,
            String autorId,
            String apodoAutor,
            String texto,
            List<String> imagenes,
            Integer estrellas,
            Instant fechaPublicacion,
            String estado,
            boolean calificacionDescartada) {

        static ComentarioResponse desde(Comentario comentario) {
            return desde(comentario, false);
        }

        /**
         * R10.1 — publico para que la cola de moderacion pinte el comentario
         * con el MISMO cuerpo que el hilo. Dos representaciones del mismo
         * comentario acabarian divergiendo, y el moderador veria algo distinto
         * de lo que ve el jugador justo cuando mas importa que coincidan.
         */
        public static ComentarioResponse desde(Comentario comentario, boolean calificacionDescartada) {
            return new ComentarioResponse(
                    comentario.id(),
                    comentario.productoId(),
                    comentario.autorId(),
                    comentario.apodoAutor(),
                    comentario.texto(),
                    comentario.imagenes(),
                    comentario.calificacion().orElse(null),
                    comentario.fechaPublicacion(),
                    comentario.estado().name(),
                    calificacionDescartada);
        }
    }

    /** Esquema {@code HiloDeComentariosResponse} del contrato. */
    public record HiloDeComentariosResponse(
            String productoId,
            List<ComentarioResponse> comentarios,
            int total,
            Double calificacionPromedio,
            int totalCalificaciones) {

        static HiloDeComentariosResponse desde(
                ServicioDePublicacionDeComentarios.HiloConsultado hilo) {
            List<ComentarioResponse> comentarios = hilo.comentarios().stream()
                    .map(ComentarioResponse::desde)
                    .toList();
            // Dos decimales, como declara el contrato: 4.333... seria ruido en
            // una ficha de cinco estrellas.
            Double promedio = hilo.calificacionPromedio().isPresent()
                    ? Math.round(hilo.calificacionPromedio().getAsDouble() * 100.0) / 100.0
                    : null;
            return new HiloDeComentariosResponse(
                    hilo.productoId(), comentarios, comentarios.size(),
                    promedio, hilo.totalCalificaciones());
        }
    }
}
