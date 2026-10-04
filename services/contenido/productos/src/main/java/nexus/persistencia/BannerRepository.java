package nexus.persistencia;

import java.time.Instant;
import java.util.List;

import nexus.dominio.Banner;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BannerRepository extends MongoRepository<Banner, String> {

        List<Banner> findByRetiradoFalseAndPublicarDesdeLessThanEqualAndVigenteHastaGreaterThanOrderByPublicarDesdeDesc(
                Instant publicarDesde,
                Instant vigenteHasta);
}
