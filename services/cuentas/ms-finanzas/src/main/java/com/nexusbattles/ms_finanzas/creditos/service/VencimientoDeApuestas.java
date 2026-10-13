package com.nexusbattles.ms_finanzas.creditos.service;

import com.nexusbattles.ms_finanzas.creditos.domain.ReservaCredito;
import com.nexusbattles.ms_finanzas.creditos.repository.ReservaCreditoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Devuelve los creditos de las apuestas de sala que se quedaron reservados mas
 * alla de su vencimiento — auditoria de DEV del 30-sep.
 *
 * <p><b>El defecto.</b> Un jugador tenia 500 creditos «apartados en apuestas»
 * con una reserva ACTIVA del 28 de septiembre, sin sala visible y sin forma de
 * recuperarlos: {@code /creditos/**} es solo de servicio, asi que el jugador no
 * puede liberarla, y salas-partidas solo la libera al cancelar, al abandonar o
 * al liquidar. Si nada de eso llega a pasar —la sala se queda abandonada, la
 * respuesta de la reserva se pierde por la red, la liberacion falla y solo se
 * anota en la bitacora— la reserva vive para siempre.
 *
 * <p><b>El arreglo no inventa un plazo.</b> Toda reserva nace con
 * {@code expira_en} = creacion + 72 h desde el principio; lo que faltaba es que
 * alguien lo leyera. Esta tarea libera, con la misma operacion idempotente que
 * usa salas-partidas ({@link CreditoService#liberar}), las reservas ACTIVAS del
 * concepto {@code apuesta-sala} ya vencidas. Solo ese concepto: las reservas de
 * subastas y torneos tienen su propio cierre y no se tocan aqui. salas-partidas
 * cierra antes las salas y partidas abandonadas con el mismo horizonte (D-39),
 * asi que una apuesta que se libera aqui ya no tiene partida que liquidar.
 *
 * <p>Una reserva que falla al liberarse no detiene el lote: se anota y se
 * reintenta en la siguiente vuelta.
 */
@Component
public class VencimientoDeApuestas {

    private static final Logger BITACORA = LoggerFactory.getLogger(VencimientoDeApuestas.class);

    /** El concepto con que salas-partidas reserva la apuesta de una sala. */
    public static final String CONCEPTO_APUESTA = "apuesta-sala";

    /** Cuantas se atienden por vuelta; el resto esperan a la siguiente. */
    static final int LOTE = 100;

    private final ReservaCreditoRepository reservas;
    private final CreditoService creditos;
    private final Clock reloj;
    private final boolean activo;

    public VencimientoDeApuestas(ReservaCreditoRepository reservas, CreditoService creditos, Clock reloj,
                                 @Value("${finanzas.apuestas.vencer-reservas:true}") boolean activo) {
        this.reservas = reservas;
        this.creditos = creditos;
        this.reloj = reloj;
        this.activo = activo;
    }

    @Scheduled(fixedDelayString = "${finanzas.apuestas.vencimiento-ms:600000}",
               initialDelayString = "${finanzas.apuestas.vencimiento-ms:600000}")
    void programado() {
        if (activo) {
            liberarVencidas();
        }
    }

    /** @return cuantas reservas vencidas se liberaron en esta vuelta */
    public int liberarVencidas() {
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        List<ReservaCredito> vencidas = reservas.findByEstadoAndTipoOperacionAndConceptoAndExpiraEnBefore(
                ReservaCredito.EstadoReserva.ACTIVA, ReservaCredito.TipoOperacion.RESERVA, CONCEPTO_APUESTA,
                ahora, PageRequest.of(0, LOTE));
        int liberadas = 0;
        for (ReservaCredito reserva : vencidas) {
            try {
                creditos.liberar(reserva.getId());
                liberadas++;
                BITACORA.info("Apuesta vencida devuelta: reserva={} jugador={} monto={} referencia={} expiraba={}",
                        reserva.getId(), reserva.getJugadorUid(), reserva.getMonto(), reserva.getReferenciaId(),
                        reserva.getExpiraEn());
            } catch (RuntimeException fallo) {
                BITACORA.warn("No se pudo devolver la apuesta vencida {} de {}: {}; se reintenta en la proxima vuelta",
                        reserva.getId(), reserva.getJugadorUid(), fallo.getMessage());
            }
        }
        return liberadas;
    }
}
