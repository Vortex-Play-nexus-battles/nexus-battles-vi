package nexus.misiones.dominio;

import java.time.Instant;
import java.util.Set;

/** Misiones favoritas de cada jugador (7.8.9, «Agregar a favoritos»; HU-MIS-017). */
public interface RepositorioDeFavoritas {

    Set<String> delJugador(String jugadorUid);

    /** Idempotente: marcar dos veces deja una sola favorita. */
    void marcar(String jugadorUid, String misionId, Instant ahora);

    /** Idempotente: desmarcar una que no lo estaba no hace nada. */
    void desmarcar(String jugadorUid, String misionId);
}
