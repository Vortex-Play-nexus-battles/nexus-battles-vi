package nexus.productos.persistencia;

import com.mongodb.client.result.UpdateResult;
import java.time.Instant;
import java.util.Optional;
import nexus.dominio.Producto;
import nexus.productos.dominio.DisponibilidadProducto;
import nexus.productos.dominio.EstadoAdquisicion;
import nexus.productos.dominio.ProductoNoEncontradoException;
import nexus.productos.dominio.RepositorioDisponibilidadProductos;
import nexus.productos.dominio.ResultadoAdquisicion;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public final class RepositorioDisponibilidadMongo
        implements RepositorioDisponibilidadProductos {

    private final MongoTemplate mongoTemplate;

    public RepositorioDisponibilidadMongo(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void guardar(DisponibilidadProducto producto) {
        Query consulta = Query.query(
                Criteria.where("_id").is(producto.productoId()));
        Update actualizacion = new Update()
                .set("tiraje", producto.unidadesDisponibles())
                .set("estado", producto.estado())
                .set("modificadoEn", Instant.now());

        UpdateResult resultado = mongoTemplate.updateFirst(
                consulta,
                actualizacion,
                Producto.class);
        if (resultado.getMatchedCount() == 0) {
            throw new ProductoNoEncontradoException(producto.productoId());
        }
    }

    @Override
    public Optional<DisponibilidadProducto> buscarPorId(String productoId) {
        return Optional.ofNullable(mongoTemplate.findById(productoId, Producto.class))
                .map(DisponibilidadProducto::desde);
    }

    @Override
    public ResultadoAdquisicion adquirirUnaUnidad(String productoId) {
        Producto limitado = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(productoId)
                        .and("tiraje").gt(0)),
                new Update()
                        .inc("tiraje", -1)
                        .set("modificadoEn", Instant.now()),
                FindAndModifyOptions.options().returnNew(true),
                Producto.class);
        if (limitado != null) {
            return aceptada();
        }

        Producto ilimitado = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(productoId)
                        .and("tiraje").is(DisponibilidadProducto.TIRAJE_ILIMITADO)),
                new Update().set("modificadoEn", Instant.now()),
                FindAndModifyOptions.options().returnNew(true),
                Producto.class);
        if (ilimitado != null) {
            return aceptada();
        }

        if (mongoTemplate.exists(
                Query.query(Criteria.where("_id").is(productoId)),
                Producto.class)) {
            return new ResultadoAdquisicion(
                    EstadoAdquisicion.AGOTADO,
                    "El producto está agotado");
        }
        return new ResultadoAdquisicion(
                EstadoAdquisicion.NO_ENCONTRADO,
                "El producto no existe");
    }

    private static ResultadoAdquisicion aceptada() {
        return new ResultadoAdquisicion(
                EstadoAdquisicion.ACEPTADA,
                "Unidad reservada");
    }
}
