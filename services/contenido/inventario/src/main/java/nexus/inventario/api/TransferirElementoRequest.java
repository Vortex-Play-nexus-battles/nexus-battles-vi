package nexus.inventario.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Transferencia de propiedad al concluir una subasta (HU-SUB-004).
 *
 * <p>El nuevo dueno viaja en el CUERPO y como UUID, igual que
 * {@link BloquearEnSubastaRequest}. No puede salir de una cabecera de
 * identidad: de las tres veces que ms-subastas llama a esta operacion, dos las
 * dispara un trabajo programado sin peticion ni token de jugador —el cierre por
 * vencimiento— y la tercera devuelve el elemento AL VENDEDOR como compensacion
 * cuando el cobro falla, y el vendedor no es quien pidio nada.
 */
public record TransferirElementoRequest(
        @NotNull UUID nuevoPropietarioUid,
        @NotNull UUID subastaId) {
}
