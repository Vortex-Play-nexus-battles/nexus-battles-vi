package com.nexusbattles.ms_subastas.subastas.service;

import com.nexusbattles.ms_subastas.panel.repository.VistaSubastaRepository;
import com.nexusbattles.ms_subastas.pujas.service.SubastaNoEncontradaException;
import com.nexusbattles.ms_subastas.subastas.dto.SubastaDetalleResponse;
import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * La ficha de una subasta ({@code GET /subastas/{subastaId}}, B8), en
 * cualquier estado: el historial necesita poder abrir las cerradas.
 *
 * <p>Cuenta visualizaciones unicas («Estadisticas de visualizaciones», 7.7.9):
 * una por jugador con sesion, sin contar al vendedor. Las visitas sin sesion
 * no cuentan porque no hay forma honesta de no contarlas dos veces; la
 * pantalla sondea cada pocos segundos y contar cada peticion inflaria el
 * numero sin decir nada.
 */
@Service
public class FichaDeSubastaService {

    private final SubastaRepository subastas;
    private final VistaSubastaRepository vistas;
    private final Clock clock;

    public FichaDeSubastaService(SubastaRepository subastas, VistaSubastaRepository vistas, Clock clock) {
        this.subastas = Objects.requireNonNull(subastas);
        this.vistas = Objects.requireNonNull(vistas);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * @param quienMira el jugador de la sesion, o null si mira un visitante
     */
    @Transactional
    public SubastaDetalleResponse ficha(UUID subastaId, UUID quienMira) {
        Subasta subasta = subastas.findById(subastaId).orElseThrow(() -> new SubastaNoEncontradaException(subastaId));
        int vistasTotales = subasta.getVistas();
        if (quienMira != null && !subasta.esVendedor(quienMira)
                && vistas.registrarSiEsNueva(subastaId, quienMira, clock.instant()) > 0) {
            vistas.sumarVista(subastaId);
            vistasTotales++;
        }
        return SubastaDetalleResponse.desde(subasta, reputacionDe(subasta.getVendedorId()), vistasTotales, quienMira);
    }

    private SubastaDetalleResponse.Reputacion reputacionDe(UUID vendedorId) {
        Map<EstadoSubasta, Long> porEstado = new EnumMap<>(EstadoSubasta.class);
        for (Object[] fila : subastas.terminadasPorEstado(vendedorId)) {
            porEstado.put((EstadoSubasta) fila[0], ((Number) fila[1]).longValue());
        }
        return SubastaDetalleResponse.Reputacion.de(
                porEstado.getOrDefault(EstadoSubasta.ADJUDICADA, 0L),
                porEstado.getOrDefault(EstadoSubasta.SIN_ADJUDICACION, 0L),
                porEstado.getOrDefault(EstadoSubasta.CANCELADA, 0L));
    }
}
