package nexus.persistencia;

import nexus.dominio.AdquisicionRegistrada;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdquisicionRegistradaRepository extends MongoRepository<AdquisicionRegistrada, String> {
}
