package nexus.aplicacion;

import java.time.Instant;
import java.util.UUID;

import nexus.auditoria.RegistroDeOperaciones;
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
        private final RegistroDeOperaciones operaciones;

        public RevertirProductoServicio(
                        ProductoRepository productos,
                        RespaldoProductoRepository respaldos,
                        RegistroDeOperaciones operaciones) {
                this.productos = productos;
                this.respaldos = respaldos;
                this.operaciones = operaciones;
        }

        public Producto revertir(String productoId, String respaldoId, String autor) {
                return revertir(productoId, respaldoId, autor, null);
        }

        /** @param motivo por qué se revierte (SA-6); queda en la entrada de la reversión */
        public Producto revertir(String productoId, String respaldoId, String autor, String motivo) {
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
                                objetivo.id(),
                                motivoLimpio(motivo));

                respaldos.save(auditoria);
                Producto guardado;
                try {
                        guardado = productos.save(restaurado);
                } catch (RuntimeException fallo) {
                        respaldos.deleteById(auditoria.id());
                        throw fallo;
                }
                operaciones.deProducto(auditoria);
                return guardado;
        }

        static String motivoLimpio(String motivo) {
                return motivo == null || motivo.isBlank() ? null : motivo.strip();
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
