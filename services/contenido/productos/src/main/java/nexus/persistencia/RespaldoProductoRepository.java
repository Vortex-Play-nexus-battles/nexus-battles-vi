package nexus.persistencia;

import java.util.List;

import nexus.dominio.RespaldoProducto;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RespaldoProductoRepository extends MongoRepository<RespaldoProducto, String> {
        List<RespaldoProducto> findByProductoIdOrderByModificadoEnDesc(String productoId);
}
