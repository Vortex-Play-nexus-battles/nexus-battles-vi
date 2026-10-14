package nexus.auditoria;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

/**
 * Las operaciones administrativas del catálogo — SA-18. Solo se insertan y se
 * leen: nada en el servicio las modifica ni las borra.
 */
public interface OperacionAdministrativaRepository extends MongoRepository<OperacionAdministrativa, String> {

        /**
         * Las operaciones del intervalo {@code (desde, hasta]}, de la más
         * antigua a la más reciente. Escrita a mano por la misma razón que la
         * de las alertas del catálogo: la forma derivada del nombre pone dos
         * criterios sobre la misma clave y Spring Data MongoDB la rechaza.
         */
        @Query(value = "{ 'ocurridaEn': { '$gt': ?0, '$lte': ?1 } }", sort = "{ 'ocurridaEn': 1, '_id': 1 }")
        List<OperacionAdministrativa> buscarEntre(Instant desdeExclusivo, Instant hastaInclusivo);

        /** Las primeras del intervalo {@code (desde, hasta]}, hasta el tamaño de la página. */
        @Query(value = "{ 'ocurridaEn': { '$gt': ?0, '$lte': ?1 } }", sort = "{ 'ocurridaEn': 1, '_id': 1 }")
        List<OperacionAdministrativa> buscarPrimerasEntre(
                        Instant desdeExclusivo,
                        Instant hastaInclusivo,
                        Pageable pagina);

        /** El historial de un objeto (un banner, por ejemplo), de lo más reciente a lo más antiguo. */
        List<OperacionAdministrativa> findByObjetoAndObjetoIdOrderByOcurridaEnDesc(
                        ObjetoAuditado objeto,
                        String objetoId);
}
