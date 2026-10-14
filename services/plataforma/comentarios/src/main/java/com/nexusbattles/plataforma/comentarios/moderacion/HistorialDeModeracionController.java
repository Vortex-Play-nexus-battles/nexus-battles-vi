package com.nexusbattles.plataforma.comentarios.moderacion;

import java.time.Instant;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code GET /api/v1/comentarios/moderacion/historial} — el historico de todas
 * las acciones de moderacion (§7.3.3, comentarios.yaml 1.14.0). Misma guarda
 * que el resto de la moderacion (Moderador, Administrador, Super
 * Administrador): la pone {@code SecurityConfig} sobre el prefijo. El literal
 * {@code /historial} le gana al patron {@code /{commentId}} del detalle.
 */
@RestController
@RequestMapping("/api/v1/comentarios/moderacion/historial")
public class HistorialDeModeracionController {

    private final HistorialDeModeracion historial;

    public HistorialDeModeracionController(HistorialDeModeracion historial) {
        this.historial = historial;
    }

    @GetMapping
    public HistorialResponse consultar(
            @RequestParam(required = false) AccionDeModeracion accion,
            @RequestParam(required = false) String moderador,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "16") int tamano) {
        return HistorialResponse.desde(historial.consultar(
                new HistorialDeModeracion.Filtro(accion, moderador, desde, hasta), pagina, tamano));
    }

    /**
     * {@code AsientoDelHistorial} del contrato: el asiento sin la IP de origen,
     * con lo minimo del comentario para reconocerlo. Sin comentario (no deberia
     * pasar: un eliminado se conserva), sus campos van nulos.
     */
    public record AsientoDelHistorialResponse(String id, String fecha, AccionDeModeracion accion,
            String moderadorId, String apodoModerador, String motivo, String estadoAnterior, String estadoNuevo,
            boolean textoEditado, String comentarioId,
            @JsonInclude(JsonInclude.Include.ALWAYS) String productoId,
            @JsonInclude(JsonInclude.Include.ALWAYS) String autorId,
            @JsonInclude(JsonInclude.Include.ALWAYS) String apodoAutor,
            @JsonInclude(JsonInclude.Include.ALWAYS) String extracto) {

        static AsientoDelHistorialResponse desde(HistorialDeModeracion.Fila fila) {
            AsientoDeModeracion a = fila.asiento();
            var c = fila.comentario();
            return new AsientoDelHistorialResponse(a.id(), a.fecha().toString(), a.accion(), a.moderadorId(),
                    a.apodoModerador(), a.motivo(), a.estadoAnterior().name(), a.estadoNuevo().name(),
                    a.textoNuevo() != null, a.comentarioId(),
                    c == null ? null : c.productoId(),
                    c == null ? null : c.autorId(),
                    c == null ? null : c.apodoAutor(),
                    fila.extracto());
        }
    }

    public record HistorialResponse(List<AsientoDelHistorialResponse> contenido, int pagina, int tamano,
            long total, int totalPaginas) {

        static HistorialResponse desde(HistorialDeModeracion.Pagina p) {
            return new HistorialResponse(p.filas().stream().map(AsientoDelHistorialResponse::desde).toList(),
                    p.pagina(), p.tamano(), p.total(), p.totalPaginas());
        }
    }
}
