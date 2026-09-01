package nexus.combate;

/** Estado mínimo de un héroe durante una partida. */
public record Combatiente(String id, String equipoId, int vida, boolean participa) {

    public Combatiente {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("El identificador del combatiente es obligatorio");
        }
        if (equipoId == null || equipoId.isBlank()) {
            throw new IllegalArgumentException("El identificador del equipo es obligatorio");
        }
        if (vida < 0) {
            throw new IllegalArgumentException("La vida no puede ser negativa");
        }
        if (vida == 0 && participa) {
            throw new IllegalArgumentException("Un combatiente sin vida no puede participar");
        }
    }

    public static Combatiente nuevo(String id, String equipoId, int vidaInicial) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("El identificador del combatiente es obligatorio");
        }
        if (equipoId == null || equipoId.isBlank()) {
            throw new IllegalArgumentException("El identificador del equipo es obligatorio");
        }
        if (vidaInicial <= 0) {
            throw new IllegalArgumentException("La vida inicial debe ser positiva");
        }
        return new Combatiente(id, equipoId, vidaInicial, true);
    }

    Combatiente recibirDanio(int danio) {
        if (danio < 0) {
            throw new IllegalArgumentException("El daño no puede ser negativo");
        }
        int vidaRestante = Math.max(0, vida - danio);
        return new Combatiente(id, equipoId, vidaRestante, vidaRestante > 0);
    }

    Combatiente perderPorInactividad() {
        return new Combatiente(id, equipoId, vida, false);
    }
}
