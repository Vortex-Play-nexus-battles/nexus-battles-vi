package nexus.combate;

import java.util.List;
import java.util.Objects;

public final class FabricaProcesadorPerdidaEquipo {

    private final InventarioBotin inventario;
    private final CatalogoBotin catalogo;
    private final TransferidorEquipo transferidor;
    private final SelectorGanador selector;

    public FabricaProcesadorPerdidaEquipo(
            InventarioBotin inventario,
            CatalogoBotin catalogo,
            TransferidorEquipo transferidor,
            SelectorGanador selector) {
        this.inventario = Objects.requireNonNull(inventario, "El inventario es obligatorio");
        this.catalogo = Objects.requireNonNull(catalogo, "El catalogo es obligatorio");
        this.transferidor = Objects.requireNonNull(transferidor, "El transferidor es obligatorio");
        this.selector = Objects.requireNonNull(selector, "El selector es obligatorio");
    }

    public ProcesadorPerdidaEquipo crear(
            String operacionId,
            List<ParticipantePerdidaEquipo> participantes) {
        return new ProcesadorPerdidaEquipo(
                operacionId,
                participantes,
                inventario,
                catalogo,
                transferidor,
                selector);
    }
}
