package nexus.combate.reglas;

import java.util.Objects;

/**
 * Algo que paso, para que quien lleva la partida lo narre.
 *
 * @param tipo        que paso
 * @param combatiente a quien le paso
 * @param origen      quien lo causo, o nulo
 * @param efecto      nombre del efecto, accion u objeto, o nulo
 * @param cantidad    cuanto, o nulo
 */
public record Evento(TipoDeEvento tipo, String combatiente, String origen, String efecto, Integer cantidad) {

    public Evento {
        Objects.requireNonNull(tipo);
        Objects.requireNonNull(combatiente);
    }
}
