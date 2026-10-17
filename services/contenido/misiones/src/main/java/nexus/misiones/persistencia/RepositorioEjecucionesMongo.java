package nexus.misiones.persistencia;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nexus.misiones.dominio.Ejecucion;
import nexus.misiones.dominio.EjecucionModificadaConcurrentemente;
import nexus.misiones.dominio.EstadoEjecucion;
import nexus.misiones.dominio.RepositorioDeEjecuciones;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * {@link RepositorioDeEjecuciones} sobre MongoDB 8, la base documental del
 * dominio de contenido (seccion 8: «el modulo de items y personajes [...] bases
 * de datos no relacionales»).
 */
public class RepositorioEjecucionesMongo implements RepositorioDeEjecuciones {

    /** Tope de lo que se lee de un jugador de una vez (historial, tablon). */
    static final int MAXIMO_POR_JUGADOR = 500;

    private final MongoOperations mongo;

    public RepositorioEjecucionesMongo(MongoOperations mongo) {
        this.mongo = mongo;
    }

    /**
     * Escritura condicional a la version leida.
     *
     * <ul>
     *   <li>Nueva (sin version): {@code insert}. Si el indice unico parcial
     *       dice que el jugador ya tiene esa mision en curso, o la clave de
     *       idempotencia ya se uso, no se guarda.</li>
     *   <li>Existente: se reemplaza SOLO si sigue en la version que se leyo
     *       ({@code _id} y {@code version} en el filtro). Si otra escritura
     *       llego antes, el filtro no casa y no se guarda nada.</li>
     * </ul>
     */
    @Override
    public Ejecucion guardar(Ejecucion ejecucion) {
        if (ejecucion.version() == null) {
            EjecucionDocumento nuevo = EjecucionDocumento.de(ejecucion, 0);
            try {
                return mongo.insert(nuevo).aDominio();
            } catch (DuplicateKeyException repetida) {
                throw new EjecucionModificadaConcurrentemente(
                        "El jugador ya tiene esa mision en curso, o la clave de la matricula ya se uso", repetida);
            }
        }
        long leida = ejecucion.version();
        EjecucionDocumento siguiente = EjecucionDocumento.de(ejecucion, leida + 1);
        Query estaVersion = new Query(Criteria.where("_id").is(ejecucion.id().toString()).and("version").is(leida));
        EjecucionDocumento anterior = mongo.findAndReplace(estaVersion, siguiente);
        if (anterior == null) {
            throw new EjecucionModificadaConcurrentemente(
                    "La ejecucion " + ejecucion.id() + " cambio desde que se leyo", null);
        }
        return siguiente.aDominio();
    }

    @Override
    public Optional<Ejecucion> buscar(UUID id) {
        return Optional.ofNullable(mongo.findById(id.toString(), EjecucionDocumento.class))
                .map(EjecucionDocumento::aDominio);
    }

    @Override
    public Optional<Ejecucion> buscarPorClave(String jugadorUid, String claveIdempotencia) {
        Query consulta = new Query(Criteria.where("jugadorUid").is(jugadorUid)
                .and("claveIdempotencia").is(claveIdempotencia));
        return Optional.ofNullable(mongo.findOne(consulta, EjecucionDocumento.class))
                .map(EjecucionDocumento::aDominio);
    }

    @Override
    public List<Ejecucion> delJugador(String jugadorUid) {
        Query consulta = new Query(Criteria.where("jugadorUid").is(jugadorUid))
                .with(Sort.by(Sort.Direction.DESC, "iniciadaEn"))
                .limit(MAXIMO_POR_JUGADOR);
        return leer(consulta);
    }

    @Override
    public List<Ejecucion> delJugadorEnMision(String jugadorUid, String misionId) {
        Query consulta = new Query(Criteria.where("jugadorUid").is(jugadorUid).and("misionId").is(misionId))
                .with(Sort.by(Sort.Direction.DESC, "iniciadaEn"))
                .limit(MAXIMO_POR_JUGADOR);
        return leer(consulta);
    }

    @Override
    public List<Ejecucion> enCursoDelJugador(String jugadorUid) {
        Query consulta = new Query(Criteria.where("jugadorUid").is(jugadorUid)
                .and("estado").is(EstadoEjecucion.EN_PROGRESO))
                .with(Sort.by(Sort.Direction.ASC, "terminaEn"));
        return leer(consulta);
    }

    @Override
    public long iniciadasDesde(String jugadorUid, String misionId, Instant desde) {
        // La cancelada sin penalizacion (la simulacion fallaba por un error del sistema) no gasta el intento.
        Query consulta = new Query(Criteria.where("jugadorUid").is(jugadorUid).and("misionId").is(misionId)
                .and("iniciadaEn").gte(desde).and("sinPenalizacion").ne(true));
        return mongo.count(consulta, EjecucionDocumento.class);
    }

    /**
     * Las vencidas que toca simular ya: sin intento aplazado ({@code proximoIntento}
     * ausente o nulo, que es como nace una ejecucion) o con el plazo del
     * aplazamiento cumplido ({@link Ejecucion#simulacionAplazada}). Asi una
     * ejecucion que no se puede simular no ocupa su sitio del lote en cada vuelta.
     */
    @Override
    public List<Ejecucion> vencidas(Instant ahora, int limite) {
        Query consulta = new Query(Criteria.where("estado").is(EstadoEjecucion.EN_PROGRESO).and("terminaEn").lte(ahora)
                .orOperator(Criteria.where("proximoIntento").is(null), Criteria.where("proximoIntento").lte(ahora)))
                .with(Sort.by(Sort.Direction.ASC, "terminaEn"))
                .limit(limite);
        return leer(consulta);
    }

    @Override
    public List<Ejecucion> conLiquidacionPendiente(Instant ahora, int limite) {
        Query consulta = new Query(Criteria.where("liquidacionPendiente").is(true).and("proximoIntento").lte(ahora))
                .with(Sort.by(Sort.Direction.ASC, "proximoIntento"))
                .limit(limite);
        return leer(consulta);
    }

    private List<Ejecucion> leer(Query consulta) {
        return mongo.find(consulta, EjecucionDocumento.class).stream().map(EjecucionDocumento::aDominio).toList();
    }
}
