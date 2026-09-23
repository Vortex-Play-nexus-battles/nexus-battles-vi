package com.nexusbattles.plataforma.comentarios.moderacion;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.nexusbattles.comun.seguridad.IdentidadDelToken;
import com.nexusbattles.plataforma.comentarios.Comentario;
import com.nexusbattles.plataforma.comentarios.publicacion.ComentariosController.ComentarioResponse;

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
 * La cola y las acciones del moderador — RF-COM-005 y RF-COM-008, contrato 1.3.0.
 *
 * <p>El moderador sale SIEMPRE del token. Ni la cola ni la decision leen un
 * identificador del cuerpo: un asiento de moderacion que dijera quien actuo a
 * partir de un campo enviado por el cliente no serviria para nada, porque
 * cualquiera podria firmar una decision con el nombre de otro.
 *
 * <p>Quien puede entrar lo decide {@code SecurityConfig} por rol. Aqui no hay
 * ni un {@code if (rol == ...)}: la autorizacion se declara en un sitio.
 */
@RestController
@RequestMapping("/api/v1/moderacion/comentarios")
public class ModeracionController {

    private final ServicioDeModeracion servicio;

    public ModeracionController(ServicioDeModeracion servicio) {
        this.servicio = servicio;
    }

    /** La cola priorizada. Vacia es 200 con lista vacia, no 404 (CA-03). */
    @GetMapping
    public ColaResponse cola(
            @RequestParam(required = false) String productoId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return ColaResponse.desde(servicio.cola(productoId, pagina, Math.min(tamano, 100)));
    }

    /** El comentario con sus reportes y su historial: todo lo que hace falta para decidir. */
    @GetMapping("/{commentId}")
    public DetalleResponse detalle(@PathVariable String commentId) {
        return DetalleResponse.desde(servicio.detalle(commentId));
    }

    /** La decision. El motivo es obligatorio, incluida APROBAR. */
    @PostMapping("/{commentId}/decision")
    public ResponseEntity<DecisionResponse> resolver(
            @PathVariable String commentId,
            @AuthenticationPrincipal Jwt moderador,
            @RequestBody DecisionRequest peticion) {

        ServicioDeModeracion.Resuelto resuelto = servicio.resolver(
                commentId,
                IdentidadDelToken.idDe(moderador).toString(),
                IdentidadDelToken.apodoDe(moderador),
                peticion.accion(),
                peticion.motivo());

        return ResponseEntity.status(HttpStatus.OK).body(DecisionResponse.desde(resuelto));
    }

    // ------------------------------------------------------------------ DTOs

    public record DecisionRequest(AccionDeModeracion accion, String motivo) {
    }

    public record AsientoResponse(String id, AccionDeModeracion accion, String moderadorId,
            String apodoModerador, String motivo, String estadoAnterior, String estadoNuevo,
            String fecha) {

        static AsientoResponse desde(AsientoDeModeracion a) {
            return new AsientoResponse(a.id(), a.accion(), a.moderadorId(), a.apodoModerador(),
                    a.motivo(), a.estadoAnterior().name(), a.estadoNuevo().name(),
                    a.fecha().toString());
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
            Map<String, Long> porCategoria, String primerReporte) {

        static EntradaResponse desde(ServicioDeModeracion.Entrada e) {
            return new EntradaResponse(
                    ComentarioResponse.desde(e.comentario(), false),
                    e.reportes(),
                    e.porCategoria().entrySet().stream()
                            .collect(Collectors.toMap(x -> x.getKey().name(), Map.Entry::getValue)),
                    e.primerReporte().toString());
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
                    ComentarioResponse.desde(d.comentario(), false),
                    d.reportes().stream().map(ReporteResponse::desde).toList(),
                    d.historial().stream().map(AsientoResponse::desde).toList());
        }
    }

    public record DecisionResponse(ComentarioResponse comentario, AsientoResponse asiento,
            boolean autorNotificado) {

        static DecisionResponse desde(ServicioDeModeracion.Resuelto r) {
            Comentario c = r.comentario();
            return new DecisionResponse(
                    ComentarioResponse.desde(c, false),
                    AsientoResponse.desde(r.asiento()),
                    r.autorNotificado());
        }
    }
}
