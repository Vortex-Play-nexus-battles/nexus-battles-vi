package com.nexusbattles.plataforma.comentarios.moderacion;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nexusbattles.comun.seguridad.IdentidadDelToken;
import com.nexusbattles.plataforma.comentarios.publicacion.ComentariosController.ComentarioResponse;
import com.nexusbattles.plataforma.comentarios.publicacion.ResumenDeComentario;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * La cola y las acciones del moderador — RF-COM-005 y RF-COM-008, contrato 1.3.0;
 * EDITAR, MARCAR, DESMARCAR y el filtro {@code marcado} desde B3 (7.3.3).
 *
 * <p>El moderador sale SIEMPRE del token. Ni la cola ni la decision leen un
 * identificador del cuerpo: un asiento de moderacion que dijera quien actuo a
 * partir de un campo enviado por el cliente no serviria para nada, porque
 * cualquiera podria firmar una decision con el nombre de otro. La IP de origen
 * del asiento sale de la peticion ({@link OrigenDeLaPeticion}).
 *
 * <p>Quien puede entrar lo decide {@code SecurityConfig} por rol. Aqui no hay
 * ni un {@code if (rol == ...)}: la autorizacion se declara en un sitio.
 *
 * <h2>Por que la ruta NO es {@code /api/v1/moderacion/comentarios}</h2>
 *
 * <p>Era la ruta natural y fue la primera que se escribio. No se puede usar:
 * {@code /api/v1/moderacion} ya es de <b>metricas-plataforma</b>, que publica
 * ahi los agregados de moderacion de HU-MET (contrato metricas-plataforma
 * 1.6.0), y el borde lo enruta a {@code srv-metricas-plataforma:8087} por una
 * regex. Colgar un segundo servicio del mismo prefijo habria funcionado en
 * pruebas —donde no hay borde— y habria dado 404 en el navegador.
 *
 * <p>Se podria haber forzado con un {@code location ^~} que le ganara a la
 * regex. No se hizo: dejar dos servicios compartiendo prefijo es una trampa
 * para el siguiente que anada una ruta a cualquiera de los dos y no entienda
 * por que se va al servicio equivocado. El prefijo {@code /api/v1/comentarios}
 * es de este servicio y de nadie mas.
 */
@RestController
@RequestMapping("/api/v1/comentarios/moderacion")
public class ModeracionController {

    private final ServicioDeModeracion servicio;

    public ModeracionController(ServicioDeModeracion servicio) {
        this.servicio = servicio;
    }

    /**
     * La cola priorizada. Vacia es 200 con lista vacia, no 404 (CA-03).
     * {@code marcado=true} es la lista de seguimiento especial (7.3.3).
     */
    @GetMapping
    public ColaResponse cola(
            @RequestParam(required = false) String productoId,
            @RequestParam(required = false) Boolean marcado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return ColaResponse.desde(servicio.cola(productoId, marcado, pagina, Math.min(tamano, 100)));
    }

    /** El comentario con sus reportes y su historial: todo lo que hace falta para decidir. */
    @GetMapping("/{commentId}")
    public DetalleResponse detalle(@PathVariable String commentId) {
        return DetalleResponse.desde(servicio.detalle(commentId));
    }

    /**
     * Los comentarios de un autor, en cualquier estado, para decidir sobre el
     * (1.7.0). Solo lectura. La pagina fuera de rango no es un error: el
     * servicio la corrige.
     */
    @GetMapping("/autores/{autorId}/comentarios")
    public HistorialDelAutorResponse historialDelAutor(
            @PathVariable String autorId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return HistorialDelAutorResponse.desde(
                servicio.historialDelAutor(autorId, pagina, Math.min(tamano, 100)));
    }

    /** La decision. El motivo es obligatorio, incluida APROBAR; EDITAR exige textoNuevo. */
    @PostMapping("/{commentId}/decision")
    public ResponseEntity<DecisionResponse> resolver(
            @PathVariable String commentId,
            @AuthenticationPrincipal Jwt moderador,
            @RequestBody DecisionRequest peticion,
            HttpServletRequest origen) {

        ServicioDeModeracion.Resuelto resuelto = servicio.resolver(
                commentId,
                IdentidadDelToken.idDe(moderador).toString(),
                IdentidadDelToken.apodoDe(moderador),
                peticion.accion(),
                peticion.motivo(),
                peticion.textoNuevo(),
                OrigenDeLaPeticion.ipDe(origen));

        return ResponseEntity.status(HttpStatus.OK).body(DecisionResponse.desde(resuelto));
    }

    // ------------------------------------------------------------------ DTOs

