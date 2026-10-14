package nexus.inventario.api;

import java.util.List;

public record TransferenciaCombateResponse(
        String operacionId,
        List<String> elementosTransferidos) {

    public TransferenciaCombateResponse {
        elementosTransferidos = List.copyOf(elementosTransferidos);
    }
}
