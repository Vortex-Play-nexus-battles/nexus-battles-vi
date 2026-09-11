package nexus.combate;

public record ParticipanteBotin(
        String combatienteId,
        String propietarioId,
        String heroeInventarioId) {

    public ParticipanteBotin {
        exigirTexto(combatienteId, "combatienteId");
        exigirTexto(propietarioId, "propietarioId");
        exigirTexto(heroeInventarioId, "heroeInventarioId");
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
    }
}
