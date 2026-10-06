package nexus.aplicacion;

import java.time.Instant;
import java.util.UUID;

import nexus.dominio.Producto;
import nexus.dominio.ProductoNoEncontradoException;
import nexus.dominio.RespaldoProducto;
import nexus.dominio.RespaldoProductoNoEncontradoException;
import nexus.dominio.ReversionProductoEnConflictoException;
import nexus.dominio.TipoCambioProducto;
import nexus.persistencia.ProductoRepository;
import nexus.persistencia.RespaldoProductoRepository;
import org.springframework.stereotype.Service;

@Service
public class RevertirProductoServicio {

        private final ProductoRepository productos;
        private final RespaldoProductoRepository respaldos;

        public RevertirProductoServicio(
                        ProductoRepository productos,
                        RespaldoProductoRepository respaldos) {
                this.productos = productos;
                this.respaldos = respaldos;
        }

        public Producto revertir(String productoId, String respaldoId, String autor) {
                Producto actual = productos.findById(productoId)
                                .orElseThrow(ProductoNoEncontradoException::new);
                RespaldoProducto objetivo = respaldos.findById(respaldoId)
                                .filter(respaldo -> productoId.equals(respaldo.productoId()))
                                .filter(respaldo -> respaldo.estadoAnterior() != null)
                                .orElseThrow(RespaldoProductoNoEncontradoException::new);

                int versionAplicada = objetivo.estadoAplicado() == null
                                ? objetivo.estadoAnterior().version() + 1
                                : objetivo.estadoAplicado().version();
                if (actual.version() != versionAplicada) {
                        throw new ReversionProductoEnConflictoException();
                }

                Instant ahora = Instant.now();
                Producto restaurado = restaurar(objetivo.estadoAnterior(), actual.version(), ahora, autor);
                Producto resultadoEsperado = restaurar(
                                objetivo.estadoAnterior(), actual.version() + 1, ahora, autor);
                RespaldoProducto auditoria = new RespaldoProducto(
                                UUID.randomUUID().toString(),
                                productoId,
                                actual,
                                resultadoEsperado,
                                ahora,
                                autor,
                                TipoCambioProducto.REVERSION,
                                objetivo.id());

                respaldos.save(auditoria);
                try {
                        return productos.save(restaurado);
                } catch (RuntimeException fallo) {
                        respaldos.deleteById(auditoria.id());
                        throw fallo;
                }
        }

        private static Producto restaurar(
                        Producto origen,
                        int version,
                        Instant modificadoEn,
                        String autor) {
                return new Producto(
                                origen.id(), origen.nombre(), origen.imagen(), origen.descripcion(), origen.tipo(),
                                origen.tiraje(), origen.precioCreditos(), origen.precioMonedaReal(), origen.premium(),
                                origen.prototipo(), origen.heroe(), origen.costoPoder(), origen.multiplicadorNivel(),
                                origen.turnosCarga(), origen.turnosRecarga(), origen.efectoGeneral(),
                                origen.efectoPotenciado(), origen.defensa(), origen.parte(), origen.efecto(),
                                origen.poderDeAtaque(), origen.tasaDeCaida(), origen.estado(), version,
                                origen.creadoEn(), modificadoEn, origen.promocion(), origen.origen(),
                                origen.semillaVersion(), autor, origen.estadoAnteriorSuspension(),
                                origen.reservasRecientes());
        }
}
