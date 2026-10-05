package nexus.alertas;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertaCatalogoRepository extends MongoRepository<AlertaCatalogo, String> {

    /**
     * Alertas implementadas en el intervalo {@code (desde, hasta]}, de la mas
     * antigua a la mas reciente.
     *
     * <p>La consulta va escrita a mano y no derivada del nombre del metodo: la
     * forma derivada ({@code ImplementadaEnAfterAndImplementadaEnLessThanEqual})
     * pone dos criterios sobre la misma clave y Spring Data MongoDB la rechaza
     * al armarla ({@code InvalidMongoDbApiUsageException}), en cada llamada. Un
     * solo criterio con {@code $gt} y {@code $lte} es lo que Mongo espera.
     */
    @Query(value = "{ 'implementadaEn': { '$gt': ?0, '$lte': ?1 } }",
            sort = "{ 'implementadaEn': 1 }")
    List<AlertaCatalogo> buscarImplementadasEntre(Instant desdeExclusivo, Instant hastaInclusivo);

    /**
     * Las primeras alertas del intervalo {@code (desde, hasta]}, de la mas
     * antigua a la mas reciente, hasta el tamano de {@code pagina} — HU-NOT-001,
     * la lectura por lotes de {@code GET /alertas/cambios}. Mismo criterio que
     * {@link #buscarImplementadasEntre}; la pagina solo pone el tope.
     */
    @Query(value = "{ 'implementadaEn': { '$gt': ?0, '$lte': ?1 } }",
            sort = "{ 'implementadaEn': 1 }")
    List<AlertaCatalogo> buscarPrimerasImplementadasEntre(
            Instant desdeExclusivo,
            Instant hastaInclusivo,
            Pageable pagina);
}
