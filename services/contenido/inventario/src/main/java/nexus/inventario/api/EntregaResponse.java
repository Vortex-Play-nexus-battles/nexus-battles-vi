package nexus.inventario.api;

import java.time.Instant;
import java.util.List;
import nexus.inventario.dominio.Entrega;
import nexus.inventario.dominio.OrigenDeEntrega;

/** Esquema {@code Entrega} del contrato de inventario (B4): lo que recibio el jugador y por que canal. */
public record EntregaResponse(
        String id,
        String uid,
        OrigenDeEntrega origen,
        String referencia,
        List<ElementoInventarioResponse> elementos,
        Instant entregadaEn) {

    static EntregaResponse de(Entrega entrega) {
        return new EntregaResponse(
                entrega.id(),
                entrega.uid(),
                entrega.origen(),
                entrega.referencia(),
                entrega.elementos().stream().map(ElementoInventarioResponse::de).toList(),
                entrega.entregadaEn());
    }
}
