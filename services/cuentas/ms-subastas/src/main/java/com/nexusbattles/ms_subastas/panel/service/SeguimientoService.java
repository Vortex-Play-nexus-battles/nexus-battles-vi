package com.nexusbattles.ms_subastas.panel.service;

import com.nexusbattles.ms_subastas.panel.dto.PanelDtos;
import com.nexusbattles.ms_subastas.panel.model.Seguimiento;
import com.nexusbattles.ms_subastas.panel.repository.SeguimientoRepository;
import com.nexusbattles.ms_subastas.pujas.service.SubastaNoEncontradaException;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * La «Lista de seguimiento» de 7.7.9: agregar, quitar y listar. Quien sigue
 * una subasta recibe sus cambios y el recordatorio de 1 hora antes del cierre
 * ({@code AvisosDeSubasta}).
 */
@Service
public class SeguimientoService {

    private final SubastaRepository subastas;
    private final SeguimientoRepository seguimientos;
    private final Clock clock;

    public SeguimientoService(SubastaRepository subastas, SeguimientoRepository seguimientos, Clock clock) {
        this.subastas = Objects.requireNonNull(subastas);
        this.seguimientos = Objects.requireNonNull(seguimientos);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Idempotente. Solo subastas activas: seguir una que ya termino no avisaria de nada. */
    @Transactional
    public void seguir(UUID subastaId, UUID jugadorId) {
        Subasta subasta = subastas.findById(subastaId).orElseThrow(() -> new SubastaNoEncontradaException(subastaId));
        if (!subasta.estaActiva()) {
            throw new OperacionRechazadaException(OperacionRechazadaException.Motivo.SUBASTA_NO_ACTIVA,
                    "La subasta ya termino: no hay cambios que seguir");
        }
        seguimientos.seguir(subastaId, jugadorId, clock.instant());
    }

    /** Idempotente; tambien para subastas ya terminadas, que es cuando mas se limpia la lista. */
    @Transactional
    public void dejarDeSeguir(UUID subastaId, UUID jugadorId) {
        if (!subastas.existsById(subastaId)) {
            throw new SubastaNoEncontradaException(subastaId);
        }
        seguimientos.dejarDeSeguir(subastaId, jugadorId);
    }

    /** Las activas primero, por la que cierra antes; despues las terminadas, la mas reciente primero. */
    @Transactional(readOnly = true)
    public List<PanelDtos.SubastaSeguida> lista(UUID jugadorId) {
        List<Seguimiento> suyos = seguimientos.findByJugadorIdOrderByCreadoEnDesc(jugadorId);
        if (suyos.isEmpty()) {
            return List.of();
        }
        Map<UUID, Subasta> porId = subastas.findAllById(suyos.stream().map(Seguimiento::getSubastaId).toList())
                .stream().collect(Collectors.toMap(Subasta::getId, Function.identity()));
        Comparator<PanelDtos.SubastaSeguida> orden = (a, b) -> {
            boolean aActiva = "ACTIVA".equals(a.estado());
            boolean bActiva = "ACTIVA".equals(b.estado());
            if (aActiva != bActiva) {
                return aActiva ? -1 : 1;
            }
            return aActiva ? a.fechaFin().compareTo(b.fechaFin()) : b.fechaFin().compareTo(a.fechaFin());
        };
        return suyos.stream()
                .filter(seguimiento -> porId.containsKey(seguimiento.getSubastaId()))
                .map(seguimiento -> PanelDtos.SubastaSeguida.de(porId.get(seguimiento.getSubastaId()), seguimiento))
                .sorted(orden)
                .toList();
    }
}
