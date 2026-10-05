package nexus.combate;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class FabricaProcesadorPerdidaEquipo {

    private final InventarioBotin inventario;
    private final CatalogoBotin catalogo;
    private final TransferidorEquipo transferidor;
    private final Function<String, SelectorGanador> selectorPorOperacion;

    public FabricaProcesadorPerdidaEquipo(
            InventarioBotin inventario,
            CatalogoBotin catalogo,
            TransferidorEquipo transferidor,
            SelectorGanador selector) {
        this(inventario, catalogo, transferidor, selectorPorOperacion(selector));
    }

    public FabricaProcesadorPerdidaEquipo(
            InventarioBotin inventario,
            CatalogoBotin catalogo,
            TransferidorEquipo transferidor,
            Function<String, SelectorGanador> selectorPorOperacion) {
        this.inventario = Objects.requireNonNull(inventario, "El inventario es obligatorio");
        this.catalogo = Objects.requireNonNull(catalogo, "El catalogo es obligatorio");
        this.transferidor = Objects.requireNonNull(transferidor, "El transferidor es obligatorio");
        this.selectorPorOperacion = Objects.requireNonNull(selectorPorOperacion, "El selector es obligatorio");
    }

    public ProcesadorPerdidaEquipo crear(
            String operacionId,
            List<ParticipantePerdidaEquipo> participantes) {
        if (operacionId == null || operacionId.isBlank()) {
            throw new IllegalArgumentException("operacionId es obligatorio");
        }
        return new ProcesadorPerdidaEquipo(
                operacionId,
                participantes,
                inventario,
                catalogo,
                transferidor,
                Objects.requireNonNull(selectorPorOperacion.apply(operacionId), "El selector es obligatorio"));
    }

    private static Function<String, SelectorGanador> selectorPorOperacion(SelectorGanador selector) {
        SelectorGanador requerido = Objects.requireNonNull(selector, "El selector es obligatorio");
        return operacionId -> requerido;
    }
}
