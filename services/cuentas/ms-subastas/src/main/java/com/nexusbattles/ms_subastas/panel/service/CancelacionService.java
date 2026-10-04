package com.nexusbattles.ms_subastas.panel.service;

import com.nexusbattles.ms_subastas.notificaciones.AvisosDeSubasta;
import com.nexusbattles.ms_subastas.pujas.repository.PujaAutomaticaRepository;
import com.nexusbattles.ms_subastas.pujas.repository.PujaRepository;
import com.nexusbattles.ms_subastas.pujas.service.SubastaNoEncontradaException;
import com.nexusbattles.ms_subastas.reglas.ReglasDelDocumento;
import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.port.FinanzasPublicacionClient;
import com.nexusbattles.ms_subastas.subastas.port.FinanzasPublicacionClientException;
import com.nexusbattles.ms_subastas.subastas.port.FinanzasPublicacionClientHttp;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClient;
import com.nexusbattles.ms_subastas.subastas.realtime.SubastaActualizadaEvent;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import com.nexusbattles.ms_subastas.subastas.service.PublicacionSubastaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Cancelar una subasta propia (7.7.10 del documento del curso, B8):
 *
 * <ul>
 *   <li>«Posible solo si no hay pujas registradas»;</li>
 *   <li>«Penalizacion del 50% de la comision pagada si se cancela»;</li>
 *   <li>«No permitida en las ultimas 6 horas de la subasta».</li>
 * </ul>
 *
 * <p><b>Con el candado de la subasta tomado</b>, el mismo que usan las pujas:
 * una puja y una cancelacion simultaneas se serializan, y gana una sola. Si la
 * puja entra primero, la cancelacion ve la puja y se rechaza; si la cancelacion
 * entra primero, la puja encuentra la subasta cancelada.
 *
 * <p><b>Dos efectos externos, y su compensacion.</b> La penalizacion se debita
 * en ms-finanzas y el producto se libera en inventario, las dos por HTTP. Si
 * algo falla despues —el otro servicio, o el propio commit—, la transaccion
 * se deshace y se compensa lo ya hecho: se devuelve la penalizacion y se vuelve
 * a bloquear el producto para esta subasta. Mismo patron que la publicacion.
 *
 * <p>La penalizacion es idempotente por subasta en ms-finanzas
 * ({@code refId sub-cancelacion-{id}}): repetir la cancelacion no cobra dos veces.
 * Y una penalizacion devuelta (cancelacion fallida) no se reutiliza: la
 * siguiente se cobra con otra generacion del refId (G7, ver
 * {@code FinanzasPublicacionClientHttp#debitarPenalizacionCancelacion}).
 */
@Service
public class CancelacionService {

    private static final Logger log = LoggerFactory.getLogger(CancelacionService.class);

    private final SubastaRepository subastas;
    private final PujaRepository pujas;
    private final PujaAutomaticaRepository automaticas;
    private final FinanzasPublicacionClient finanzas;
    private final InventarioClient inventario;
    private final AvisosDeSubasta avisos;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    public CancelacionService(SubastaRepository subastas, PujaRepository pujas, PujaAutomaticaRepository automaticas,
                              FinanzasPublicacionClient finanzas, InventarioClient inventario, AvisosDeSubasta avisos,
                              ApplicationEventPublisher eventos, Clock clock) {
        this.subastas = Objects.requireNonNull(subastas);
        this.pujas = Objects.requireNonNull(pujas);
        this.automaticas = Objects.requireNonNull(automaticas);
        this.finanzas = Objects.requireNonNull(finanzas);
        this.inventario = Objects.requireNonNull(inventario);
        this.avisos = Objects.requireNonNull(avisos);
        this.eventos = Objects.requireNonNull(eventos);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public Subasta cancelar(UUID subastaId, UUID vendedorId) {
        Subasta subasta = subastas.findByIdParaActualizar(subastaId)
                .orElseThrow(() -> new SubastaNoEncontradaException(subastaId));

        if (!subasta.esVendedor(vendedorId)) {
            throw new OperacionRechazadaException(OperacionRechazadaException.Motivo.NO_ES_EL_VENDEDOR,
                    "Solo el vendedor puede cancelar su subasta");
        }
        // Idempotente: repetir la cancelacion (una respuesta perdida) devuelve la misma.
        if (subasta.getEstado() == EstadoSubasta.CANCELADA) {
            return subasta;
        }
        if (!subasta.estaActiva()) {
            throw new OperacionRechazadaException(OperacionRechazadaException.Motivo.SUBASTA_NO_ACTIVA,
                    "La subasta ya termino (" + subasta.getEstado() + ")");
        }
        if (subasta.tieneOfertas() || subasta.getCantidadPujas() > 0 || pujas.existsBySubastaId(subastaId)) {
            throw new OperacionRechazadaException(OperacionRechazadaException.Motivo.CANCELACION_CON_PUJAS,
                    "La subasta ya tiene pujas registradas: no se puede cancelar");
        }
        Instant ahora = clock.instant();
        Instant limite = subasta.getFechaFin().minus(ReglasDelDocumento.CANCELACION_PROHIBIDA_ULTIMAS);
        if (!ahora.isBefore(limite)) {
            throw new OperacionRechazadaException(OperacionRechazadaException.Motivo.CANCELACION_FUERA_DE_PLAZO,
                    "No se puede cancelar en las ultimas " + ReglasDelDocumento.CANCELACION_PROHIBIDA_ULTIMAS.toHours()
                            + " horas de la subasta");
        }

        BigDecimal penalizacion = ReglasDelDocumento.penalizacionDeCancelacion(subasta.getComisionCobrada());
        Efectos efectos = new Efectos(subasta);
        boolean registrada = registrar(efectos);
        try {
            if (penalizacion.signum() > 0) {
                cobrarPenalizacion(vendedorId, penalizacion, subastaId);
                efectos.debitado = true;
            }
            if (efectos.tieneInventario()) {
                inventario.liberarReserva(subasta.getElementoInventarioId(), subastaId, "cancelacion-" + subastaId);
                efectos.liberado = true;
            }

            subasta.setPenalizacionCobrada(penalizacion);
            subasta.cerrar(EstadoSubasta.CANCELADA, ahora);
            subastas.save(subasta);

            // Los avisos leen quien tenia puja automatica ANTES de desactivarlas.
            avisos.cancelada(subasta);
            automaticas.desactivarTodas(subastaId);
            eventos.publishEvent(new SubastaActualizadaEvent(this, subasta));
            return subasta;
        } catch (RuntimeException | Error fallo) {
            if (!registrada) {
                efectos.compensar();
            }
            throw fallo;
        }
    }

    private void cobrarPenalizacion(UUID vendedorId, BigDecimal penalizacion, UUID subastaId) {
        try {
            finanzas.debitarPenalizacionCancelacion(vendedorId, penalizacion, subastaId);
        } catch (FinanzasPublicacionClientException noDisponible) {
            throw noDisponible;
        } catch (PublicacionSubastaException rechazo) {
            if (FinanzasPublicacionClientHttp.SALDO_INSUFICIENTE.equals(rechazo.getCodigo())) {
                throw new OperacionRechazadaException(OperacionRechazadaException.Motivo.SALDO_INSUFICIENTE,
                        "No tienes creditos para la penalizacion de " + penalizacion.toPlainString()
                                + " (" + ReglasDelDocumento.PENALIZACION_CANCELACION_PORCENTAJE + " % de la comision)");
            }
            throw rechazo;
        }
    }

    /** Con transaccion activa, compensa al deshacerse; sin ella, lo hace quien atrapa el fallo. */
    private static boolean registrar(TransactionSynchronization compensacion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(compensacion);
            return true;
        }
        return false;
    }

    /** Lo que ya se hizo fuera de la base de datos, para deshacerlo si la cancelacion no se confirma. */
    private final class Efectos implements TransactionSynchronization {
        private final Subasta subasta;
        private boolean debitado;
        private boolean liberado;

        private Efectos(Subasta subasta) {
            this.subasta = subasta;
        }

        boolean tieneInventario() {
            return subasta.getElementoInventarioId() != null && !subasta.getElementoInventarioId().isBlank();
        }

        @Override
        public void afterCompletion(int status) {
            if (status == STATUS_ROLLED_BACK) {
                compensar();
            } else if (status == STATUS_UNKNOWN) {
                log.error("Resultado de commit desconocido al cancelar la subasta {}; requiere conciliacion",
                        subasta.getId());
            }
        }

        void compensar() {
            if (debitado) {
                try {
                    finanzas.compensarPenalizacionCancelacion(subasta.getId(), "cancelacion-fallida");
                } catch (RuntimeException fallo) {
                    log.error("No se pudo devolver la penalizacion de la subasta {}; requiere conciliacion",
                            subasta.getId(), fallo);
                }
            }
            if (liberado) {
                try {
                    inventario.reservar(subasta.getElementoInventarioId(), subasta.getVendedorId(), subasta.getId(),
                            "recancelacion-" + subasta.getId());
                } catch (RuntimeException fallo) {
                    log.error("No se pudo volver a bloquear el elemento {} de la subasta {}; requiere conciliacion",
                            subasta.getElementoInventarioId(), subasta.getId(), fallo);
                }
            }
        }
    }
}
