package com.nexusbattles.ms_subastas.subastas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * HU-SUB-011. Una fila del listado paginado -- corresponde al schema
 * SubastaResumen de contracts/openapi/ms-subastas-listado.yaml.
 *
 * <p><b>G4/G5 (listado 1.2.0): sin el {@code uid} del vendedor.</b> El listado
 * y el canal en vivo los lee cualquiera, tambien sin cuenta, y el
 * {@code vendedorId} permitia seguir a un jugador por todo lo que vende (y,
 * con la ficha, que traia apodo y uid juntos, ligar uno con otro). La
 * interfaz solo lo usaba para saber si la subasta es de quien mira: eso lo
 * dice ahora el servidor en {@code esPropia}, con el token de quien pide el
 * listado. En el canal en vivo, que va a todos, no viaja ({@code null}): la
 * interfaz relee el listado al recibir un cambio.
 */
public record SubastaResumenResponse(
    UUID id,
    String nombreProducto,
    String tipoProducto,
    String rareza,
    String miniaturaUrl,
    BigDecimal precioInicial,
    BigDecimal ofertaVigente,
    BigDecimal precioCompraInmediata,
    int cantidadPujas,
    Instant fechaFin,
    boolean esMaestroDeJuego,
    /**
     * Obsoleto desde el listado 1.2.0: siempre {@code null}. Se conserva en
     * la forma para no romper a quien lo leia (regla 2): ya no lleva el
     * {@code uid} del vendedor. Lo que servia para eso es {@code esPropia}.
     */
    @Deprecated String vendedorId,
    String estado,
    @JsonInclude(JsonInclude.Include.NON_NULL) Boolean esPropia
) {

    /**
     * Para el canal en vivo, que va a todos: sin {@code esPropia}.
     *
     * <p>B8: con {@code estado}, que el contrato del canal en vivo ya exigia
     * ({@code contracts/websocket/subastas.yaml}: {@code required [id, estado]})
     * y el resumen no traia. En el listado siempre es ACTIVA; en el canal dice
     * si la subasta se adjudico, quedo sin ofertas o se cancelo.
     */
    public static SubastaResumenResponse desde(Subasta subasta) {
        return construir(subasta, null);
    }

    /**
     * Para el listado: {@code esPropia} si quien mira (su {@code uid}, o null
     * sin sesion) es el vendedor.
     */
    public static SubastaResumenResponse desde(Subasta subasta, UUID quienMira) {
        return construir(subasta, quienMira != null && quienMira.equals(subasta.getVendedorId()));
    }

    private static SubastaResumenResponse construir(Subasta subasta, Boolean esPropia) {
        return new SubastaResumenResponse(
            subasta.getId(),
            subasta.getNombreProducto(),
            subasta.getTipoProducto() != null ? subasta.getTipoProducto().name() : null,
            subasta.getRareza(),
            subasta.getMiniaturaUrl(),
            subasta.getPrecioInicial(),
            subasta.getOfertaVigente(),
            subasta.getPrecioCompraInmediata(),
            subasta.getCantidadPujas(),
            subasta.getFechaFin(),
            subasta.isEsMaestroDeJuego(),
            null,
            subasta.getEstado() != null ? subasta.getEstado().name() : null,
            esPropia
        );
    }
}
