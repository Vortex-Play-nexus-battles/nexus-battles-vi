package nexus.inventario.dominio;

import java.util.Objects;

/** Instancia propia del jugador que referencia un producto del catalogo. */
public record ElementoInventario(
        String id,
        String productoId,
        TipoElementoInventario tipo,
        String nombrePropio,
        ParteArmadura parteArmadura,
        String subastaId) {

    public ElementoInventario(
            String id,
            String productoId,
            TipoElementoInventario tipo,
            String nombrePropio) {
        this(id, productoId, tipo, nombrePropio, null, null);
    }

    public ElementoInventario(
            String id,
            String productoId,
            TipoElementoInventario tipo,
            String nombrePropio,
            ParteArmadura parteArmadura) {
        this(id, productoId, tipo, nombrePropio, parteArmadura, null);
    }

    public ElementoInventario {
        exigirTexto(id, "id");
        exigirTexto(productoId, "productoId");
        Objects.requireNonNull(tipo, "tipo no puede ser nulo");
        exigirTexto(nombrePropio, "nombrePropio");
        if (tipo != TipoElementoInventario.ARMADURA && parteArmadura != null) {
            throw new IllegalArgumentException("Solo una armadura puede declarar una parte");
        }
        if (subastaId != null && subastaId.isBlank()) {
            throw new IllegalArgumentException("subastaId no puede estar vacio");
        }
    }

    public ElementoInventario renombrar(String nuevoNombre) {
        exigirDisponible();
        return new ElementoInventario(id, productoId, tipo, nuevoNombre, parteArmadura, subastaId);
    }

    public boolean disponible() {
        return subastaId == null;
    }

    public ElementoInventario bloquearEnSubasta(String nuevaSubastaId) {
        exigirTexto(nuevaSubastaId, "subastaId");
        if (nuevaSubastaId.equals(subastaId)) {
            return this;
        }
        if (!disponible()) {
            throw new ElementoNoDisponibleException(
                    "El producto ya esta bloqueado por otra subasta vigente.");
        }
        return new ElementoInventario(
                id, productoId, tipo, nombrePropio, parteArmadura, nuevaSubastaId);
    }

    public ElementoInventario liberarBloqueoSubasta(String subastaQueFinalizo) {
        exigirTexto(subastaQueFinalizo, "subastaId");
        if (disponible()) {
            return this;
        }
        if (!subastaId.equals(subastaQueFinalizo)) {
            throw new ElementoNoDisponibleException(
                    "El aviso no corresponde a la subasta que mantiene el bloqueo.");
        }
        return new ElementoInventario(id, productoId, tipo, nombrePropio, parteArmadura, null);
    }

    public void exigirDisponible() {
        if (!disponible()) {
            throw new ElementoNoDisponibleException(
                    "El producto esta bloqueado por una subasta vigente.");
        }
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " no puede estar vacio");
        }
    }
}
