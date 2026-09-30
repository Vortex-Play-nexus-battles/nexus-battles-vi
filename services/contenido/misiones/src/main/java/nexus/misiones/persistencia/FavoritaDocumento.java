package nexus.misiones.persistencia;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Una mision favorita de un jugador (coleccion {@code favoritas}), id {@code jugador:mision}. */
@Document(collection = "favoritas")
record FavoritaDocumento(@Id String id, @Indexed String jugadorUid, String misionId, Instant marcadaEn) {

    static String idDe(String jugadorUid, String misionId) {
        return jugadorUid + ":" + misionId;
    }
}
