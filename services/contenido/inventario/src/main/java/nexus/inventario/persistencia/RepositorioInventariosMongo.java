package nexus.inventario.persistencia;

import java.util.List;
import java.util.Optional;
import nexus.inventario.dominio.ConflictoDeEscrituraException;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.FalloPersistenciaInventarioException;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.RepositorioDeInventarios;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioInventariosMongo implements RepositorioDeInventarios {

    private final RepositorioInventariosSpringData documentos;
    private final MongoOperations mongo;

    public RepositorioInventariosMongo(
            RepositorioInventariosSpringData documentos, MongoOperations mongo) {
        this.documentos = documentos;
        this.mongo = mongo;
    }

    /**
     * Guarda el inventario entero, solo si nadie lo escribio desde que se leyo
     * (B4, {@code @Version}).
     *
     * @throws ConflictoDeEscrituraException si otra escritura llego antes, o si
     *         dos inventarios nuevos del mismo jugador chocaron en el indice
     *         unico de {@code propietarioId}: releer y reintentar lo resuelve
     * @throws FalloPersistenciaInventarioException si la base no respondio
     */
    @Override
    public Inventario guardar(Inventario inventario) {
        try {
            return documentos.save(conVersion(InventarioDocumento.de(inventario))).aDominio();
        } catch (OptimisticLockingFailureException | DuplicateKeyException conflicto) {
            throw new ConflictoDeEscrituraException(conflicto);
        } catch (DataAccessException error) {
            throw new FalloPersistenciaInventarioException(error);
        }
    }

    /**
     * Un documento anterior a B4 no tiene version, y para Spring Data un
     * {@code @Version} nulo es un documento nuevo: su primer guardado seria un
     * insert que chocaria con su propio {@code _id}. Antes de ese primer
     * guardado se le pone la version 0 en la base (solo si sigue sin version) y
     * en el documento, y el guardado sigue versionado como cualquier otro.
     */
    private InventarioDocumento conVersion(InventarioDocumento documento) {
        if (documento.id() == null || documento.version() != null) {
            return documento;
        }
        mongo.updateFirst(
                Query.query(Criteria.where("_id").is(documento.id()).and("version").is(null)),
                new Update().set("version", 0L),
                mongo.getCollectionName(InventarioDocumento.class));
        return documento.withVersion(0L);
    }

    @Override
    public Optional<Inventario> buscarPorPropietario(String propietarioId) {
        return documentos.findByPropietarioId(propietarioId).map(InventarioDocumento::aDominio);
    }

    @Override
    public Optional<Inventario> buscarPorElementoId(String elementoId) {
        return documentos.findByElementosId(elementoId).map(InventarioDocumento::aDominio);
    }

    @Override
    public List<Inventario> buscarTodosPorElementoId(String elementoId) {
        return documentos.findAllByElementosId(elementoId).stream()
                .map(InventarioDocumento::aDominio)
                .toList();
    }

    @Override
    public List<ElementoInventario> buscarElementos(String propietarioId, String criterio) {
        TextCriteria texto = TextCriteria.forDefaultLanguage()
                .matching(criterio)
                .caseSensitive(false)
                .diacriticSensitive(false);
        TextQuery consulta = TextQuery.queryText(texto);
        consulta.addCriteria(Criteria.where("propietarioId").is(propietarioId));

        InventarioDocumento inventario = mongo.findOne(consulta, InventarioDocumento.class);
        if (inventario == null) {
            return List.of();
        }
        return inventario.aDominio().elementos().stream()
                .filter(elemento -> CoincidenciaDeElemento.conTexto(elemento, criterio))
                .toList();
    }
}
