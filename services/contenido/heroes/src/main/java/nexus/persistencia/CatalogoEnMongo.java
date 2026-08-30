package nexus.persistencia;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import nexus.dominio.CatalogoDeHeroes;
import nexus.dominio.HeroeNoDisponibleException;
import nexus.dominio.Prototipo;
import nexus.dominio.PrototiposIniciales;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Catalogo persistido en MongoDB (seccion 8 del documento: almacenamiento no
 * relacional para personajes e items). Activo bajo el perfil "mongo". Si la
 * coleccion esta vacia al arrancar, se siembran los ocho prototipos iniciales
 * de las Tablas 5, 6 y 7 — que son datos de partida, no un limite.
 */
public class CatalogoEnMongo implements CatalogoDeHeroes {

    private final MongoTemplate mongo;

    public CatalogoEnMongo(MongoTemplate mongo) {
        this.mongo = mongo;
        if (mongo.count(new Query(), PrototipoDocumento.class) == 0) {
            PrototiposIniciales.LISTA.forEach(p -> mongo.insert(PrototipoDocumento.de(p)));
        }
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
