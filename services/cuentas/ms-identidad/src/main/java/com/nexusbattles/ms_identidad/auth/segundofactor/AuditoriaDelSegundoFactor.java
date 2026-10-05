package com.nexusbattles.ms_identidad.auth.segundofactor;

import com.nexusbattles.ms_identidad.onboarding.auditoria.AuditoriaDeCuenta;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Lleva a la auditoria (ms-cumplimiento, fail-open) cada cambio del segundo
 * factor, despues del commit. Nunca el secreto ni un codigo: solo que paso y,
 * al usar un codigo de recuperacion, cuantos quedan.
 */
@Component
public class AuditoriaDelSegundoFactor {

    private final AuditoriaDeCuenta auditoria;

    public AuditoriaDelSegundoFactor(AuditoriaDeCuenta auditoria) {
        this.auditoria = auditoria;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alCambiar(CambioDeSegundoFactor cambio) {
        String valorNuevo = switch (cambio.cambio()) {
            case ACTIVADO -> "totp";
            case DESACTIVADO -> "sin-segundo-factor";
            case RECUPERACION_USADA -> "codigos-restantes=" + (cambio.restantes() == null ? 0 : cambio.restantes());
        };
        auditoria.segundoFactor(cambio.afectado(), cambio.cambio().motivo(), valorNuevo, cambio.ip());
    }
}
