package nexus.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * HU-PRD-007 — qué respaldo restaurar y, desde SA-6 (contrato 1.10.0), por qué:
 * el motivo queda en el historial con la reversión (§7.2.5). La consola lo
 * exige; la API lo admite vacío para no romper a quien ya revierte.
 */
public record SolicitudRevertirProducto(
        @NotBlank(message = "respaldoId es obligatorio") String respaldoId,
        @Size(max = 500, message = "motivo admite como máximo 500 caracteres") String motivo) {

        /** Sin motivo, como antes de SA-6. */
        public SolicitudRevertirProducto(String respaldoId) {
                this(respaldoId, null);
        }
}
