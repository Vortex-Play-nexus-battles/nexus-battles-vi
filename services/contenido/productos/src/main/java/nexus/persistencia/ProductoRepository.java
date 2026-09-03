package nexus.persistencia;

import nexus.dominio.Producto;
import nexus.dominio.EstadoProducto;
import nexus.dominio.TipoProducto;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductoRepository extends MongoRepository<Producto, String> {
        long countByTipo(TipoProducto tipo);

        long countByEstado(EstadoProducto estado);
}
