package com.nexusbattles.ms_subastas.pujas.service;

import com.nexusbattles.ms_subastas.panel.repository.SeguimientoRepository;
import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClient;
import com.nexusbattles.ms_subastas.pujas.dto.MiParticipacionResponse;
import com.nexusbattles.ms_subastas.pujas.dto.MiResumenResponse;
import com.nexusbattles.ms_subastas.pujas.dto.ParticipacionResponse;
import com.nexusbattles.ms_subastas.pujas.dto.PujaDelHistorialResponse;
import com.nexusbattles.ms_subastas.pujas.model.EstadoPuja;
import com.nexusbattles.ms_subastas.pujas.model.Puja;
import com.nexusbattles.ms_subastas.pujas.model.PujaAutomatica;
import com.nexusbattles.ms_subastas.pujas.repository.PujaAutomaticaRepository;
import com.nexusbattles.ms_subastas.pujas.repository.PujaRepository;
import com.nexusbattles.ms_subastas.reglas.FuenteDeReglas;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
@Slf4j
public class ConsultaDeParticipacionService {

    private final SubastaRepository subastaRepository;
    private final PujaRepository pujaRepository;
    private final PujaAutomaticaRepository pujaAutomaticaRepository;
    private final FuenteDeReglas reglas;
    private final Clock clock;
    private final CreditoClient creditoClient;
    private final SeguimientoRepository seguimientos;

    public ConsultaDeParticipacionService(SubastaRepository subastaRepository, PujaRepository pujaRepository,
                                          PujaAutomaticaRepository pujaAutomaticaRepository, FuenteDeReglas reglas,
                                          Clock clock, CreditoClient creditoClient,
                                          SeguimientoRepository seguimientos) {
        this.subastaRepository = Objects.requireNonNull(subastaRepository);
        this.pujaRepository = Objects.requireNonNull(pujaRepository);
        this.pujaAutomaticaRepository = Objects.requireNonNull(pujaAutomaticaRepository);
        this.reglas = Objects.requireNonNull(reglas);
        this.clock = Objects.requireNonNull(clock);
        this.creditoClient = Objects.requireNonNull(creditoClient);
        this.seguimientos = Objects.requireNonNull(seguimientos);
    }

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
                segundosParaVolverAPujar(subastaId, jugadorId),
                seguimientos.existsBySubastaIdAndJugadorId(subastaId, jugadorId));
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
                saldoDisponibleOSinSaber(jugadorId),
                pujaRepository.countByJugadorIdAndEstado(jugadorId, EstadoPuja.ACTIVA));
    }

    /**
     * «Mis pujas» (7.7.9, B8): las subastas en las que el jugador pujo o tiene
     * una puja automatica, tambien las ya cerradas, con el estado de su
     * participacion. Tres consultas en total, no una por subasta.
     */
    @Transactional(readOnly = true)
    public List<ParticipacionResponse> misParticipaciones(UUID jugadorId) {
        Map<UUID, Object[]> resumen = new HashMap<>();
        for (Object[] fila : pujaRepository.resumenPorSubastaDe(jugadorId)) {
            resumen.put((UUID) fila[0], fila);
        }
        Set<UUID> ids = new LinkedHashSet<>(resumen.keySet());
        Set<UUID> soloAutomatica = new LinkedHashSet<>();
        for (PujaAutomatica automatica : pujaAutomaticaRepository.findByJugadorId(jugadorId)) {
            if (!resumen.containsKey(automatica.getSubastaId()) && automatica.isActiva()) {
                soloAutomatica.add(automatica.getSubastaId());
            }
        }
        ids.addAll(soloAutomatica);
        if (ids.isEmpty()) {
            return List.of();
        }
        return subastaRepository.findAllById(ids).stream()
                .map(subasta -> {
                    Object[] fila = resumen.get(subasta.getId());
                    return ParticipacionResponse.de(subasta, jugadorId,
                            fila == null ? null : (BigDecimal) fila[1],
                            fila == null ? null : (Instant) fila[2]);
                })
                .sorted(Comparator.comparing(ParticipacionResponse::fechaFin).reversed())
                .toList();
    }

    /**
     * El saldo libre segun ms-finanzas, o {@code null} si ese servicio no
     * responde.
     *
     * <p>El nulo es deliberado y no se sustituye por cero. Un cero le diria al
     * jugador que esta arruinado cuando lo unico que pasa es que no se pudo
     * preguntar, y la pantalla usa este numero para decidir si le deja pujar:
     * con un cero inventado le bloquearia pujas que si puede pagar.
     *
     * <p>Tampoco se deja caer la excepcion. El resumen sirve igual sin el
     * saldo —el retenido y las subastas que va ganando salen de esta misma base
     * de datos—, asi que una averia de creditos no debe dejar al jugador sin
     * pantalla; solo sin esa cifra.
     */
    private BigDecimal saldoDisponibleOSinSaber(UUID jugadorId) {
        try {
            return creditoClient.saldoDisponible(jugadorId);
        } catch (RuntimeException noSeSabe) {
            log.warn("No se pudo consultar el saldo de {}: {}", jugadorId, noSeSabe.getMessage());
            return null;
        }
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
                    return Math.max(0, reglas.vigentes().intervaloMinimoSegundos() - transcurridos);
                })
                .orElse(0L);
    }

    private Subasta exigirQueExista(UUID subastaId) {
        return subastaRepository.findById(subastaId)
                .orElseThrow(() -> new SubastaNoEncontradaException(subastaId));
    }
}
