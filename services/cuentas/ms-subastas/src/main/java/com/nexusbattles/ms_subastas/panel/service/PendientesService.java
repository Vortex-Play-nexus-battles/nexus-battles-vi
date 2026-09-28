package com.nexusbattles.ms_subastas.panel.service;

import com.nexusbattles.ms_subastas.notificaciones.AvisosDeSubasta;
import com.nexusbattles.ms_subastas.panel.dto.PanelDtos;
import com.nexusbattles.ms_subastas.panel.model.EstadoPendiente;
import com.nexusbattles.ms_subastas.panel.model.PendienteDeRecoger;
import com.nexusbattles.ms_subastas.panel.repository.PendienteDeRecogerRepository;
import com.nexusbattles.ms_subastas.reglas.FuenteDeReglas;
import com.nexusbattles.ms_subastas.reglas.PoliticaAlVencer;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClient;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * «Productos pendientes de recoger» (7.7.9 del documento del curso, B8).
 *
 * <p>Al cerrarse con ganador, la subasta transfiere el producto al ganador y
 * cobra, pero conserva el bloqueo: el producto esta en su inventario como no
 * disponible. Recogerlo es soltar ese bloqueo. «Recoger todo» hace lo mismo
 * con cada pendiente por separado —uno que falla no retiene a los demas— y
 * al vencer los 7 dias se aplica la politica que diga el parametro
 * {@code subastas.pendientes.al-vencer} (decision del PO; provisional
 * ENTREGAR).
 */
@Service
public class PendientesService {

    private static final Logger log = LoggerFactory.getLogger(PendientesService.class);

    private final PendienteDeRecogerRepository pendientes;
    private final SubastaRepository subastas;
    private final InventarioClient inventario;
    private final AvisosDeSubasta avisos;
    private final FuenteDeReglas reglas;
    private final Clock clock;
    private final TransactionTemplate transaccion;

    public PendientesService(PendienteDeRecogerRepository pendientes, SubastaRepository subastas,
                             InventarioClient inventario, AvisosDeSubasta avisos, FuenteDeReglas reglas, Clock clock,
                             PlatformTransactionManager transacciones) {
        this.pendientes = Objects.requireNonNull(pendientes);
        this.subastas = Objects.requireNonNull(subastas);
        this.inventario = Objects.requireNonNull(inventario);
        this.avisos = Objects.requireNonNull(avisos);
        this.reglas = Objects.requireNonNull(reglas);
        this.clock = Objects.requireNonNull(clock);
        this.transaccion = new TransactionTemplate(transacciones);
    }

    /** Los pendientes del jugador, el que vence antes primero. */
    @Transactional(readOnly = true)
    public List<PanelDtos.Pendiente> pendientesDe(UUID jugadorId) {
        List<PendienteDeRecoger> suyos = pendientes.findByGanadorIdAndEstadoOrderByVenceEnAsc(jugadorId,
                EstadoPendiente.PENDIENTE);
        if (suyos.isEmpty()) {
            return List.of();
        }
        Map<UUID, Subasta> porId = subastas.findAllById(suyos.stream().map(PendienteDeRecoger::getSubastaId).toList())
                .stream().collect(Collectors.toMap(Subasta::getId, Function.identity()));
        return suyos.stream().map(p -> PanelDtos.Pendiente.de(p, porId.get(p.getSubastaId()))).toList();
    }

    /** Recoge uno. Idempotente: recoger lo ya recogido devuelve su estado. */
    @Transactional
    public PanelDtos.Pendiente recoger(UUID subastaId, UUID jugadorId) {
        return recogerEnLaTransaccion(subastaId, jugadorId);
    }

