package nexus.alertas;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConsultaAlertasJugadorRepository
        extends MongoRepository<ConsultaAlertasJugador, String> {
}
