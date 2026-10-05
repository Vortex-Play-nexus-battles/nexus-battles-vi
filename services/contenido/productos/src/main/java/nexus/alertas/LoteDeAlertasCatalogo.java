package nexus.alertas;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Un lote de cambios del catalogo para otro servicio — HU-NOT-001 (#532),
 * productos.yaml 1.6.0.
 *
 * @param hasta    hasta donde llega el lote: quien consulta lo guarda y lo
 *                 envia como {@code desde} la vez siguiente
 * @param completo false si quedaron cambios posteriores a {@code hasta} sin
 *                 entregar porque el lote se corto en el limite
 * @param alertas  del cambio mas antiguo al mas reciente
 */
public record LoteDeAlertasCatalogo(
        Instant hasta,
        boolean completo,
        List<AlertaCatalogo> alertas) {

    public LoteDeAlertasCatalogo {
        hasta = Objects.requireNonNull(hasta, "hasta es obligatorio");
        alertas = List.copyOf(Objects.requireNonNull(alertas, "las alertas son obligatorias"));
    }
}
