package nexus.misiones.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import nexus.misiones.dominio.simulacion.EventoDeCombate;
import nexus.misiones.dominio.simulacion.EventosDePrueba;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * Los turnos de combate contra un MongoDB 8 de verdad (HU-SIM-003): que los
 * records anidados vuelvan identicos, que se lean en orden, que reemplazar
 * borre lo que dejo un intento anterior y los indices.
 */
@DataMongoTest(properties = "spring.data.mongodb.auto-index-creation=true")
@Testcontainers
@Import(RepositorioEventosDeCombateMongo.class)
class EventosDeCombateMongoIT {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGODB = new MongoDBContainer("mongo:8.0");

    private static final UUID UNA = UUID.fromString("0c7a2d9e-4f8b-4a55-9b65-6f1d3b2f0a11");
    private static final UUID OTRA = UUID.fromString("1d8b3e0f-5a9c-4b66-8c76-7a2e4c3a1b22");

    @Autowired
    private RepositorioEventosDeCombateMongo eventos;

    @Autowired
    private MongoTemplate mongo;

    @BeforeEach
    void limpiar() {
        // remove y no drop: tirar la coleccion se llevaria los indices.
        mongo.remove(new Query(), EventoDeCombateDocumento.class);
    }

    @Test
    @DisplayName("los turnos de una ejecucion se leen en orden de secuencia y vuelven identicos")
    void idaYVuelta() {
        List<EventoDeCombate> escritos = List.of(EventosDePrueba.evento(UNA, 1), EventosDePrueba.eventoSinJugada(UNA, 2),
                EventosDePrueba.evento(UNA, 3));

        eventos.reemplazar(UNA, escritos);

        assertThat(eventos.de(UNA)).containsExactlyElementsOf(escritos);
    }

    @Test
    @DisplayName("reemplazar deja solo los turnos nuevos: lo de un intento anterior se borra")
    void reemplazarBorraLoAnterior() {
        eventos.reemplazar(UNA, List.of(EventosDePrueba.evento(UNA, 1), EventosDePrueba.evento(UNA, 2),
                EventosDePrueba.evento(UNA, 3)));

        eventos.reemplazar(UNA, List.of(EventosDePrueba.evento(UNA, 1)));

        assertThat(eventos.de(UNA)).hasSize(1);
    }

    @Test
    @DisplayName("no toca los turnos de otra ejecucion")
    void aislamiento() {
        eventos.reemplazar(OTRA, List.of(EventosDePrueba.evento(OTRA, 1)));

        eventos.reemplazar(UNA, List.of(EventosDePrueba.evento(UNA, 1), EventosDePrueba.evento(UNA, 2)));

        assertThat(eventos.de(OTRA)).hasSize(1);
        assertThat(eventos.de(UNA)).hasSize(2);
    }

    @Test
    @DisplayName("una ejecucion sin turnos devuelve la lista vacia")
    void sinTurnos() {
        assertThat(eventos.de(UNA)).isEmpty();
        eventos.reemplazar(UNA, List.of());
        assertThat(eventos.de(UNA)).isEmpty();
    }

    @Test
    @DisplayName("el indice por ejecucion y secuencia es unico: dos turnos con la misma secuencia no caben")
    void indiceUnico() {
        assertThat(mongo.indexOps(EventoDeCombateDocumento.class).getIndexInfo())
                .anySatisfy(indice -> {
                    assertThat(indice.getName()).isEqualTo("ejecucion_secuencia");
                    assertThat(indice.isUnique()).isTrue();
                });

        mongo.insert(EventoDeCombateDocumento.de(EventosDePrueba.evento(UNA, 1), Instant.now()));
        EventoDeCombateDocumento repetido = new EventoDeCombateDocumento("otro-id", UNA.toString(), "templo-olvidado",
                1, 1, "X", 1, null, null, null, List.of(), null, null, Instant.now());

        assertThatThrownBy(() -> mongo.insert(repetido)).isInstanceOf(DuplicateKeyException.class);
    }
}
