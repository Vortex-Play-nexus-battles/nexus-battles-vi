package nexus.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import nexus.dominio.EstadoProducto;
import nexus.dominio.Producto;
import nexus.dominio.RespaldoProducto;
import nexus.dominio.RespaldoProductoNoEncontradoException;
import nexus.dominio.ReversionProductoEnConflictoException;
import nexus.dominio.TipoCambioProducto;
import nexus.dominio.TipoProducto;
import nexus.persistencia.ProductoRepository;
import nexus.persistencia.RespaldoProductoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RevertirProductoServicioTest {

        private static final String PRODUCTO_ID = "550e8400-e29b-41d4-a716-446655440000";
        private static final String RESPALDO_ID = "respaldo-1";

        @Test
        @DisplayName("restaura el estado respaldado y registra la reversión con su autor")
        void revierteAlEstadoAnterior() {
                ProductoRepository productos = mock(ProductoRepository.class);
                RespaldoProductoRepository respaldos = mock(RespaldoProductoRepository.class);
                Producto anterior = producto("Espada solar", 1);
                Producto aplicado = producto("Espada solar +1", 2);
                RespaldoProducto respaldo = new RespaldoProducto(
                                RESPALDO_ID,
                                PRODUCTO_ID,
                                anterior,
                                aplicado,
                                Instant.parse("2026-10-05T10:00:00Z"),
                                "admin-original",
                                TipoCambioProducto.MODIFICACION,
                                null);
                when(productos.findById(PRODUCTO_ID)).thenReturn(Optional.of(aplicado));
                when(respaldos.findById(RESPALDO_ID)).thenReturn(Optional.of(respaldo));
                when(productos.save(any(Producto.class))).thenAnswer(invocacion -> {
                        Producto guardado = invocacion.getArgument(0, Producto.class);
                        return conVersion(guardado, guardado.version() + 1);
                });

                Producto resultado = new RevertirProductoServicio(productos, respaldos)
                                .revertir(PRODUCTO_ID, RESPALDO_ID, "admin-revisor");

                assertEquals("Espada solar", resultado.nombre());
                assertEquals(3, resultado.version());
                assertEquals("admin-revisor", resultado.modificadoPor());

                ArgumentCaptor<RespaldoProducto> auditoria = ArgumentCaptor.forClass(RespaldoProducto.class);
                verify(respaldos).save(auditoria.capture());
                assertEquals(TipoCambioProducto.REVERSION, auditoria.getValue().tipoCambio());
                assertEquals(RESPALDO_ID, auditoria.getValue().reversionDe());
                assertEquals("Espada solar +1", auditoria.getValue().estadoAnterior().nombre());
                assertEquals("Espada solar", auditoria.getValue().estadoAplicado().nombre());
        }

        @Test
        @DisplayName("rechaza un respaldo anterior cuando el producto volvió a cambiar")
        void noPisaCambiosPosteriores() {
                ProductoRepository productos = mock(ProductoRepository.class);
                RespaldoProductoRepository respaldos = mock(RespaldoProductoRepository.class);
                RespaldoProducto respaldo = new RespaldoProducto(
                                RESPALDO_ID,
                                PRODUCTO_ID,
                                producto("Original", 1),
                                producto("Cambio", 2),
                                Instant.now(),
                                "admin",
                                TipoCambioProducto.MODIFICACION,
                                null);
                when(productos.findById(PRODUCTO_ID)).thenReturn(Optional.of(producto("Otro cambio", 3)));
                when(respaldos.findById(RESPALDO_ID)).thenReturn(Optional.of(respaldo));

                RevertirProductoServicio servicio = new RevertirProductoServicio(productos, respaldos);

                assertThrows(
                                ReversionProductoEnConflictoException.class,
                                () -> servicio.revertir(PRODUCTO_ID, RESPALDO_ID, "admin"));
        }

        @Test
        @DisplayName("no permite usar el respaldo de otro producto")
        void exigeRespaldoDelProducto() {
                ProductoRepository productos = mock(ProductoRepository.class);
                RespaldoProductoRepository respaldos = mock(RespaldoProductoRepository.class);
                when(productos.findById(PRODUCTO_ID)).thenReturn(Optional.of(producto("Actual", 2)));
                when(respaldos.findById(RESPALDO_ID)).thenReturn(Optional.of(new RespaldoProducto(
                                RESPALDO_ID,
                                "otro-producto",
                                producto("Anterior", 1),
                                Instant.now(),
                                "admin")));

                RevertirProductoServicio servicio = new RevertirProductoServicio(productos, respaldos);

                assertThrows(
                                RespaldoProductoNoEncontradoException.class,
                                () -> servicio.revertir(PRODUCTO_ID, RESPALDO_ID, "admin"));
        }

        private static Producto producto(String nombre, int version) {
                Instant fecha = Instant.parse("2026-10-05T10:00:00Z");
                return new Producto(
                                PRODUCTO_ID, nombre, "productos/espada.webp", "Arma", TipoProducto.ARMA,
                                10, 500, null, false, null, null, null, null, null, null, null, null,
                                null, null, null, 20, new BigDecimal("10"), EstadoProducto.ACTIVO,
                                version, fecha, fecha, null, null, null, "admin", null, java.util.List.of());
        }

        private static Producto conVersion(Producto producto, int version) {
                return new Producto(
                                producto.id(), producto.nombre(), producto.imagen(), producto.descripcion(),
                                producto.tipo(), producto.tiraje(), producto.precioCreditos(),
                                producto.precioMonedaReal(), producto.premium(), producto.prototipo(),
                                producto.heroe(), producto.costoPoder(), producto.multiplicadorNivel(),
                                producto.turnosCarga(), producto.turnosRecarga(), producto.efectoGeneral(),
                                producto.efectoPotenciado(), producto.defensa(), producto.parte(), producto.efecto(),
                                producto.poderDeAtaque(), producto.tasaDeCaida(), producto.estado(), version,
                                producto.creadoEn(), producto.modificadoEn(), producto.promocion(), producto.origen(),
                                producto.semillaVersion(), producto.modificadoPor(),
                                producto.estadoAnteriorSuspension(), producto.reservasRecientes());
        }
}
