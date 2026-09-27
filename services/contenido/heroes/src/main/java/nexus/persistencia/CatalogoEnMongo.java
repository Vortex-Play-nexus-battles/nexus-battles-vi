package nexus.persistencia;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import nexus.dominio.CatalogoDeHeroes;
import nexus.dominio.HeroeNoDisponibleException;
import nexus.dominio.Prototipo;
import nexus.dominio.PrototiposIniciales;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Catalogo persistido en MongoDB (seccion 8 del documento: almacenamiento no
 * relacional para personajes e items). Activo bajo el perfil "mongo".
 *
 * <h2>Semilla versionada (B4)</h2>
 *
 * Al arrancar siembra los ocho prototipos iniciales de las Tablas 5, 6 y 7
 * —datos de partida, no un limite— con el mismo criterio que el catalogo de
 * productos. Hasta B4 solo sembraba si la coleccion estaba vacia: un cambio en
 * {@link PrototiposIniciales} nunca llegaba a una base que ya tenia algo. Ahora,
 * por cada prototipo:
 * <ul>
 *   <li>si falta, se inserta con {@code origen=SEMILLA} y la
 *       {@link PrototiposIniciales#VERSION} que lo escribio;</li>
 *   <li>si esta sembrado con una version menor y nadie lo edito, se reemplaza
 *       por el contenido nuevo, solo si sigue como se leyo;</li>
 *   <li>si alguien lo edito ({@code modificadoPor}), se respeta y se avisa en la
 *       bitacora;</li>
 *   <li>si esta al dia, o lo registro otro camino, no se toca.</li>
 * </ul>
 * Los prototipos sembrados antes de B4 no tienen marcas: se adoptan (hoy nadie
 * los puede editar: heroes no tiene escritura por API).
 */
public class CatalogoEnMongo implements CatalogoDeHeroes {

    private static final Logger BITACORA = LoggerFactory.getLogger(CatalogoEnMongo.class);

    private final MongoTemplate mongo;

    public CatalogoEnMongo(MongoTemplate mongo) {
        this.mongo = mongo;
        sembrar(PrototiposIniciales.LISTA, PrototiposIniciales.VERSION);
    }

    /** Lo que hizo una corrida de la semilla, para la bitacora y las pruebas. */
    record ResultadoSemilla(
            List<String> insertados,
            List<String> actualizados,
            List<String> alDia,
            List<String> respetados) {
    }

    ResultadoSemilla sembrar(List<Prototipo> prototipos, int version) {
        List<String> insertados = new ArrayList<>();
        List<String> actualizados = new ArrayList<>();
        List<String> alDia = new ArrayList<>();
        List<String> respetados = new ArrayList<>();

        for (Prototipo prototipo : prototipos) {
            String nombre = prototipo.nombre();
            PrototipoDocumento actual = mongo.findById(nombre, PrototipoDocumento.class);
            if (actual == null) {
                try {
                    mongo.insert(PrototipoDocumento.sembrado(prototipo, version));
                    insertados.add(nombre);
                } catch (DuplicateKeyException otraInstanciaLoSembro) {
                    alDia.add(nombre);
                }
            } else if (!actual.esDeLaSemilla() || actual.versionSembrada() >= version) {
                alDia.add(nombre);
            } else if (actual.editadoPorAlguien()) {
                respetados.add(nombre);
            } else if (reemplazarSiNoCambio(actual, PrototipoDocumento.sembrado(prototipo, version))) {
                actualizados.add(nombre);
            } else {
                alDia.add(nombre);
            }
        }

        BITACORA.info("Semilla de prototipos v{}: {} insertados, {} actualizados, {} al dia, {} respetados",
                version, insertados.size(), actualizados.size(), alDia.size(), respetados.size());
        respetados.forEach(nombre -> BITACORA.warn(
                "Semilla de prototipos v{}: {} fue editado; se respeta y no se pone al dia", version, nombre));
        return new ResultadoSemilla(insertados, actualizados, alDia, respetados);
    }

    /** Reemplaza el prototipo solo si su version sembrada y su autor siguen como se leyeron. */
    private boolean reemplazarSiNoCambio(PrototipoDocumento leido, PrototipoDocumento reemplazo) {
        Criteria versionLeida = leido.semillaVersion == null
                ? Criteria.where("semillaVersion").is(null)
                : Criteria.where("semillaVersion").is(leido.semillaVersion);
        Query sinCambios = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(leido.nombre),
                versionLeida,
                Criteria.where("modificadoPor").is(null)));
        return mongo.findAndReplace(sinCambios, reemplazo) != null;
    }

    @Override
    public void registrar(Prototipo prototipo) {
        if (buscar(prototipo.nombre()) != null) {
            throw new IllegalArgumentException(
                    "Ya existe un prototipo con el nombre \"" + prototipo.nombre() + "\".");
        }
        mongo.insert(PrototipoDocumento.de(prototipo));
    }

    @Override
    public List<Prototipo> listar() {
        return mongo.findAll(PrototipoDocumento.class).stream()
                .map(PrototipoDocumento::aDominio)
                .toList();
    }

    @Override
    public Prototipo fichaDe(String nombre) {
        PrototipoDocumento doc = buscar(nombre);
        if (doc == null) {
            throw new HeroeNoDisponibleException();
        }
        return doc.aDominio();
    }

    private PrototipoDocumento buscar(String nombre) {
        return mongo.findOne(
                new Query(Criteria.where("nombreNormalizado").is(normalizar(nombre))),
                PrototipoDocumento.class);
    }

    static String normalizar(String nombre) {
        return Normalizer.normalize(nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}
