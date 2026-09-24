package nexus.persistencia;

import java.util.Collection;

import nexus.dominio.Producto;
import nexus.dominio.EstadoProducto;
import nexus.dominio.TipoProducto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductoRepository extends MongoRepository<Producto, String> {
        long countByTipo(TipoProducto tipo);

        long countByEstado(EstadoProducto estado);

        // R16 — listado paginado del catalogo (GET /api/v1/productos, contrato
        // 1.2.0). Los estados llegan siempre como coleccion: por omision son
        // ACTIVO y UNICO, y con un estado explicito es solo ese. El orden y el
        // tamano de la pagina viajan en el Pageable.
        Page<Producto> findByEstadoIn(
                        Collection<EstadoProducto> estados,
                        Pageable paginacion);

        Page<Producto> findByTipoAndEstadoIn(
                        TipoProducto tipo,
                        Collection<EstadoProducto> estados,
                        Pageable paginacion);
}
