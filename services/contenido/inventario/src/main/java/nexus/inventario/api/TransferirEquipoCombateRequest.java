package nexus.inventario.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record TransferirEquipoCombateRequest(
        @NotEmpty List<@Valid MovimientoCombateRequest> transferencias) {
}
