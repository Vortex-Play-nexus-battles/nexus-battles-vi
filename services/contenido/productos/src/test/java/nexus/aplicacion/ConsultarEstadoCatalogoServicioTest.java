package nexus.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import nexus.api.ResumenCatalogo;
import nexus.dominio.EstadoProducto;
import nexus.dominio.TipoProducto;
import nexus.persistencia.ProductoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConsultarEstadoCatalogoServicioTest {

        @Test
        @DisplayName("devuelve el total y todas las categorias y estados")
        void devuelveResumenCompleto() {
                ProductoRepository repositorio = mock(ProductoRepository.class);
                when(repositorio.count()).thenReturn(6L);
                when(repositorio.countByTipo(TipoProducto.HEROE)).thenReturn(1L);
                when(repositorio.countByTipo(TipoProducto.ARMA)).thenReturn(2L);
                when(repositorio.countByEstado(EstadoProducto.ACTIVO)).thenReturn(5L);
                when(repositorio.countByEstado(EstadoProducto.SUSPENDIDO))
                        .thenReturn(1L);

                ResumenCatalogo resumen =
                        new ConsultarEstadoCatalogoServicio(repositorio).consultar();

                assertEquals(6L, resumen.total());
                assertEquals(1L, resumen.porTipo().get(TipoProducto.HEROE));
                assertEquals(2L, resumen.porTipo().get(TipoProducto.ARMA));
                assertEquals(0L, resumen.porTipo().get(TipoProducto.HABILIDAD));
                assertEquals(5L, resumen.porEstado().get(EstadoProducto.ACTIVO));
                assertEquals(1L, resumen.porEstado().get(EstadoProducto.SUSPENDIDO));
                assertEquals(0L, resumen.porEstado().get(EstadoProducto.UNICO));
        }

        @Test
        @DisplayName("refleja los cambios del catalogo en cada consulta")
        void reflejaCambios() {
                ProductoRepository repositorio = mock(ProductoRepository.class);
                when(repositorio.count()).thenReturn(1L, 2L);
                when(repositorio.countByTipo(TipoProducto.ARMA)).thenReturn(1L, 2L);
                when(repositorio.countByEstado(EstadoProducto.ACTIVO))
                        .thenReturn(1L, 2L);
                ConsultarEstadoCatalogoServicio servicio =
                        new ConsultarEstadoCatalogoServicio(repositorio);

                ResumenCatalogo inicial = servicio.consultar();
                ResumenCatalogo actualizado = servicio.consultar();

                assertNotEquals(inicial, actualizado);
                assertEquals(1L, inicial.total());
                assertEquals(2L, actualizado.total());
                assertEquals(1L, inicial.porTipo().get(TipoProducto.ARMA));
                assertEquals(2L, actualizado.porTipo().get(TipoProducto.ARMA));
                assertEquals(1L, inicial.porEstado().get(EstadoProducto.ACTIVO));
                assertEquals(2L, actualizado.porEstado().get(EstadoProducto.ACTIVO));
        }
}
