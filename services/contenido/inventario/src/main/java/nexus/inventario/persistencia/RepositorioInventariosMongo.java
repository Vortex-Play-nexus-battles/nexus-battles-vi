package nexus.inventario.persistencia;

import java.util.Optional;
import nexus.inventario.dominio.FalloPersistenciaInventarioException;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.RepositorioDeInventarios;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioInventariosMongo implements RepositorioDeInventarios {

    private final RepositorioInventariosSpringData documentos;

    public RepositorioInventariosMongo(RepositorioInventariosSpringData documentos) {
        this.documentos = documentos;
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
}
