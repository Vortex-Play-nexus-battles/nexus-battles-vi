package com.nexusbattles.ms_subastas.pujas.creditos;

/**
 * ms-finanzas no respondio: conexion caida, tiempo agotado o 5xx.
 *
 * <p>Separada del {@link CreditoClientException} de negocio para que el
 * cortacircuitos y el reintento puedan distinguirlas — resilience4j filtra por
 * clase, no por un campo. Un saldo insuficiente es una respuesta correcta del
 * servicio: reintentarla da exactamente lo mismo y abrir el cortacircuitos por
 * ella dejaria sin creditos a todos los jugadores. Esto, en cambio, si merece
 * reintento y si es un fallo del servicio.
 *
 * <p>Mismo patron que {@code InventarioNoDisponibleException}.
 */
public class CreditoNoDisponibleException extends CreditoClientException {

    public CreditoNoDisponibleException(String mensaje) {
        super(Motivo.SERVICIO_NO_DISPONIBLE, mensaje);
    }

    public CreditoNoDisponibleException(String mensaje, Throwable causa) {
        super(Motivo.SERVICIO_NO_DISPONIBLE, mensaje, causa);
    }
}
