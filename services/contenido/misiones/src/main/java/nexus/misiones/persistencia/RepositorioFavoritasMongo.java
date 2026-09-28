package nexus.misiones.persistencia;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import nexus.misiones.dominio.RepositorioDeFavoritas;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/** {@link RepositorioDeFavoritas} sobre MongoDB; el identificador compuesto las hace idempotentes. */
public class RepositorioFavoritasMongo implements RepositorioDeFavoritas {

    private final MongoOperations mongo;

    public RepositorioFavoritasMongo(MongoOperations mongo) {
        this.mongo = mongo;
    }

    @Override
    public Set<String> delJugador(String jugadorUid) {
        return mongo.find(new Query(Criteria.where("jugadorUid").is(jugadorUid)), FavoritaDocumento.class).stream()
                .map(FavoritaDocumento::misionId)
                .collect(Collectors.toSet());
    }

    @Override
    public void marcar(String jugadorUid, String misionId, Instant ahora) {
        mongo.save(new FavoritaDocumento(FavoritaDocumento.idDe(jugadorUid, misionId), jugadorUid, misionId, ahora));
    }

    @Override
    public void desmarcar(String jugadorUid, String misionId) {
        mongo.remove(new Query(Criteria.where("_id").is(FavoritaDocumento.idDe(jugadorUid, misionId))),
                FavoritaDocumento.class);
    }
}
