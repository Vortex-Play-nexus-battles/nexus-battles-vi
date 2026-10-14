package nexus.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import nexus.api.PlantillaDisenoProducto;
import nexus.api.SolicitudModificarProducto;
import nexus.dominio.EstadoProducto;
import nexus.dominio.Producto;
import nexus.dominio.RespaldoProducto;
import nexus.dominio.TipoCambioProducto;
import nexus.dominio.TipoProducto;
import nexus.persistencia.ProductoRepository;
import nexus.persistencia.RespaldoProductoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GestionarVersionesDisenoServicioTest {

        private static final String PRODUCTO_ID = "550e8400-e29b-41d4-a716-446655440000";
        private static final Instant AHORA = Instant.parse("2026-10-11T15:00:00Z");

        @Test
        @DisplayName("lista la versión actual y las versiones anteriores de diseño con fecha y autor")
        void listaVersionesDelDiseno() {
                ProductoRepository productos = mock(ProductoRepository.class);
                RespaldoProductoRepository respaldos = mock(RespaldoProductoRepository.class);
                Producto anterior = producto(2, "anterior.webp", "Diseño anterior", "admin-anterior");
                Producto actual = producto(3, "actual.webp", "Diseño actual", "admin-actual");
                RespaldoProducto respaldo = new RespaldoProducto(
                                "respaldo-1", PRODUCTO_ID, anterior, actual, AHORA,
                                "admin-actual", TipoCambioProducto.MODIFICACION, null);

                when(productos.findById(PRODUCTO_ID)).thenReturn(Optional.of(actual));
                when(respaldos.findByProductoIdOrderByModificadoEnDesc(PRODUCTO_ID))
                                .thenReturn(List.of(respaldo));

                GestionarVersionesDisenoServicio servicio = servicio(productos, respaldos, mock(ModificarProductoServicio.class));

                var versiones = servicio.consultar(PRODUCTO_ID);

                assertEquals(List.of(3, 2), versiones.stream().map(v -> v.version()).toList());
                assertEquals("admin-actual", versiones.get(0).autor());
                assertTrue(versiones.get(0).actual());
                assertEquals("admin-anterior", versiones.get(1).autor());
        }

        @Test
        @DisplayName("exporta una plantilla versionada sin datos económicos ni identidad del destino")
        void exportaPlantilla() {
                ProductoRepository productos = mock(ProductoRepository.class);
                Producto actual = producto(3, "actual.webp", "Diseño actual", "admin-actual");
                when(productos.findById(PRODUCTO_ID)).thenReturn(Optional.of(actual));

                GestionarVersionesDisenoServicio servicio = servicio(
                                productos, mock(RespaldoProductoRepository.class), mock(ModificarProductoServicio.class));

                PlantillaDisenoProducto plantilla = servicio.exportar(PRODUCTO_ID);

                assertEquals(PlantillaDisenoProducto.FORMATO, plantilla.formato());
                assertEquals(PlantillaDisenoProducto.VERSION_FORMATO, plantilla.versionFormato());
                assertEquals("actual.webp", plantilla.imagen());
                assertEquals("Diseño actual", plantilla.descripcion());
                assertEquals(AHORA, plantilla.exportadaEn());
        }

        @Test
        @DisplayName("importa solo imagen y descripción y registra el cambio como importación de diseño")
        void importaPlantillaSinCambiarElRestoDelProducto() {
                ProductoRepository productos = mock(ProductoRepository.class);
                ModificarProductoServicio modificar = mock(ModificarProductoServicio.class);
                Producto actualizado = producto(4, "importada.webp", "Diseño importado", "admin-importador");
                PlantillaDisenoProducto plantilla = new PlantillaDisenoProducto(
                                PlantillaDisenoProducto.FORMATO,
                                PlantillaDisenoProducto.VERSION_FORMATO,
                                "producto-origen",
                                7,
                                "importada.webp",
                                "Diseño importado",
                                AHORA);
                when(modificar.modificar(
                                eq(PRODUCTO_ID),
                                org.mockito.ArgumentMatchers.any(SolicitudModificarProducto.class),
                                eq("admin-importador"),
                                eq(TipoCambioProducto.IMPORTACION_DISENO)))
                                .thenReturn(actualizado);

                GestionarVersionesDisenoServicio servicio = servicio(
                                productos, mock(RespaldoProductoRepository.class), modificar);

                Producto resultado = servicio.importar(PRODUCTO_ID, plantilla, "admin-importador");

                ArgumentCaptor<SolicitudModificarProducto> cambios = ArgumentCaptor.forClass(SolicitudModificarProducto.class);
                verify(modificar).modificar(
                                eq(PRODUCTO_ID),
                                cambios.capture(),
                                eq("admin-importador"),
                                eq(TipoCambioProducto.IMPORTACION_DISENO));
                assertEquals("importada.webp", cambios.getValue().imagen());
                assertEquals("Diseño importado", cambios.getValue().descripcion());
                assertEquals(null, cambios.getValue().nombre());
                assertEquals(null, cambios.getValue().precioCreditos());
                assertEquals(actualizado, resultado);
        }

        private static GestionarVersionesDisenoServicio servicio(
                        ProductoRepository productos,
                        RespaldoProductoRepository respaldos,
                        ModificarProductoServicio modificar) {
                return new GestionarVersionesDisenoServicio(
                                productos,
                                respaldos,
                                modificar,
                                Clock.fixed(AHORA, ZoneOffset.UTC));
        }

        private static Producto producto(int version, String imagen, String descripcion, String autor) {
                Instant fecha = AHORA.minusSeconds((4L - version) * 3600L);
                return new Producto(
                                PRODUCTO_ID, "Espada solar", imagen, descripcion, TipoProducto.ARMA,
                                10, 500, null, false, null, null, null, null, null, null, null, null,
                                null, null, null, 20, new BigDecimal("10"), EstadoProducto.ACTIVO,
                                version, fecha, fecha, null, null, null, autor, null, List.of());
        }
}
