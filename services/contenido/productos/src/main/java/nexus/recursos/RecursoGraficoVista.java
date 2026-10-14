package nexus.recursos;

import java.time.Instant;
import java.util.List;

/**
 * Un recurso gráfico tal como lo publica el contrato ({@code RecursoGrafico},
 * productos.yaml 1.10.0): sus datos, sin los bytes, que se piden por su
 * {@code url}.
 *
 * @param usadoEn los productos que hoy lo tienen como imagen
 */
public record RecursoGraficoVista(
        String id,
        ClaseDeRecurso clase,
        FormatoDeRecurso formato,
        String tipoContenido,
        int bytes,
        int ancho,
        int alto,
        String nombre,
        String productoId,
        String url,
        String subidoPor,
        Instant subidoEn,
        Instant retiradoEn,
        String retiradoPor,
        String motivoRetiro,
        List<UsoDeRecurso> usadoEn) {

    /** Un producto que usa el recurso como imagen. */
    public record UsoDeRecurso(String productoId, String nombre) {
    }

    public RecursoGraficoVista {
        usadoEn = usadoEn == null ? List.of() : List.copyOf(usadoEn);
    }

    public static RecursoGraficoVista de(RecursoGrafico r, List<UsoDeRecurso> usadoEn) {
        return new RecursoGraficoVista(r.id(), r.clase(), r.formato(), r.formato().tipoMime(), r.bytes(),
                r.ancho(), r.alto(), r.nombre(), r.productoId(), r.url(), r.subidoPor(), r.subidoEn(),
                r.retiradoEn(), r.retiradoPor(), r.motivoRetiro(), usadoEn);
    }
}
