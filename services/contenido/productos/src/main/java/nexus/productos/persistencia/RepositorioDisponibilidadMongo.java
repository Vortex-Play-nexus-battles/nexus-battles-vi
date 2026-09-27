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
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/**
 * Tiraje y estado de los productos sobre MongoDB, con escrituras atomicas.
 *
 * <p>Todas las escrituras de aqui son parciales ({@code updateFirst},
 * {@code findAndModify}) y suben la version del producto ({@code @Version}) en
 * la misma operacion. Es lo que impide que una edicion completa de un
 * administrador, leida antes, deshaga despues una suspension o devuelva
 * unidades ya vendidas: su guardado encuentra otra version y se rechaza con
 * conflicto.
 */
@Repository
public class RepositorioDisponibilidadMongo
        implements RepositorioDisponibilidadProductos {

    /**
     * Cuantas claves de reserva recuerda cada producto. Es la ventana en la que
     * un reintento con la misma clave se reconoce aunque el registro duradero
     * de la adquisicion no llegara a escribirse (ver AdquirirProductoServicio).
     * Cien reservas del mismo producto entre un intento y su reintento no caben
     * en una peticion que se repite a los segundos.
     */
    static final int VENTANA_DE_RESERVAS = 100;

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
                .set("modificadoEn", Instant.now())
                .inc("version", 1);
        if (producto.estado() == EstadoProducto.SUSPENDIDO) {
            actualizacion.set(
                    "estadoAnteriorSuspension",
                    producto.estadoAlReactivar());
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

    /**
     * Una reserva, atomica y una sola vez por clave.
     *
     * <p>El filtro exige que la clave NO este entre las reservas recientes y la
     * actualizacion la anade en la misma operacion que descuenta la unidad: dos
     * peticiones con la misma clave, aunque lleguen a la vez, descuentan una
     * sola vez. Si ninguna de las dos variantes (tiraje limitado o ilimitado)
     * aplica, se relee el producto para decir por que: ya reservado con esa
     * clave, suspendido, agotado o inexistente.
     */
    @Override
    public ResultadoAdquisicion adquirirUnaUnidad(String productoId, String clave) {
        Producto limitado = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(productoId)
                        .and("estado").ne(EstadoProducto.SUSPENDIDO)
                        .and("tiraje").gt(0)
                        .and("reservasRecientes").ne(clave)),
                recordarClave(clave).inc("tiraje", -1),
                FindAndModifyOptions.options().returnNew(true),
                Producto.class);
        if (limitado != null) {
            return aceptada();
        }

        Producto ilimitado = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(productoId)
                        .and("estado").ne(EstadoProducto.SUSPENDIDO)
                        .and("tiraje").is(DisponibilidadProducto.TIRAJE_ILIMITADO)
                        .and("reservasRecientes").ne(clave)),
                recordarClave(clave),
                FindAndModifyOptions.options().returnNew(true),
                Producto.class);
        if (ilimitado != null) {
            return aceptada();
        }

        Producto existente = mongoTemplate.findById(productoId, Producto.class);
        if (existente == null) {
            return new ResultadoAdquisicion(
                    EstadoAdquisicion.NO_ENCONTRADO,
                    "El producto no existe");
        }
        if (existente.reservasRecientes().contains(clave)) {
            return aceptada();
        }
        if (existente.estado() == EstadoProducto.SUSPENDIDO) {
            return new ResultadoAdquisicion(
                    EstadoAdquisicion.SUSPENDIDO,
                    "El producto está suspendido y no se puede adquirir");
        }
        return new ResultadoAdquisicion(
                EstadoAdquisicion.AGOTADO,
                "El producto está agotado");
    }

    private static Update recordarClave(String clave) {
        Update actualizacion = new Update()
                .set("modificadoEn", Instant.now())
                .inc("version", 1);
        actualizacion.push("reservasRecientes").slice(-VENTANA_DE_RESERVAS).each(clave);
        return actualizacion;
    }

    private static ResultadoAdquisicion aceptada() {
        return new ResultadoAdquisicion(
                EstadoAdquisicion.ACEPTADA,
                "Unidad reservada");
    }

    /**
     * El estado al que vuelve un producto suspendido. Desde B4 el campo esta
     * mapeado en {@link Producto}; un documento suspendido sin el (anterior a
     * que se guardara) vuelve a ACTIVO.
     */
    private static EstadoProducto estadoAlReactivar(Producto producto) {
        if (producto.estado() != EstadoProducto.SUSPENDIDO) {
            return producto.estado();
        }
        return producto.estadoAnteriorSuspension() == null
                ? EstadoProducto.ACTIVO
                : producto.estadoAnteriorSuspension();
    }
}
