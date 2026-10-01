package nexus.misiones.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;

/**
 * La forma de la consulta de las vencidas (HU-SIM-007), comprobada sin Docker: lo que se le manda a Mongo. Que
 * Mongo la entienda y devuelva lo debido lo prueba {@code RepositoriosMongoIT}, que corre donde hay Docker.
 */
class ConsultaDeVencidasTest {

    @Test
    @DisplayName("las vencidas excluyen a las que otra vuelta tiene reservadas, y admiten las que no traen el campo")
    void excluyeLasReservadas() {
        List<Query> consultas = new ArrayList<>();
        MongoOperations mongo = (MongoOperations) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {MongoOperations.class}, (proxy, metodo, args) -> {
                    if (metodo.getName().equals("find")) {
                        consultas.add((Query) args[0]);
                        return List.of();
                    }
                    throw new UnsupportedOperationException(metodo.getName());
                });

        new RepositorioEjecucionesMongo(mongo).vencidas(Instant.parse("2026-10-01T10:00:00Z"), 7);

        assertThat(consultas).hasSize(1);
        Query consulta = consultas.getFirst();
        String documento = consulta.getQueryObject().toString();
        assertThat(documento).contains("estado=EN_PROGRESO").contains("terminaEn=");
        // O no tiene reserva (campo ausente o nulo) o la reserva ya vencio.
        assertThat(documento).contains("$or").contains("simulacionReservadaHasta=null")
                .contains("simulacionReservadaHasta=Document{{$lte=");
        assertThat(consulta.getLimit()).isEqualTo(7);
    }
}
