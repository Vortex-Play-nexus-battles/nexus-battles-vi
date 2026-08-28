package nexus.inventario.persistencia;

import java.util.Optional;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.RepositorioDeInventarios;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioInventariosMongo implements RepositorioDeInventarios {

    private final RepositorioInventariosSpringData documentos;

    public RepositorioInventariosMongo(RepositorioInventariosSpringData documentos) {
        this.documentos = documentos;
    }

    @Override
    public Inventario guardar(Inventario inventario) {
        return documentos.save(InventarioDocumento.de(inventario)).aDominio();
    }

    @Override
    public Optional<Inventario> buscarPorPropietario(String propietarioId) {
        return documentos.findByPropietarioId(propietarioId).map(InventarioDocumento::aDominio);
    }
}
