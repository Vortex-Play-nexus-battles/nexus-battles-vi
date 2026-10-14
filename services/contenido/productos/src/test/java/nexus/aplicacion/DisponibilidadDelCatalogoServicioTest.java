package nexus.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import nexus.auditoria.RegistroDeOperaciones;
import nexus.dominio.EstadoProducto;
import nexus.dominio.Producto;
import nexus.dominio.RespaldoProducto;
import nexus.dominio.TipoCambioProducto;
import nexus.dominio.TipoProducto;
import nexus.persistencia.ProductoRepository;
import nexus.persistencia.RespaldoProductoRepository;
import nexus.productos.dominio.CatalogoProductos;
import nexus.productos.dominio.DisponibilidadProducto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DisponibilidadDelCatalogoServicioTest {

        @Test
        @DisplayName("suspender registra autor, estado anterior y estado aplicado")
        void auditaLaSuspension() {
                CatalogoProductos catalogo = mock(CatalogoProductos.class);
                ProductoRepository productos = mock(ProductoRepository.class);
                RespaldoProductoRepository respaldos = mock(RespaldoProductoRepository.class);
                Producto activo = producto(EstadoProducto.ACTIVO, null, 3);
                when(productos.findById(activo.id())).thenReturn(Optional.of(activo));
                when(respaldos.save(any(RespaldoProducto.class)))
                                .thenAnswer(invocacion -> invocacion.getArgument(0, RespaldoProducto.class));
                when(catalogo.suspender(activo.id()))
                                .thenReturn(DisponibilidadProducto.desde(
                                                producto(EstadoProducto.SUSPENDIDO, EstadoProducto.ACTIVO, 4),
                                                EstadoProducto.ACTIVO));

                DisponibilidadProducto resultado = new DisponibilidadDelCatalogoServicio(
                                catalogo, productos, respaldos, mock(RegistroDeOperaciones.class)).suspender(activo.id(), "admin-uid");

                assertEquals(EstadoProducto.SUSPENDIDO, resultado.estado());
                ArgumentCaptor<RespaldoProducto> captor = ArgumentCaptor.forClass(RespaldoProducto.class);
                verify(respaldos).save(captor.capture());
                assertEquals(TipoCambioProducto.SUSPENSION, captor.getValue().tipoCambio());
                assertEquals("admin-uid", captor.getValue().autor());
                assertEquals(EstadoProducto.ACTIVO, captor.getValue().estadoAnterior().estado());
                assertEquals(EstadoProducto.SUSPENDIDO, captor.getValue().estadoAplicado().estado());
        }

        @Test
        @DisplayName("SA-6: el motivo de suspender y de reactivar queda en su entrada del historial, sin espacios de más")
        void guardaElMotivo() {
                CatalogoProductos catalogo = mock(CatalogoProductos.class);
                ProductoRepository productos = mock(ProductoRepository.class);
                RespaldoProductoRepository respaldos = mock(RespaldoProductoRepository.class);
                Producto activo = producto(EstadoProducto.ACTIVO, null, 3);
                Producto suspendido = producto(EstadoProducto.SUSPENDIDO, EstadoProducto.ACTIVO, 4);
                when(respaldos.save(any(RespaldoProducto.class)))
                                .thenAnswer(invocacion -> invocacion.getArgument(0, RespaldoProducto.class));
                when(catalogo.suspender(activo.id()))
                                .thenReturn(DisponibilidadProducto.desde(suspendido, EstadoProducto.ACTIVO));
                when(catalogo.reactivar(activo.id()))
                                .thenReturn(DisponibilidadProducto.desde(producto(EstadoProducto.ACTIVO, null, 5)));
                DisponibilidadDelCatalogoServicio servicio = new DisponibilidadDelCatalogoServicio(
                                catalogo, productos, respaldos, mock(RegistroDeOperaciones.class));

                when(productos.findById(activo.id())).thenReturn(Optional.of(activo));
                servicio.suspender(activo.id(), "admin-uid", "  Error de balance en su daño  ");
                when(productos.findById(activo.id())).thenReturn(Optional.of(suspendido));
                servicio.reactivar(activo.id(), "admin-uid", "   ");

                ArgumentCaptor<RespaldoProducto> captor = ArgumentCaptor.forClass(RespaldoProducto.class);
                verify(respaldos, org.mockito.Mockito.times(2)).save(captor.capture());
                assertEquals(TipoCambioProducto.SUSPENSION, captor.getAllValues().get(0).tipoCambio());
                assertEquals("Error de balance en su daño", captor.getAllValues().get(0).motivo());
                assertEquals(TipoCambioProducto.REACTIVACION, captor.getAllValues().get(1).tipoCambio());
                assertEquals(null, captor.getAllValues().get(1).motivo());
        }

        @Test
        @DisplayName("repetir una suspensión no inventa otra entrada de auditoría")
        void suspensionIdempotenteNoDuplicaAuditoria() {
                CatalogoProductos catalogo = mock(CatalogoProductos.class);
                ProductoRepository productos = mock(ProductoRepository.class);
                RespaldoProductoRepository respaldos = mock(RespaldoProductoRepository.class);
                Producto suspendido = producto(EstadoProducto.SUSPENDIDO, EstadoProducto.ACTIVO, 4);
                when(productos.findById(suspendido.id())).thenReturn(Optional.of(suspendido));
                when(catalogo.suspender(suspendido.id()))
                                .thenReturn(DisponibilidadProducto.desde(suspendido, EstadoProducto.ACTIVO));

                new DisponibilidadDelCatalogoServicio(catalogo, productos, respaldos, mock(RegistroDeOperaciones.class))
                                .suspender(suspendido.id(), "admin-uid");

                verify(respaldos, never()).save(any());
        }

        private static Producto producto(
                        EstadoProducto estado,
                        EstadoProducto estadoAnterior,
                        int version) {
                Instant fecha = Instant.parse("2026-10-05T10:00:00Z");
                return new Producto(
                                "550e8400-e29b-41d4-a716-446655440000", "Espada", "productos/espada.webp",
                                "Arma", TipoProducto.ARMA, 10, 500, null, false, null, null, null, null,
                                null, null, null, null, null, null, null, 20, new BigDecimal("10"), estado,
                                version, fecha, fecha, null, null, null, "admin", estadoAnterior, List.of());
        }
}
