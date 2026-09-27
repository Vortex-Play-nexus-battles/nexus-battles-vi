package nexus.persistencia;

import nexus.dominio.Producto;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Implementacion de {@link ProductoRepositoryPersonalizado} sobre
 * {@link MongoTemplate}; Spring Data la une a {@link ProductoRepository} por el
 * sufijo {@code Impl}.
 */
class ProductoRepositoryPersonalizadoImpl implements ProductoRepositoryPersonalizado {

        private final MongoTemplate mongo;

        ProductoRepositoryPersonalizadoImpl(MongoTemplate mongo) {
                this.mongo = mongo;
        }

        @Override
        public boolean reemplazarSemillaSiNoCambio(Producto reemplazo, int versionEsperada) {
                Query sinCambios = Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(reemplazo.id()),
                        conVersion(versionEsperada),
                        // Nulo o ausente: ningun administrador lo ha editado.
                        Criteria.where("modificadoPor").is(null)));
                // findAndReplace no toca la version por su cuenta: el reemplazo ya
                // la trae incrementada, igual que haria un save versionado.
                return mongo.findAndReplace(sinCambios, reemplazo) != null;
        }

        @Override
        public void normalizarVersion(String id) {
                mongo.updateFirst(
                        Query.query(new Criteria().andOperator(Criteria.where("_id").is(id), conVersion(0))),
                        new Update().set("version", 1),
                        mongo.getCollectionName(Producto.class));
        }

        /** La version dada; la 0 tambien cubre los documentos que no la tienen. */
        private static Criteria conVersion(int version) {
                if (version != 0) {
                        return Criteria.where("version").is(version);
                }
                return new Criteria().orOperator(
                        Criteria.where("version").is(0),
                        Criteria.where("version").is(null));
        }
}
