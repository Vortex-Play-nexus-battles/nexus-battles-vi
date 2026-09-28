package nexus.combate.reglas;

import java.util.Objects;

/**
 * La accion no se puede jugar ahora — 409 {@code accion-no-permitida}.
 *
 * <p>El servidor valida todo lo que el cliente podria falsear: el cliente solo
 * elige accion y objetivo. El mensaje es apto para el jugador: llega tal cual a
 * su cola privada.
 */
public class AccionNoPermitida extends RuntimeException {

    private final MotivoDeRechazo motivo;

    public AccionNoPermitida(MotivoDeRechazo motivo, String mensaje) {
        super(mensaje);
        this.motivo = Objects.requireNonNull(motivo);
    }

    public MotivoDeRechazo motivo() {
        return motivo;
    }
}
