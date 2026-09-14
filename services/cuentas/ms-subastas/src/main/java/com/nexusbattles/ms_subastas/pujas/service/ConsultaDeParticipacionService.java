package com.nexusbattles.ms_subastas.pujas.service;

import com.nexusbattles.ms_subastas.pujas.dto.MiParticipacionResponse;
import com.nexusbattles.ms_subastas.pujas.dto.MiResumenResponse;
import com.nexusbattles.ms_subastas.pujas.dto.PujaDelHistorialResponse;
import com.nexusbattles.ms_subastas.pujas.model.EstadoPuja;
import com.nexusbattles.ms_subastas.pujas.model.Puja;
import com.nexusbattles.ms_subastas.pujas.repository.PujaAutomaticaRepository;
import com.nexusbattles.ms_subastas.pujas.repository.PujaRepository;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Consultas de solo lectura sobre una subasta: su historial de pujas y la
 * situacion del jugador que mira.
 *
 * <p>Separado de {@link PujaApplicationService} a proposito. Aquel abre
 * transaccion de escritura y toma el lock pesimista de la fila de la subasta;
 * si estas consultas pasaran por ahi, abrir la pantalla de una subasta
 * bloquearia las pujas de todos los demas mientras se pinta. Aqui solo se lee.
 */
@Service
@RequiredArgsConstructor
public class ConsultaDeParticipacionService {

    private final SubastaRepository subastaRepository;
    private final PujaRepository pujaRepository;
    private final PujaAutomaticaRepository pujaAutomaticaRepository;
    private final ParametrosPuja parametros;
    private final Clock clock;

    /**
     * @param quienMira puede ser null: el historial es publico y se puede ver
     *                  sin sesion. Con jugador, cada linea viene marcada como
     *                  propia o ajena.
     */
    @Transactional(readOnly = true)
    public List<PujaDelHistorialResponse> historial(UUID subastaId, UUID quienMira) {
        exigirQueExista(subastaId);
        return pujaRepository.findBySubastaIdOrderByCreadaEnDesc(subastaId).stream()
                .map(puja -> PujaDelHistorialResponse.de(puja, quienMira))
                .toList();
    }

    @Transactional(readOnly = true)
    public MiParticipacionResponse miParticipacion(UUID subastaId, UUID jugadorId) {
        Subasta subasta = exigirQueExista(subastaId);

        Puja miPujaVigente = pujaRepository.findBySubastaIdAndEstado(subastaId, EstadoPuja.ACTIVA)
                .filter(puja -> puja.getJugadorId().equals(jugadorId))
                .orElse(null);

        boolean vasGanando = jugadorId.equals(subasta.getMejorPostorId());
        // Que te superaron no es lo contrario de ir ganando: hace falta haber
        // pujado antes. Quien nunca pujo no es que vaya perdiendo, es que no
        // esta participando, y la pantalla no debe decirle lo mismo.
        boolean hasPujado = pujaRepository
                .findFirstByJugadorIdAndSubastaIdOrderByCreadaEnDesc(jugadorId, subastaId).isPresent();

        var automatica = pujaAutomaticaRepository.findBySubastaIdAndJugadorId(subastaId, jugadorId);

        return new MiParticipacionResponse(
                vasGanando,
                hasPujado && !vasGanando,
                miPujaVigente == null ? null : miPujaVigente.getMonto(),
                miPujaVigente == null ? BigDecimal.ZERO : miPujaVigente.getMonto(),
                automatica.map(a -> a.getLimite()).orElse(null),
                automatica.map(a -> a.isActiva()).orElse(false),
                segundosParaVolverAPujar(subastaId, jugadorId));
    }

    /**
     * Lo que el jugador tiene en juego sumando todas las subastas.
     *
     * <p>Aparte del endpoint por subasta a proposito: el panel de resumen se ve
     * tambien fuera del detalle, y pedir la situacion de cada subasta del
     * listado para pintarlo seria una consulta por fila.
     */
    @Transactional(readOnly = true)
    public MiResumenResponse miResumen(UUID jugadorId) {
        return new MiResumenResponse(
                pujaRepository.sumarMontoPorJugadorYEstado(jugadorId, EstadoPuja.ACTIVA),
                pujaRepository.countByJugadorIdAndEstado(jugadorId, EstadoPuja.ACTIVA));
    }

    /**
     * Cuanto falta para que este jugador pueda pujar otra vez en ESTA subasta.
     * El intervalo es por subasta, no por cuenta: participar en varias a la vez
     * es justo lo que la historia permite.
     */
    private long segundosParaVolverAPujar(UUID subastaId, UUID jugadorId) {
        return pujaRepository.findFirstByJugadorIdAndSubastaIdOrderByCreadaEnDesc(jugadorId, subastaId)
                .map(ultima -> {
                    long transcurridos = Duration.between(ultima.getCreadaEn(), clock.instant()).getSeconds();
                    return Math.max(0, parametros.getIntervaloMinimoSegundos() - transcurridos);
                })
                .orElse(0L);
    }

    private Subasta exigirQueExista(UUID subastaId) {
        return subastaRepository.findById(subastaId)
                .orElseThrow(() -> new SubastaNoEncontradaException(subastaId));
    }
}
