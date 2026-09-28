package com.nexusbattles.ms_finanzas.partidas;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Proyección de lectura para la pantalla "Mis cofres" — esquema
 * {@code ResumenCofre} de cofres.yaml 1.1.0. No expone el {@code uidJugador}
 * (redundante: el llamador ya se autenticó como ese jugador).
 *
 * <p>Desde B7 trae el contenido real —{@code premios}—, cómo va su entrega al
 * inventario y el {@code sorteo} (semilla y versión de la tabla) con el que se
 * puede repetir. Los cofres anteriores a B7 salen con {@code premios} vacío,
 * {@code SIN_CONTENIDO} y sin sorteo: no tenían contenido, y se dice.
 */
public record ResumenCofre(
        UUID id,
        String contenido,
        Instant entregadoEn,
        String semanaIso,
        List<Premio> premios,
        String estadoEntrega,
        String entregaId,
        Sorteo sorteo) {

    /** Sin el detalle de 1.1.0: el constructor de antes de B7. */
    public ResumenCofre(UUID id, String contenido, Instant entregadoEn) {
        this(id, contenido, entregadoEn, null, List.of(), null, null, null);
    }

    /** Esquema {@code PremioDeCofre}. */
    public record Premio(String productoId, int cantidad) { }

    /** Cómo se sorteó el contenido. */
    public record Sorteo(long semilla, String tablaVersion) { }

    public static ResumenCofre desde(CofreEntregado cofre) {
        return new ResumenCofre(
                cofre.getId(),
                cofre.getContenido(),
                cofre.getEntregadoEn(),
                cofre.getSemanaIso(),
                cofre.getPremios().stream().map(p -> new Premio(p.getProductoId(), p.getCantidad())).toList(),
                cofre.getEstadoEntrega() == null ? null : cofre.getEstadoEntrega().name(),
                cofre.getEntregaId(),
                cofre.getSemilla() == null ? null : new Sorteo(cofre.getSemilla(), cofre.getTablaVersion()));
    }
}
