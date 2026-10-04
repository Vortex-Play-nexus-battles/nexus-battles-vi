package nexus.misiones.persistencia;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import nexus.misiones.dominio.RepositorioDeEventosDeCombate;
import nexus.misiones.dominio.simulacion.EventoDeCombate;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * {@link RepositorioDeEventosDeCombate} sobre la coleccion
 * {@code eventos_de_combate} de la base de misiones (regla 7: nadie mas la
 * lee). Una escritura por mision simulada: borra lo que hubiera de la
 * ejecucion e inserta todos los turnos de una vez.
 */
public class RepositorioEventosDeCombateMongo implements RepositorioDeEventosDeCombate {

    private final MongoOperations mongo;

    public RepositorioEventosDeCombateMongo(MongoOperations mongo) {
        this.mongo = mongo;
    }

    @Override
    public void reemplazar(UUID ejecucionId, List<EventoDeCombate> eventos) {
        mongo.remove(deLaEjecucion(ejecucionId), EventoDeCombateDocumento.class);
        if (eventos.isEmpty()) {
            return;
        }
        Instant ahora = Instant.now();
        mongo.insert(eventos.stream().map(e -> EventoDeCombateDocumento.de(e, ahora)).toList(),
                EventoDeCombateDocumento.class);
    }

    @Override
    public List<EventoDeCombate> de(UUID ejecucionId) {
        return mongo.find(deLaEjecucion(ejecucionId).with(Sort.by("secuencia")), EventoDeCombateDocumento.class)
                .stream().map(EventoDeCombateDocumento::aDominio).toList();
    }

    private static Query deLaEjecucion(UUID ejecucionId) {
        return new Query(Criteria.where("ejecucionId").is(ejecucionId.toString()));
    }
}