    /**
     * «Recoger todo»: cada pendiente en su propia transaccion, para que un
     * fallo de inventario con uno no deshaga los que ya se recogieron.
     */
    public PanelDtos.ResultadoDeRecogida recogerTodo(UUID jugadorId) {
        List<PanelDtos.Pendiente> recogidos = new ArrayList<>();
        List<PanelDtos.ResultadoDeRecogida.Fallo> fallidos = new ArrayList<>();
        List<UUID> ids = pendientes.findByGanadorIdAndEstadoOrderByVenceEnAsc(jugadorId, EstadoPendiente.PENDIENTE)
                .stream().map(PendienteDeRecoger::getSubastaId).toList();
        for (UUID subastaId : ids) {
            try {
                recogidos.add(transaccion.execute(estado -> recogerEnLaTransaccion(subastaId, jugadorId)));
            } catch (RuntimeException fallo) {
                log.warn("No se pudo recoger el pendiente de la subasta {}: {}", subastaId, fallo.getMessage());
                fallidos.add(new PanelDtos.ResultadoDeRecogida.Fallo(subastaId,
                        "No se pudo recoger ahora; sigue pendiente. Vuelve a intentarlo en un momento."));
            }
        }
        return new PanelDtos.ResultadoDeRecogida(recogidos, fallidos);
    }

    /**
     * Aplica la politica del parametro a un pendiente que vencio. Lo llama
     * {@link VencimientoDePendientesJob}, uno por transaccion.
     *
     * @return si lo resolvio (falso: ya no estaba pendiente)
     */
    @Transactional
    public boolean resolverVencido(UUID subastaId) {
        PendienteDeRecoger pendiente = pendientes.findByIdParaActualizar(subastaId).orElse(null);
        if (pendiente == null || !pendiente.estaPendiente() || pendiente.getVenceEn().isAfter(clock.instant())) {
            return false;
        }
        Subasta subasta = subastas.findById(subastaId).orElseThrow();
        PoliticaAlVencer politica = reglas.vigentes().alVencerPendientes();
        if (politica == PoliticaAlVencer.DEVOLVER_AL_VENDEDOR) {
            // Vuelve al vendedor conservando el bloqueo (asi lo exige
            // inventario para mover un elemento de una subasta) y despues se
            // suelta: el vendedor lo recibe disponible.
            inventario.transferirProducto(pendiente.getElementoInventarioId(), subasta.getVendedorId(), subastaId,
                    "devolver-" + subastaId);
            inventario.liberarReserva(pendiente.getElementoInventarioId(), subastaId, "devuelto-" + subastaId);
            pendiente.resolver(EstadoPendiente.DEVUELTO_AL_VENDEDOR, clock.instant());
        } else {
            inventario.liberarReserva(pendiente.getElementoInventarioId(), subastaId, "vencido-" + subastaId);
            pendiente.resolver(EstadoPendiente.ENTREGADO_AL_VENCER, clock.instant());
        }
        pendientes.save(pendiente);
        avisos.pendienteVencido(subasta, pendiente, politica);
        return true;
    }

    private PanelDtos.Pendiente recogerEnLaTransaccion(UUID subastaId, UUID jugadorId) {
        PendienteDeRecoger pendiente = pendientes.findByIdParaActualizar(subastaId)
                .filter(p -> p.getGanadorId().equals(jugadorId))
                .orElseThrow(() -> new OperacionRechazadaException(
                        OperacionRechazadaException.Motivo.PENDIENTE_NO_ENCONTRADO,
                        "No tienes ningun producto pendiente de recoger de esa subasta"));
        Subasta subasta = subastas.findById(subastaId).orElse(null);
        if (pendiente.getEstado() == EstadoPendiente.RECOGIDO) {
            return PanelDtos.Pendiente.de(pendiente, subasta);
        }
        if (!pendiente.estaPendiente()) {
            throw new OperacionRechazadaException(OperacionRechazadaException.Motivo.PENDIENTE_YA_RESUELTO,
                    "El plazo para recogerlo ya vencio (" + pendiente.getEstado() + ")");
        }
        inventario.liberarReserva(pendiente.getElementoInventarioId(), subastaId, "recoger-" + subastaId);
        pendiente.resolver(EstadoPendiente.RECOGIDO, clock.instant());
        pendientes.save(pendiente);
        if (subasta != null) {
            avisos.recogido(subasta, pendiente);
        }
        return PanelDtos.Pendiente.de(pendiente, subasta);
    }
}
