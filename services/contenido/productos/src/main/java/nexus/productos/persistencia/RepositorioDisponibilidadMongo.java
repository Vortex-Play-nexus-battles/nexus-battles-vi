package nexus.productos.persistencia;

import com.mongodb.client.result.UpdateResult;
import java.time.Instant;
import java.util.Optional;
import nexus.dominio.EstadoProducto;
import nexus.dominio.Producto;
import nexus.productos.dominio.DisponibilidadProducto;
import nexus.productos.dominio.EstadoAdquisicion;
import nexus.productos.dominio.ProductoNoEncontradoException;
import nexus.productos.dominio.RepositorioDisponibilidadProductos;
import nexus.productos.dominio.ResultadoAdquisicion;
import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioDisponibilidadMongo
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
                .set("estado", producto.estado())
                .set("modificadoEn", Instant.now());
        if (producto.estado() == EstadoProducto.SUSPENDIDO) {
            actualizacion.set(
                    "estadoAnteriorSuspension",
                    producto.estadoAlReactivar().name());
        } else {
            actualizacion.unset("estadoAnteriorSuspension");
        }

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
        Producto producto = mongoTemplate.findById(productoId, Producto.class);
        if (producto == null) {
            return Optional.empty();
        }
        return Optional.of(DisponibilidadProducto.desde(
                producto,
                estadoAlReactivar(producto)));
    }

    @Override
    public ResultadoAdquisicion adquirirUnaUnidad(String productoId) {
        Producto limitado = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(productoId)
                        .and("estado").ne(EstadoProducto.SUSPENDIDO)
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
                        .and("estado").ne(EstadoProducto.SUSPENDIDO)
                        .and("tiraje").is(DisponibilidadProducto.TIRAJE_ILIMITADO)),
                new Update().set("modificadoEn", Instant.now()),
                FindAndModifyOptions.options().returnNew(true),
                Producto.class);
        if (ilimitado != null) {
            return aceptada();
        }

        Producto existente = mongoTemplate.findById(productoId, Producto.class);
        if (existente != null && existente.estado() == EstadoProducto.SUSPENDIDO) {
            return new ResultadoAdquisicion(
                    EstadoAdquisicion.SUSPENDIDO,
                    "El producto está suspendido y no se puede adquirir");
        }
        if (existente != null) {
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

    private EstadoProducto estadoAlReactivar(Producto producto) {
        if (producto.estado() != EstadoProducto.SUSPENDIDO) {
            return producto.estado();
        }
        Document documento = mongoTemplate
                .getCollection(mongoTemplate.getCollectionName(Producto.class))
                .find(new Document("_id", producto.id()))
                .first();
        if (documento == null) {
            return EstadoProducto.ACTIVO;
        }
        String estadoAnterior = documento.getString("estadoAnteriorSuspension");
        if (estadoAnterior == null) {
            return EstadoProducto.ACTIVO;
        }
        try {
            return EstadoProducto.valueOf(estadoAnterior);
        } catch (IllegalArgumentException excepcion) {
            return EstadoProducto.ACTIVO;
        }
    }
}
