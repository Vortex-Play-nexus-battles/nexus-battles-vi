package com.nexusbattles.ms_finanzas.pagos.pasarela;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Simula la comunicación con un procesador de pagos externo (HU-PAG-001,
 * RF-PAG-006). No hay un banco real detrás: esta clase decide el resultado
 * de forma controlada para poder probar los caminos que pide el criterio
 * de aceptación (aprobado, rechazado, y fallo transitorio para el
 * mecanismo de reintento de PagoService).
 *
 * Reglas de simulación (temporales, documentar en
 * DECISIONES-PENDIENTES-DEL-PO.md si el PO no las confirma):
 *   - Si el monto es negativo o cero: se rechaza (validación básica).
 *   - Si el refId contiene "FALLO-PASARELA" (convención de prueba): se
 *     lanza PasarelaNoDisponibleException, simulando que la pasarela no
 *     respondió a tiempo. Así los tests pueden forzar este camino sin
 *     depender del azar.
 *   - En cualquier otro caso: se aprueba.
 */
@Component
public class PasarelaSimuladaClient {

    public RespuestaPasarela procesar(SolicitudPago solicitud) {
        if (solicitud.refId() != null && solicitud.refId().contains("FALLO-PASARELA")) {
            throw new PasarelaNoDisponibleException(
                "La pasarela simulada no respondió a tiempo para refId=" + solicitud.refId()
            );
        }

        if (solicitud.monto() == null || solicitud.monto().compareTo(BigDecimal.ZERO) <= 0) {
            return new RespuestaPasarela(
                ResultadoPago.RECHAZADA,
                null,
                "Monto inválido"
            );
        }

        String referenciaExterna = "PASARELA-SIM-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        return new RespuestaPasarela(
            ResultadoPago.APROBADA,
            referenciaExterna,
            null
        );
    }

    public record SolicitudPago(
        String uidUsuario,
        BigDecimal monto,
        String moneda,
        String concepto,
        String refId
    ) {}

    public record RespuestaPasarela(
        ResultadoPago resultado,
        String referenciaExterna,
        String motivoRechazo
    ) {}

    public enum ResultadoPago {
        APROBADA, RECHAZADA, INDETERMINADA
    }

    /**
     * Error transitorio: la pasarela no respondió a tiempo, o no está
     * disponible. PagoService debe reintentar ante esta excepción; si los
     * reintentos se agotan, la transacción se registra como INDETERMINADA.
     */
    public static class PasarelaNoDisponibleException extends RuntimeException {
        public PasarelaNoDisponibleException(String message) {
            super(message);
        }
    }
}