    /** {@code DecisionRequest}; {@code textoNuevo} solo cuenta con EDITAR (1.4.0). */
    public record DecisionRequest(AccionDeModeracion accion, String motivo, String textoNuevo) {
    }

    /**
     * {@code AsientoDeModeracion} del contrato. {@code textoAnterior} y
     * {@code textoNuevo} solo en EDITAR (nulos en el resto). La IP de origen
     * se guarda pero no sale por aqui.
     */
    public record AsientoResponse(String id, AccionDeModeracion accion, String moderadorId,
            String apodoModerador, String motivo, String estadoAnterior, String estadoNuevo,
            String fecha,
            @JsonInclude(JsonInclude.Include.ALWAYS) String textoAnterior,
            @JsonInclude(JsonInclude.Include.ALWAYS) String textoNuevo) {

        static AsientoResponse desde(AsientoDeModeracion a) {
            return new AsientoResponse(a.id(), a.accion(), a.moderadorId(), a.apodoModerador(),
                    a.motivo(), a.estadoAnterior().name(), a.estadoNuevo().name(),
                    a.fecha().toString(), a.textoAnterior(), a.textoNuevo());
        }
    }

    public record ReporteResponse(String id, String comentarioId, CategoriaDeReporte categoria,
            String descripcion, String fecha) {

        static ReporteResponse desde(RegistroDeReporte r) {
            return new ReporteResponse(r.id(), r.comentarioId(), r.categoria(),
                    r.descripcion(), r.fecha().toString());
        }
    }

    public record EntradaResponse(ComentarioResponse comentario, int reportes,
            Map<String, Long> porCategoria, String primerReporte, boolean prioridadElevada) {

        static EntradaResponse desde(ServicioDeModeracion.Entrada e) {
            return new EntradaResponse(
                    ComentarioResponse.paraModeracion(e.comentario()),
                    e.reportes(),
                    e.porCategoria().entrySet().stream()
                            .collect(Collectors.toMap(x -> x.getKey().name(), Map.Entry::getValue)),
                    e.primerReporte().toString(),
                    e.prioridadElevada());
        }
    }

    public record ColaResponse(List<EntradaResponse> entradas, int total, int pagina, int tamano) {

        static ColaResponse desde(ServicioDeModeracion.Cola cola) {
            return new ColaResponse(
                    cola.entradas().stream().map(EntradaResponse::desde).toList(),
                    cola.total(), cola.pagina(), cola.tamano());
        }
    }

    public record DetalleResponse(ComentarioResponse comentario, List<ReporteResponse> reportes,
            List<AsientoResponse> historial) {

        static DetalleResponse desde(ServicioDeModeracion.Detalle d) {
            return new DetalleResponse(
                    ComentarioResponse.paraModeracion(d.comentario()),
                    d.reportes().stream().map(ReporteResponse::desde).toList(),
                    d.historial().stream().map(AsientoResponse::desde).toList());
        }
    }

    /**
     * {@code ItemDelHistorial} del contrato: lo minimo para decidir sobre el
     * autor. Sin imagenes, estrellas ni marca de seguimiento, y sin el autor,
     * que ya es el de la respuesta.
     */
    public record ItemDelHistorialResponse(String id, String productoId, String texto,
            String fechaPublicacion, String estado, boolean editado) {

        static ItemDelHistorialResponse desde(ResumenDeComentario r) {
            return new ItemDelHistorialResponse(r.id(), r.productoId(), r.texto(),
                    r.fechaPublicacion().toString(), r.estado().name(), r.editado());
        }
    }

    /** {@code apodoAutor} no sale si el autor no tiene comentarios. */
    public record HistorialDelAutorResponse(String autorId,
            @JsonInclude(JsonInclude.Include.NON_NULL) String apodoAutor,
            List<ItemDelHistorialResponse> comentarios, long total, int pagina, int tamano) {

        static HistorialDelAutorResponse desde(ServicioDeModeracion.Historial h) {
            return new HistorialDelAutorResponse(h.autorId(), h.apodoAutor(),
                    h.comentarios().stream().map(ItemDelHistorialResponse::desde).toList(),
                    h.total(), h.pagina(), h.tamano());
        }
    }

    public record DecisionResponse(ComentarioResponse comentario, AsientoResponse asiento,
            boolean autorNotificado) {

        static DecisionResponse desde(ServicioDeModeracion.Resuelto r) {
            return new DecisionResponse(
                    ComentarioResponse.paraModeracion(r.comentario()),
                    AsientoResponse.desde(r.asiento()),
                    r.autorNotificado());
        }
    }
}
