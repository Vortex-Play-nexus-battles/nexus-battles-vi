package nexus.aplicacion;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import nexus.api.CambioProductoVista;
import nexus.dominio.EstadoProducto;
import nexus.dominio.Producto;
import nexus.dominio.RespaldoProducto;
import nexus.dominio.TipoCambioProducto;
import nexus.dominio.TipoProducto;
import nexus.persistencia.ProductoRepository;
import nexus.persistencia.RespaldoProductoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConsultarHistorialProductoServicioTest {

        @Test
        @DisplayName("solo el cambio que produjo la versión actual se ofrece para revertir")
        void marcaElRespaldoAplicable() {
                ProductoRepository productos = mock(ProductoRepository.class);
                RespaldoProductoRepository respaldos = mock(RespaldoProductoRepository.class);
                Producto actual = producto("Actual", 3);
                RespaldoProducto reciente = respaldo("r2", producto("Intermedio", 2), actual);
                RespaldoProducto antiguo = respaldo(
                                "r1", producto("Original", 1), producto("Intermedio", 2));
                when(productos.findById(actual.id())).thenReturn(Optional.of(actual));
                when(respaldos.findByProductoIdOrderByModificadoEnDesc(actual.id()))
                                .thenReturn(List.of(reciente, antiguo));

                List<CambioProductoVista> historial = new ConsultarHistorialProductoServicio(
                                productos, respaldos).consultar(actual.id());

                assertTrue(historial.get(0).revertible());
                assertFalse(historial.get(1).revertible());
        }

        private static RespaldoProducto respaldo(String id, Producto antes, Producto despues) {
                return new RespaldoProducto(
                                id,
                                antes.id(),
                                antes,
                                despues,
                                Instant.parse("2026-10-05T10:00:00Z"),
                                "admin",
                                TipoCambioProducto.MODIFICACION,
                                null);
        }

        private static Producto producto(String nombre, int version) {
                Instant fecha = Instant.parse("2026-10-05T10:00:00Z");
                return new Producto(
                                "550e8400-e29b-41d4-a716-446655440000", nombre, "productos/espada.webp",
                                "Arma", TipoProducto.ARMA, 10, 500, null, false, null, null, null, null,
                                null, null, null, null, null, null, null, 20, new BigDecimal("10"),
                                EstadoProducto.ACTIVO, version, fecha, fecha, null, null, null, "admin", null,
                                java.util.List.of());
        }
}
