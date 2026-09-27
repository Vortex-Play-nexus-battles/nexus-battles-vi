package nexus.inventario.persistencia;

import java.time.Instant;
import java.util.Optional;
import nexus.inventario.dominio.ClaveDeEntregaOcupadaException;
import nexus.inventario.dominio.Entrega;
import nexus.inventario.dominio.EstadoEntrega;
import nexus.inventario.dominio.FalloPersistenciaInventarioException;
import nexus.inventario.dominio.RepositorioDeEntregas;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/** {@link RepositorioDeEntregas} sobre MongoDB — B4. */
@Repository
public class RepositorioEntregasMongo implements RepositorioDeEntregas {

    private final MongoOperations mongo;

    public RepositorioEntregasMongo(MongoOperations mongo) {
        this.mongo = mongo;
    }

    @Override
    public Optional<Entrega> buscarPorClave(String clave) {
        try {
            return Optional.ofNullable(
                            mongo.findOne(Query.query(Criteria.where("clave").is(clave)), EntregaDocumento.class))
                    .map(EntregaDocumento::aDominio);
        } catch (DataAccessException error) {
            throw new FalloPersistenciaInventarioException(error);
        }
    }

    @Override
    public void registrar(Entrega entrega) {
        try {
            mongo.insert(EntregaDocumento.de(entrega));
        } catch (DuplicateKeyException claveOcupada) {
            throw new ClaveDeEntregaOcupadaException(claveOcupada);
        } catch (DataAccessException error) {
            throw new FalloPersistenciaInventarioException(error);
        }
    }

    @Override
    public void completar(String entregaId, Instant entregadaEn) {
        try {
            mongo.updateFirst(
                    Query.query(Criteria.where("_id").is(entregaId).and("estado").is(EstadoEntrega.PENDIENTE)),
                    new Update().set("estado", EstadoEntrega.COMPLETADA).set("entregadaEn", entregadaEn),
                    EntregaDocumento.class);
        } catch (DataAccessException error) {
            throw new FalloPersistenciaInventarioException(error);
        }
    }
}
