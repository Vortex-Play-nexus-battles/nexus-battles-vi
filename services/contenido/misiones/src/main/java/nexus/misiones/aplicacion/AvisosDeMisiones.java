package nexus.misiones.aplicacion;

import java.time.Instant;

/**
 * La bandeja de notificaciones del jugador ({@code POST /internal/notifications},
 * notificaciones.yaml 1.2.0): el aviso queda en su bandeja y llega en tiempo
 * real a sus sesiones abiertas (HU-NOT-006). Lo usa misiones para RF-NOT-004.
 *
 * <p>Idempotente por {@code id}: el mismo id no crea un segundo aviso (el
 * servicio responde 409, que aqui cuenta como entregado). Por eso la
 * liquidacion puede reintentar un paso de avisos sin duplicar ninguno.
 */
public interface AvisosDeMisiones {

    /** El {@code tipo} de los avisos de misiones: el origen del evento (websocket/notificaciones.yaml). */
    String TIPO = "MISION";

    /**
     * @param jugadorUid destinatario
     * @param id         identificador del evento: el mismo en cada reintento
     * @throws RechazoDelServicio si la bandeja lo rechaza de forma definitiva
     */
    void avisar(String jugadorUid, String id, String titulo, String cuerpo, Instant creadaEn);
}
