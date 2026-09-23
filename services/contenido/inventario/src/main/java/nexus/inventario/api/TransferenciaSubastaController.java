package nexus.inventario.api;

import jakarta.validation.Valid;
import nexus.inventario.aplicacion.TransferirElementoPorSubasta;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cambia de dueno un elemento al concluir una subasta (HU-SUB-004).
 *
 * <p>En su propio controlador y no dentro de {@code bloqueo-subasta} porque no
 * es una operacion sobre el bloqueo: es sobre la propiedad. El bloqueo se
 * libera como efecto, no como fin.
 *
 * <p>Repetir la llamada con la misma clave termina bien y no vuelve a mover
 * nada. Importa: el cierre por vencimiento de ms-subastas corre dentro de una
 * transaccion y reintenta la misma subasta cada 30 s.
 */
@RestController
@RequestMapping("/api/v1/inventario/elementos/{elementoId}/transferencias")
public class TransferenciaSubastaController {

    private final TransferirElementoPorSubasta transferencias;

    public TransferenciaSubastaController(TransferirElementoPorSubasta transferencias) {
        this.transferencias = transferencias;
    }

    @PostMapping
    public ElementoInventarioResponse transferir(
            @RequestHeader(name = "Idempotency-Key", required = false) String claveIdempotencia,
            @PathVariable String elementoId,
            @Valid @RequestBody TransferirElementoRequest solicitud) {
        return ElementoInventarioResponse.de(transferencias.transferir(
                elementoId, solicitud.nuevoPropietarioUid(), solicitud.subastaId(), claveIdempotencia));
    }
}
