package com.nexusbattles.plataforma.comentarios.moderacion;

import java.time.Instant;

/**
 * Lo minimo de un reporte para contarlo en la frecuencia de §7.3.8: cuando,
 * de que categoria, sobre que comentario y de quien. La descripcion libre no
 * se lee: contar no necesita leer lo que alguien escribio.
 *
 * <p>Los dos identificadores solo sirven para contar distintos; no salen de
 * {@link FrecuenciaDeReportes}.
 */
public record ReporteContado(Instant fecha, CategoriaDeReporte categoria, String comentarioId,
        String reportanteId) {
}
