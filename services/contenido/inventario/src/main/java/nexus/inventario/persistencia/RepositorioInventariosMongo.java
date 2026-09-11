package nexus.inventario.persistencia;

import java.util.List;
import java.util.Optional;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.FalloPersistenciaInventarioException;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.RepositorioDeInventarios;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
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

    @Override
    public Inventario guardar(Inventario inventario) {
        try {
            return documentos.save(InventarioDocumento.de(inventario)).aDominio();
        } catch (DataAccessException error) {
            throw new FalloPersistenciaInventarioException(error);
        }
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
