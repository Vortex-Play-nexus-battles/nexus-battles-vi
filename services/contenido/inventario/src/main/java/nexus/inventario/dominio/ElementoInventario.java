package nexus.inventario.dominio;

import java.util.Objects;

/** Instancia propia del jugador que referencia un producto del catalogo. */
public record ElementoInventario(
        String id,
        String productoId,
        TipoElementoInventario tipo,
        String nombrePropio,
        ParteArmadura parteArmadura) {

    public ElementoInventario(
            String id,
            String productoId,
            TipoElementoInventario tipo,
            String nombrePropio) {
        this(id, productoId, tipo, nombrePropio, null);
    }

    public ElementoInventario {
        exigirTexto(id, "id");
        exigirTexto(productoId, "productoId");
        Objects.requireNonNull(tipo, "tipo no puede ser nulo");
        exigirTexto(nombrePropio, "nombrePropio");
        if (tipo != TipoElementoInventario.ARMADURA && parteArmadura != null) {
            throw new IllegalArgumentException("Solo una armadura puede declarar una parte");
        }
    }

    public ElementoInventario renombrar(String nuevoNombre) {
        return new ElementoInventario(id, productoId, tipo, nuevoNombre, parteArmadura);
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " no puede estar vacio");
        }
    }
}
