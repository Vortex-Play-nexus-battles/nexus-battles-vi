package nexus.combate;

public record ParticipantePerdidaEquipo(
        String combatienteId,
        String equipoId,
        String propietarioId,
        String heroeInventarioId,
        boolean controladoPorIa) {

    public ParticipantePerdidaEquipo(
            String combatienteId,
            String equipoId,
            String propietarioId,
            String heroeInventarioId) {
        this(combatienteId, equipoId, propietarioId, heroeInventarioId, false);
    }

    public static ParticipantePerdidaEquipo maquina(String combatienteId, String equipoId) {
        return new ParticipantePerdidaEquipo(combatienteId, equipoId, null, null, true);
    }

    public ParticipantePerdidaEquipo {
        exigirTexto(combatienteId, "combatienteId");
        exigirTexto(equipoId, "equipoId");
        if (!controladoPorIa) {
            exigirTexto(propietarioId, "propietarioId");
            exigirTexto(heroeInventarioId, "heroeInventarioId");
        }
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
    }
}
