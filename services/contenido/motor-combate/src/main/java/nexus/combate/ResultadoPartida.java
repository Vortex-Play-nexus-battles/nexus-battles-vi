package nexus.combate;

import java.util.Objects;

public record ResultadoPartida(String ganadorEquipoId, MotivoFinPartida motivo) {

    public ResultadoPartida {
        Objects.requireNonNull(ganadorEquipoId, "El equipo ganador es obligatorio");
        Objects.requireNonNull(motivo, "El motivo de cierre es obligatorio");
    }
}
