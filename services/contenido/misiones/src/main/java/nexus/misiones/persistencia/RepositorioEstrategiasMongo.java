package nexus.misiones.persistencia;

import java.util.Optional;
import nexus.misiones.dominio.EstrategiaGuardada;
import nexus.misiones.dominio.RepositorioDeEstrategias;
import org.springframework.data.mongodb.core.MongoOperations;

/** {@link RepositorioDeEstrategias} sobre MongoDB: una por jugador y heroe. */
public class RepositorioEstrategiasMongo implements RepositorioDeEstrategias {

    private final MongoOperations mongo;

    public RepositorioEstrategiasMongo(MongoOperations mongo) {
        this.mongo = mongo;
    }

    @Override
    public Optional<EstrategiaGuardada> buscar(String jugadorUid, String heroeId) {
        return Optional.ofNullable(mongo.findById(EstrategiaDocumento.idDe(jugadorUid, heroeId),
                EstrategiaDocumento.class)).map(EstrategiaDocumento::aDominio);
    }

    @Override
    public EstrategiaGuardada guardar(EstrategiaGuardada estrategia) {
        return mongo.save(EstrategiaDocumento.de(estrategia)).aDominio();
    }
}
