package nexus.combate;

public record ParticipantePerdidaEquipo(
        String combatienteId,
        String equipoId,
        String propietarioId,
        String heroeInventarioId) {

    public ParticipantePerdidaEquipo {
        exigirTexto(combatienteId, "combatienteId");
        exigirTexto(equipoId, "equipoId");
        exigirTexto(propietarioId, "propietarioId");
        exigirTexto(heroeInventarioId, "heroeInventarioId");
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
    }
}
