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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nexusbattles.comun.seguridad.IdentidadDelToken;
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

    public ComentariosController(ServicioDePublicacionDeComentarios servicio) {
        this.servicio = servicio;
    }

    /**
     * Una pagina del hilo publico del producto (#438; proveedor de HU-INV-014;
     * paginado desde B3).
     *
     * <p>Siempre 200: un producto sin comentarios es un hilo vacio, no un
     * recurso inexistente. El promedio va nulo cuando nadie califico, para que
     * la ficha diga «sin valoraciones» en vez de pintar un cero.
     *
     * <p><b>G4 (contrato 1.9.0): el hilo no publica el {@code uid} de nadie.</b>
     * Lo lee cualquiera, tambien sin cuenta, y el {@code autorId} de cada
     * comentario permitia seguir a un jugador por todos los productos en los
     * que opino. De su autor solo sale el apodo, que RN-CMT-001 manda
     * ensenar. Lo que el cliente necesitaba del {@code uid} —saber cuales son
     * los suyos para ofrecer «Eliminar» en vez de «Reportar»— lo dice el
     * servidor en {@code propio}, comparando con el token de quien mira si lo
     * trae (sin token, o con uno que no es de usuario, nada es propio).
     */
    @GetMapping
    public HiloDeComentariosResponse consultar(
            @PathVariable String productId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "" + ServicioDePublicacionDeComentarios.TAMANO_POR_OMISION) int tamano,
            @AuthenticationPrincipal Jwt quienMira) {
        return HiloDeComentariosResponse.desde(
                servicio.consultarHilo(productId, pagina, tamano), uidDeQuienMira(quienMira));
    }

    /**
     * El {@code uid} de quien lee el hilo, o null si no hay token de usuario.
     * Un token de servicio no identifica a un jugador: no es error, solo no
     * tiene comentarios propios.
     */
    static String uidDeQuienMira(Jwt quienMira) {
        if (quienMira == null) {
            return null;
        }
        try {
            return IdentidadDelToken.idDe(quienMira).toString();
        } catch (IllegalArgumentException | NullPointerException sinUsuario) {
            return null;
        }
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
                .body(ComentarioResponse.publicado(
                        comentario, publicado.estrellas(), publicado.calificacionDescartada()));
    }

    /**
     * Retira un comentario propio — HU-COM-004 (contrato 1.2.0).
     *
     * <p>Quien retira es el {@code uid} del token; el cuerpo no manda nada.
     * 204 tambien si ya estaba retirado (idempotente); 403 si es de otro;
     * 404 si no esta en el hilo del producto.
     *
     * <p>Reportar un comentario ajeno (RF-COM-006) no esta aqui: su ruta si
     * cuelga de este recurso, pero la clase vive en {@code moderacion} para
     * que publicar no dependa de moderar. Ver {@code ReportesController}.
     */
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
     * mandarlos; el servicio no los lee. {@code imagenes} son, desde la 1.4.0,
     * los {@code id} de imagenes subidas antes.
     */
    public record PublicacionComentarioRequest(
            @Deprecated String autorId,
            @Deprecated String apodoAutor,
            String texto,
            List<String> imagenes,
            Integer estrellas) {
    }

    /**
     * {@code ComentarioResponse} del contrato.
     *
     * <p>{@code estrellas} son las de la calificacion de su autor sobre el
     * producto (B3): el comentario ya no las guarda. {@code
     * calificacionDescartada} (1.2.0, RF-COM-002 / D-07) dice que las
     * estrellas que traia la publicacion se descartaron porque el autor ya
     * habia calificado: el comentario entro igual. Va a {@code false} fuera de
     * la respuesta de publicar.
     *
     * <p>{@code marcado} es de moderacion (7.3.3, «seguimiento especial») y
     * solo viaja en sus respuestas: en el hilo publico va nulo y no se
     * serializa. {@code editado} si es publico: quien lee tiene que saber que
     * un moderador cambio el texto.
     *
     * <p>G4 (1.9.0): {@code autorId} ya no sale en el hilo publico —va nulo y
     * no se serializa—; sigue en la respuesta de publicar (es el {@code uid}
     * de quien publica, que lo pide) y en las de moderacion, que lo necesitan
     * para el historial del autor. {@code propio} sale en el hilo y en la
     * respuesta de publicar; en moderacion no.
     */
    public record ComentarioResponse(
            String id,
            String productoId,
            @JsonInclude(JsonInclude.Include.NON_NULL) String autorId,
            String apodoAutor,
            String texto,
            List<String> imagenes,
            Integer estrellas,
            Instant fechaPublicacion,
            String estado,
            boolean calificacionDescartada,
            boolean editado,
            @JsonInclude(JsonInclude.Include.NON_NULL) Boolean marcado,
            @JsonInclude(JsonInclude.Include.NON_NULL) Boolean propio) {

        /**
         * Como se ve en el hilo publico: sin el {@code uid} del autor, y con
         * {@code propio} si quien mira es su autor.
         *
         * @param uidDeQuienMira el {@code uid} del token de quien lee, o null
         */
        public static ComentarioResponse publico(Comentario comentario, Integer estrellas, String uidDeQuienMira) {
            boolean propio = uidDeQuienMira != null && uidDeQuienMira.equals(comentario.autorId());
            return construir(comentario, false, estrellas, false, null, propio);
        }

        /** La respuesta de publicar: es de quien la pide, asi que es suya. */
        public static ComentarioResponse publicado(
                Comentario comentario, Integer estrellas, boolean calificacionDescartada) {
            return construir(comentario, true, estrellas, calificacionDescartada, null, true);
        }

        /**
         * R10.1 — publico para que la cola de moderacion pinte el comentario
         * con el MISMO cuerpo que el hilo. Dos representaciones del mismo
         * comentario acabarian divergiendo, y el moderador veria algo distinto
         * de lo que ve el jugador justo cuando mas importa que coincidan. Lo
         * unico que anade es la marca de seguimiento, que es suya. Las
         * estrellas no: son la calificacion del producto, no el contenido que
         * se modera.
         */
        public static ComentarioResponse paraModeracion(Comentario comentario) {
            return construir(comentario, true, null, false, comentario.marcado(), null);
        }

        private static ComentarioResponse construir(
                Comentario comentario, boolean conAutorId, Integer estrellas, boolean calificacionDescartada,
                Boolean marcado, Boolean propio) {
            return new ComentarioResponse(
                    comentario.id(),
                    comentario.productoId(),
                    conAutorId ? comentario.autorId() : null,
                    comentario.apodoAutor(),
                    comentario.texto(),
                    comentario.imagenes(),
                    estrellas,
                    comentario.fechaPublicacion(),
                    comentario.estado().name(),
                    calificacionDescartada,
                    comentario.editado(),
                    marcado,
                    propio);
        }
    }

    /**
     * Esquema {@code HiloDeComentariosResponse} del contrato (paginado desde
     * 1.5.0). {@code total} son los publicados de todas las paginas;
     * {@code calificacionPromedio} y {@code totalCalificaciones}, los de la
     * tabla de calificaciones, los mismos que {@code GET /rating}.
     */
    public record HiloDeComentariosResponse(
            String productoId,
            List<ComentarioResponse> comentarios,
            int pagina,
            int tamano,
            long total,
            int totalPaginas,
            Double calificacionPromedio,
            long totalCalificaciones) {

        static HiloDeComentariosResponse desde(
                ServicioDePublicacionDeComentarios.HiloConsultado hilo, String uidDeQuienMira) {
            List<ComentarioResponse> comentarios = hilo.comentarios().stream()
                    .map(comentario -> ComentarioResponse.publico(
                            comentario, hilo.estrellasPorAutor().get(comentario.autorId()), uidDeQuienMira))
                    .toList();
            return new HiloDeComentariosResponse(
                    hilo.productoId(), comentarios, hilo.pagina(), hilo.tamano(),
                    hilo.total(), hilo.totalPaginas(),
                    hilo.resumen().promedio(), hilo.resumen().total());
        }
    }
}
