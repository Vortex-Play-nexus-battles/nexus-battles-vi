package nexus.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

import nexus.api.SolicitudCrearProducto;
import nexus.dominio.EstadoProducto;
import nexus.dominio.Producto;
import nexus.dominio.TipoProducto;
import nexus.persistencia.ProductoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class CrearProductoServicioTest {

        @Test
        @DisplayName("crea un producto activo con UUID, version, fechas y lo guarda")
        void creaYGuardaProducto() {
                ProductoRepository repositorio = mock(ProductoRepository.class);

                when(repositorio.save(any(Producto.class)))
                        .thenAnswer(invocacion ->
                                invocacion.getArgument(0, Producto.class));

                CrearProductoServicio servicio =
                        new CrearProductoServicio(
                                repositorio,
                                Mappers.getMapper(ProductoMapper.class));

                SolicitudCrearProducto solicitud = new SolicitudCrearProducto(
                        "Espada solar",
                        "productos/espada-solar.webp",
                        "Arma creada para probar la persistencia",
                        TipoProducto.ARMA,
                        100,
                        500,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        40,
                        new BigDecimal("12.5"));

                Producto producto = servicio.crear(solicitud);
                UUID identificador = UUID.fromString(producto.id());

                assertEquals(4, identificador.version());
                assertEquals("Espada solar", producto.nombre());
                assertEquals(TipoProducto.ARMA, producto.tipo());
                assertEquals(100, producto.tiraje());
                assertEquals(500, producto.precioCreditos());
                assertFalse(producto.premium());
                assertEquals(40, producto.poderDeAtaque());
                assertEquals(
                        new BigDecimal("12.5"),
                        producto.tasaDeCaida());
                assertEquals(EstadoProducto.ACTIVO, producto.estado());
                assertEquals(1, producto.version());
                assertNotNull(producto.creadoEn());
                assertNotNull(producto.modificadoEn());
                assertEquals(producto.creadoEn(), producto.modificadoEn());
                assertTrue(producto.creadoEn().toEpochMilli() > 0);

                verify(repositorio).save(producto);
        }
}
