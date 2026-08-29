package nexus.inventario.persistencia;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

interface RepositorioInventariosSpringData extends MongoRepository<InventarioDocumento, String> {

    Optional<InventarioDocumento> findByPropietarioId(String propietarioId);

    Optional<InventarioDocumento> findByElementosId(String elementoId);
}
