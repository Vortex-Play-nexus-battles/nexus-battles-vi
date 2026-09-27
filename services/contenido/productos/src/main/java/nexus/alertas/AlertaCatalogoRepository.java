package nexus.alertas;

import java.time.Instant;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertaCatalogoRepository extends MongoRepository<AlertaCatalogo, String> {

    List<AlertaCatalogo>
            findByImplementadaEnAfterAndImplementadaEnLessThanEqualOrderByImplementadaEnAsc(
                    Instant desde,
                    Instant hasta);
}
