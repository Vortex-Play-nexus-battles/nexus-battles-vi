package nexus.combate;

public record DerrotaEnemigo(
        String accionId,
        String jugadorGanadorId,
        String propietarioEnemigoId,
        String heroeEnemigoId) {

    public DerrotaEnemigo {
        exigirTexto(accionId, "accionId");
        exigirTexto(jugadorGanadorId, "jugadorGanadorId");
        exigirTexto(propietarioEnemigoId, "propietarioEnemigoId");
        exigirTexto(heroeEnemigoId, "heroeEnemigoId");
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
    }
}
