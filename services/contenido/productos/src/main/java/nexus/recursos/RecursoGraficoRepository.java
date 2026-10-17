package nexus.recursos;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecursoGraficoRepository extends MongoRepository<RecursoGrafico, String> {

    /** El mismo archivo, de la misma clase y para el mismo producto, todavía en la biblioteca. */
    Optional<RecursoGrafico> findFirstByHuellaAndClaseAndProductoIdAndRetiradoEnIsNull(
            String huella, ClaseDeRecurso clase, String productoId);
}
