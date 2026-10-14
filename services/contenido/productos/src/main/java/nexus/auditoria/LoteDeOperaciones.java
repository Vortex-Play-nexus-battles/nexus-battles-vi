package nexus.auditoria;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Un lote de operaciones administrativas para la bitácora central — SA-18,
 * productos.yaml 1.12.0. Mismas reglas que el lote de cambios del catálogo
 * (1.6.0).
 *
 * @param hasta       hasta dónde llega el lote: quien consulta lo envía como
 *                    {@code desde} la vez siguiente
 * @param completo    false si quedaron operaciones posteriores a {@code hasta}
 *                    sin entregar porque el lote se cortó en el límite
 * @param operaciones de la más antigua a la más reciente
 */
public record LoteDeOperaciones(
        Instant hasta,
        boolean completo,
        List<OperacionAdministrativa> operaciones) {

        public LoteDeOperaciones {
                hasta = Objects.requireNonNull(hasta, "hasta es obligatorio");
                operaciones = List.copyOf(Objects.requireNonNull(operaciones, "las operaciones son obligatorias"));
        }
}
