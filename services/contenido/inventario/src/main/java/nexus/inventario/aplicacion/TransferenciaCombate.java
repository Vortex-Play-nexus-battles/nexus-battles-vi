package nexus.inventario.aplicacion;

public record TransferenciaCombate(
        String elementoId,
        String propietarioOrigenId,
        String heroeOrigenId,
        String propietarioDestinoId) {

    public TransferenciaCombate {
        exigirTexto(elementoId, "elementoId");
        exigirTexto(propietarioOrigenId, "propietarioOrigenId");
        exigirTexto(heroeOrigenId, "heroeOrigenId");
        exigirTexto(propietarioDestinoId, "propietarioDestinoId");
        if (propietarioOrigenId.equals(propietarioDestinoId)) {
            throw new IllegalArgumentException("El origen y el destino deben ser diferentes");
        }
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
    }
}
