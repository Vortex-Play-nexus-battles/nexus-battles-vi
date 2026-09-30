package nexus.combate;

public record TransferenciaEquipo(
        String elementoId,
        String propietarioOrigenId,
        String heroeOrigenId,
        String propietarioDestinoId) {

    public TransferenciaEquipo {
        exigirTexto(elementoId, "elementoId");
        exigirTexto(propietarioOrigenId, "propietarioOrigenId");
        exigirTexto(heroeOrigenId, "heroeOrigenId");
        exigirTexto(propietarioDestinoId, "propietarioDestinoId");
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
    }
}
