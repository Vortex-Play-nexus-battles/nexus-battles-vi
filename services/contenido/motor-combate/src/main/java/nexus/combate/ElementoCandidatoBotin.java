package nexus.combate;

import java.util.Objects;

public record ElementoCandidatoBotin(
        String elementoId,
        String productoId,
        TipoBotin tipo,
        String nombrePropio,
        ParteArmaduraBotin parteArmadura,
        OrigenBotin origen) {

    public ElementoCandidatoBotin {
        exigirTexto(elementoId, "elementoId");
        exigirTexto(productoId, "productoId");
        Objects.requireNonNull(tipo, "El tipo del elemento es obligatorio");
        exigirTexto(nombrePropio, "nombrePropio");
        Objects.requireNonNull(origen, "El origen del elemento es obligatorio");
        if (tipo == TipoBotin.ARMADURA && parteArmadura == null) {
            throw new IllegalArgumentException("La armadura debe declarar su parte");
        }
        if (tipo != TipoBotin.ARMADURA && parteArmadura != null) {
            throw new IllegalArgumentException("Solo una armadura puede declarar su parte");
        }
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
    }
}
