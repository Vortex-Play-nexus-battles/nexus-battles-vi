package com.nexusbattles.ms_subastas.notificaciones;

/**
 * {@code POST /api/v1/correos/subasta} del servicio de correo
 * ({@code contracts/openapi/correo.yaml} 1.4.0), con la credencial de
 * servicio de ms-subastas: correo solo atiende a {@code rol=SERVICIO}.
 *
 * <p>Siempre con {@code Idempotency-Key}, la del aviso del outbox (estable por
 * evento): si el correo llego pero la respuesta se perdio, el reintento lleva
 * la misma clave y correo no encola otro. Un 202 significa «guardado en la
 * cola persistente de correo», no «entregado en la bandeja».
 */
public interface CorreoSubastaClient {

    /**
     * @throws CorreoRechazadoException   si correo rechazo el cuerpo (400): reintentar no lo arregla
     * @throws CorreoNoDisponibleException si no respondio o no acepto la credencial: se reintenta
     */
    void enviar(String claveDeIdempotencia, CorreoSubasta correo);

    /**
     * {@code CorreoSubastaRequest} del contrato.
     *
     * @param debeEnviarCorreo falso si el jugador pidio avisos solo en la
     *                         aplicacion. ms-subastas no tiene todavia esa
     *                         preferencia (decision del PO), asi que siempre va true
     */
    record CorreoSubasta(String email, String apodo, String asunto, String mensaje, boolean debeEnviarCorreo) {
    }
}
