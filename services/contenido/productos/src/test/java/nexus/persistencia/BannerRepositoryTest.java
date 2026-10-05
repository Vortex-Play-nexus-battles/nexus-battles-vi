package nexus.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;

import nexus.dominio.Banner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@DataMongoTest
@Testcontainers
class BannerRepositoryTest {

        @Container
        @ServiceConnection
        static final MongoDBContainer MONGODB = new MongoDBContainer("mongo:8.0");

        @Autowired
        private BannerRepository repositorio;

        @BeforeEach
        void limpiar() {
                repositorio.deleteAll();
        }

        @Test
        @DisplayName("solo consulta banners vigentes y no retirados")
        void filtraBannersVisibles() {
                Instant ahora = Instant.parse("2026-09-24T07:00:00Z");
                repositorio.saveAll(List.of(
                        banner("vigente", ahora.minusSeconds(60),
                                ahora.plusSeconds(60), false),
                        banner("futuro", ahora.plusSeconds(60),
                                ahora.plusSeconds(120), false),
                        banner("vencido", ahora.minusSeconds(120),
                                ahora.minusSeconds(60), false),
                        banner("retirado", ahora.minusSeconds(60),
                                ahora.plusSeconds(60), true)));

                List<Banner> visibles = repositorio
                        .findByRetiradoFalseAndPublicarDesdeLessThanEqualAndVigenteHastaGreaterThanOrderByPublicarDesdeDesc(
                                ahora,
                                ahora);

                assertEquals(List.of("vigente"),
                        visibles.stream().map(Banner::id).toList());
        }

        private Banner banner(
                String id,
                Instant publicarDesde,
                Instant vigenteHasta,
                boolean retirado) {
                return new Banner(
                        id,
                        "Mensaje " + id,
                        publicarDesde,
                        vigenteHasta,
                        retirado,
                        publicarDesde,
                        publicarDesde);
        }
}
