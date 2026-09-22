package nexus.inventario.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record BloquearEnSubastaRequest(
        @NotNull UUID propietarioUid,
        @NotNull UUID subastaId) {
}
