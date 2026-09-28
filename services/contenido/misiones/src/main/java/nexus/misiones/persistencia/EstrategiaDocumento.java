package nexus.misiones.persistencia;

import java.time.Instant;
import java.util.List;
import nexus.misiones.dominio.EstrategiaGuardada;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Una estrategia guardada (coleccion {@code estrategias}). El identificador es
 * {@code jugador:heroe}: una por jugador y heroe, y guardar otra la reemplaza.
 */
@Document(collection = "estrategias")
record EstrategiaDocumento(
        @Id String id,
        String jugadorUid,
        String heroeId,
        String prototipo,
        int nivel,
        List<List<String>> rotaciones,
        Instant actualizadaEn) {

    static String idDe(String jugadorUid, String heroeId) {
        return jugadorUid + ":" + heroeId;
    }

    static EstrategiaDocumento de(EstrategiaGuardada e) {
        return new EstrategiaDocumento(idDe(e.jugadorUid(), e.heroeId()), e.jugadorUid(), e.heroeId(),
                e.prototipo(), e.nivel(), e.rotaciones(), e.actualizadaEn());
    }

    EstrategiaGuardada aDominio() {
        return new EstrategiaGuardada(jugadorUid, heroeId, prototipo, nivel, rotaciones, actualizadaEn);
    }
}
