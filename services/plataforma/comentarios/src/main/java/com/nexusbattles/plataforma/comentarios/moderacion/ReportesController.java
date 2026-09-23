package com.nexusbattles.plataforma.comentarios.moderacion;

import com.nexusbattles.comun.seguridad.IdentidadDelToken;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La puerta de entrada al flujo de moderacion: un jugador marca un comentario
 * — RF-COM-006, contrato 1.3.0.
 *
 * <h2>Por que la ruta esta bajo el comentario y la clase bajo moderacion</h2>
 *
 * <p>La <b>ruta</b> es la del comentario ({@code
 * /api/v1/products/{productId}/comments/{commentId}/reportes}) porque asi lo
 * declara el contrato y porque es donde el jugador esta cuando reporta: en el
 * hilo del producto, mirando el comentario. Lo que no cabe en esa ruta es la
 * cola ni la decision, que son del moderador y viven en {@code
 * /api/v1/moderacion}: mezclarlas daria un prefijo donde la mitad de los
 * metodos son para cualquiera y la otra mitad para moderadores, que es como se
 * acaba abriendo uno por descuido.
 *
 * <p>La <b>clase</b>, en cambio, vive aqui y no en {@code publicacion} para
 * que la dependencia apunte en un solo sentido. Publicar un comentario no
 * necesita saber que existe la moderacion; moderar si necesita saber que
 * existen los comentarios. Cuando {@code ComentariosController} tenia tambien
 * este metodo, el controlador de publicacion arrastraba
 * {@link ServicioDeModeracion} a cualquier prueba de rodaja que lo cargara,
 * que es justo la senal de que la pieza estaba en el sitio equivocado.
 *
 * <p>El reportante sale SIEMPRE del token. El cuerpo no nombra a nadie: si lo
 * hiciera, cualquiera podria dejar reportes a nombre de otra persona y el
 * limite por usuario no significaria nada.
 */
@RestController
@RequestMapping("/api/v1/products/{productId}/comments/{commentId}/reportes")
public class ReportesController {

    private final ServicioDeModeracion moderacion;

    public ReportesController(ServicioDeModeracion moderacion) {
        this.moderacion = moderacion;
    }

    @PostMapping
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

    /**
     * Lo que el jugador recibe al reportar.
     *
     * <p>Devuelve {@code estadoDelComentario} y {@code reportesTotales} a
     * proposito: sin ellos el cliente tendria que volver a pedir el hilo para
     * saber si su reporte retuvo el comentario o solo se sumo a los que ya
     * habia.
     */
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
}
