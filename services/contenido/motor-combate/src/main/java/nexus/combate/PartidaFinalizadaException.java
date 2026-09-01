package nexus.combate;

/** Se lanza al intentar modificar un combate que ya produjo un resultado. */
public final class PartidaFinalizadaException extends IllegalStateException {

    public PartidaFinalizadaException() {
        super("La partida ya finalizó y no admite más acciones");
    }
}
