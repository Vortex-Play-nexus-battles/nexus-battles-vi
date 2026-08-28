package nexus.inventario.dominio;

import java.util.Objects;

/** Instancia propia del jugador que referencia un producto del catalogo. */
public record ElementoInventario(
        String id,
        String productoId,
        TipoElementoInventario tipo,
        String nombrePropio) {

    public ElementoInventario {
        exigirTexto(id, "id");
        exigirTexto(productoId, "productoId");
        Objects.requireNonNull(tipo, "tipo no puede ser nulo");
        exigirTexto(nombrePropio, "nombrePropio");
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " no puede estar vacio");
        }
    }
}
