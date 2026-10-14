package nexus.aplicacion;

import java.time.Instant;
import java.util.UUID;

import nexus.dominio.EstadoProducto;
import nexus.dominio.Producto;
import nexus.dominio.ProductoNoEncontradoException;
import nexus.dominio.RespaldoProducto;
import nexus.dominio.TipoCambioProducto;
import nexus.persistencia.ProductoRepository;
import nexus.persistencia.RespaldoProductoRepository;
import nexus.productos.dominio.CatalogoProductos;
import nexus.productos.dominio.DisponibilidadProducto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Suspension logica y reactivacion de productos — HU-PRD-004, B4 (seccion
 * 7.2.1: "eliminacion logica").
 *
 * <p>Suspender no borra nada ni toca el tiraje: el producto sigue en el
 * catalogo y en los inventarios de quienes ya lo tienen, y deja de estar
 * disponible para nuevas adquisiciones (la reserva de tiraje lo rechaza) y para
 * el publico (la proyeccion publica no lo muestra). Reactivar lo devuelve al
 * estado que tenia, ACTIVO o UNICO. Las dos son idempotentes: repetirlas no
 * escribe nada.
 */
@Service
public class DisponibilidadDelCatalogoServicio {

        private static final Logger BITACORA = LoggerFactory.getLogger(DisponibilidadDelCatalogoServicio.class);

        private final CatalogoProductos catalogo;
        private final ProductoRepository productos;
        private final RespaldoProductoRepository respaldos;

        public DisponibilidadDelCatalogoServicio(
                        CatalogoProductos catalogo,
                        ProductoRepository productos,
                        RespaldoProductoRepository respaldos) {
                this.catalogo = catalogo;
                this.productos = productos;
                this.respaldos = respaldos;
        }

        /** @param autor identificador estable del administrador, para la bitacora */
        public DisponibilidadProducto suspender(String productoId, String autor) {
                Producto actual = buscar(productoId);
                if (actual.estado() == EstadoProducto.SUSPENDIDO) {
                        return catalogo.suspender(productoId);
                }
                RespaldoProducto respaldo = auditarEstado(
                                actual,
                                EstadoProducto.SUSPENDIDO,
                                actual.estado(),
                                autor,
                                TipoCambioProducto.SUSPENSION);
                DisponibilidadProducto suspendido;
                try {
                        suspendido = catalogo.suspender(productoId);
                } catch (RuntimeException fallo) {
                        respaldos.deleteById(respaldo.id());
                        throw fallo;
                }
                BITACORA.info("Producto {} suspendido por {}", productoId, autor);
                return suspendido;
        }

        /** @param autor identificador estable del administrador, para la bitacora */
        public DisponibilidadProducto reactivar(String productoId, String autor) {
                Producto actual = buscar(productoId);
                if (actual.estado() != EstadoProducto.SUSPENDIDO) {
                        return catalogo.reactivar(productoId);
                }
                EstadoProducto destino = actual.estadoAnteriorSuspension() == null
                                ? EstadoProducto.ACTIVO
                                : actual.estadoAnteriorSuspension();
                RespaldoProducto respaldo = auditarEstado(
                                actual,
                                destino,
                                null,
                                autor,
                                TipoCambioProducto.REACTIVACION);
                DisponibilidadProducto reactivado;
                try {
                        reactivado = catalogo.reactivar(productoId);
                } catch (RuntimeException fallo) {
                        respaldos.deleteById(respaldo.id());
                        throw fallo;
                }
                BITACORA.info("Producto {} reactivado por {} ({})", productoId, autor, reactivado.estado());
                return reactivado;
        }

        private Producto buscar(String productoId) {
                return productos.findById(productoId)
                                .orElseThrow(ProductoNoEncontradoException::new);
        }

        private RespaldoProducto auditarEstado(
                        Producto actual,
                        EstadoProducto estado,
                        EstadoProducto estadoAnteriorSuspension,
                        String autor,
                        TipoCambioProducto tipo) {
                Instant ahora = Instant.now();
                Producto aplicado = new Producto(
                                actual.id(), actual.nombre(), actual.imagen(), actual.descripcion(), actual.tipo(),
                                actual.tiraje(), actual.precioCreditos(), actual.precioMonedaReal(), actual.premium(),
                                actual.prototipo(), actual.heroe(), actual.costoPoder(), actual.multiplicadorNivel(),
                                actual.turnosCarga(), actual.turnosRecarga(), actual.efectoGeneral(),
                                actual.efectoPotenciado(), actual.defensa(), actual.parte(), actual.efecto(),
                                actual.poderDeAtaque(), actual.tasaDeCaida(), estado, actual.version() + 1,
                                actual.creadoEn(), ahora, actual.promocion(), actual.origen(), actual.semillaVersion(),
                                actual.modificadoPor(), estadoAnteriorSuspension, actual.reservasRecientes());
                RespaldoProducto respaldo = new RespaldoProducto(
                                UUID.randomUUID().toString(),
                                actual.id(),
                                actual,
                                aplicado,
                                ahora,
                                autor,
                                tipo,
                                null);
                respaldos.save(respaldo);
                return respaldo;
        }
}
