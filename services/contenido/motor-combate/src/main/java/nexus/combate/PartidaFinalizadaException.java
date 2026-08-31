package nexus.combate;

public final class PartidaFinalizadaException extends IllegalStateException {

    public PartidaFinalizadaException() {
        super("La partida ya finalizó y no admite más acciones");
    }
}
