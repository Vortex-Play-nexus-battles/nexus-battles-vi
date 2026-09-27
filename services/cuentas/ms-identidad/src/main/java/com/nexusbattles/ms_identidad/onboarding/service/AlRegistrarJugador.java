package com.nexusbattles.ms_identidad.onboarding.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Cuando se confirma la transaccion que crea el alta: arranque del bootstrap.
 *
 * <p>AFTER_COMMIT y no dentro de la transaccion: si se deshace, no debe
 * quedar ni un credito ni un heroe de un jugador que no existe. Y el
 * bootstrap no alarga la transaccion con llamadas de red.
 *
 * <p>B1: esa transaccion es la confirmacion del correo, no el registro. La
 * auditoria del alta de la cuenta sale al registrarse ({@code CuentaRegistrada})
 * y la de la verificacion al confirmar ({@code CorreoVerificado}); aqui solo
 * se lanza el bootstrap.
 */
@Component
public class AlRegistrarJugador {

    private final LanzadorOnboarding lanzador;

    public AlRegistrarJugador(LanzadorOnboarding lanzador) {
        this.lanzador = lanzador;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alConfirmarse(JugadorRegistrado evento) {
        lanzador.lanzar(evento.uid());
    }
}
