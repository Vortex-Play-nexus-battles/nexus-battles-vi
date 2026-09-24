package com.nexusbattles.ms_identidad.onboarding.service;

import com.nexusbattles.ms_identidad.onboarding.auditoria.AuditoriaDeCuenta;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Cuando el registro se confirma: auditoria del alta y arranque del
 * bootstrap.
 *
 * <p>AFTER_COMMIT y no dentro del registro: si el registro se deshace (correo
 * repetido que se colo, fallo al guardar el avatar), no debe quedar ni un
 * credito ni un heroe de una cuenta que no existe. Y el bootstrap no alarga
 * la transaccion del registro con llamadas de red.
 */
@Component
public class AlRegistrarJugador {

    private final AuditoriaDeCuenta auditoria;
    private final LanzadorOnboarding lanzador;

    public AlRegistrarJugador(AuditoriaDeCuenta auditoria, LanzadorOnboarding lanzador) {
        this.auditoria = auditoria;
        this.lanzador = lanzador;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alConfirmarse(JugadorRegistrado evento) {
        auditoria.registro(evento.uid(), evento.apodo(), evento.ip());
        lanzador.lanzar(evento.uid());
    }
}
