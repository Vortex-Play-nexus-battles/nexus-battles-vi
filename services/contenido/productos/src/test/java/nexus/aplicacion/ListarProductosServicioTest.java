package nexus.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import nexus.api.PaginaDeProductos;
import nexus.api.ProductoCreado;
import nexus.dominio.EstadoProducto;
import nexus.dominio.Producto;
import nexus.dominio.TipoProducto;
import nexus.persistencia.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Reglas del listado del catalogo (R16): que estados entran por omision, como
 * se combinan los filtros y que pagina y orden se le piden al repositorio.
 * La consulta real contra MongoDB la cubre ProductoRepositoryTest.
 */
class ListarProductosServicioTest {

        /** El orden se escribe aqui a mano, no con la constante: la prueba lo fija. */
        private static final Sort ORDEN_ESPERADO = Sort.by(
                Sort.Order.asc("creadoEn"),
                Sort.Order.asc("id"));

        private static final Pageable PRIMERA_PAGINA = PageRequest.of(0, 20, ORDEN_ESPERADO);

        private static final Set<EstadoProducto> ACTIVO_Y_UNICO =
                Set.of(EstadoProducto.ACTIVO, EstadoProducto.UNICO);

        private ProductoRepository repositorio;
        private ListarProductosServicio servicio;

        @BeforeEach
        void preparar() {
                repositorio = mock(ProductoRepository.class);
                servicio = new ListarProductosServicio(
                        repositorio,
                        Mappers.getMapper(ProductoMapper.class));
        }

        @Test
        @DisplayName("por omision lista solo ACTIVO y UNICO y responde el mismo detalle que GET /{id}")
        void porOmisionListaSoloActivosYUnicos() {
                when(repositorio.findByEstadoIn(any(), any()))
                        .thenReturn(new PageImpl<>(
                                List.of(
                                        producto("p-1", TipoProducto.ARMA, EstadoProducto.ACTIVO),
                                        producto("p-2", TipoProducto.HEROE, EstadoProducto.UNICO)),
                                PRIMERA_PAGINA,
                                2));

                PaginaDeProductos pagina = servicio.listar(0, 20, null, null);

                verify(repositorio).findByEstadoIn(ACTIVO_Y_UNICO, PRIMERA_PAGINA);
                verify(repositorio, never()).findByTipoAndEstadoIn(any(), any(), any());

                assertEquals(List.of("p-1", "p-2"), ids(pagina));
                ProductoCreado primero = pagina.content().get(0);
                assertEquals("Producto p-1", primero.nombre());
                assertEquals(TipoProducto.ARMA, primero.tipo());
                assertEquals(EstadoProducto.ACTIVO, primero.estado());
                assertEquals(EstadoProducto.UNICO, pagina.content().get(1).estado());
                assertEquals(0, pagina.page());
                assertEquals(20, pagina.size());
                assertEquals(2L, pagina.totalElements());
                assertEquals(1, pagina.totalPages());
        }

        @Test
        @DisplayName("SUSPENDIDO no esta entre los estados que se listan por omision")
        void suspendidoNuncaEntraPorOmision() {
                assertEquals(ACTIVO_Y_UNICO, ListarProductosServicio.ESTADOS_LISTABLES_POR_OMISION);
                assertTrue(ListarProductosServicio.ESTADOS_LISTABLES_POR_OMISION.stream()
                        .noneMatch(EstadoProducto.SUSPENDIDO::equals));
        }

        @Test
        @DisplayName("el filtro por tipo no saca del listado la regla de estados por omision")
        void filtraPorTipoDentroDeLosEstadosListables() {
                when(repositorio.findByTipoAndEstadoIn(any(), any(), any()))
                        .thenReturn(new PageImpl<>(
                                List.of(producto("p-1", TipoProducto.ARMA, EstadoProducto.ACTIVO)),
                                PRIMERA_PAGINA,
                                1));

                PaginaDeProductos pagina = servicio.listar(0, 20, TipoProducto.ARMA, null);

                verify(repositorio).findByTipoAndEstadoIn(
                        TipoProducto.ARMA,
                        ACTIVO_Y_UNICO,
                        PRIMERA_PAGINA);
                verify(repositorio, never()).findByEstadoIn(any(), any());
                assertEquals(List.of("p-1"), ids(pagina));
        }

        @Test
        @DisplayName("un estado explicito sustituye a los de por omision, incluido SUSPENDIDO")
        void estadoExplicitoSustituyeAlFiltroPorOmision() {
                when(repositorio.findByEstadoIn(any(), any()))
                        .thenReturn(new PageImpl<>(
                                List.of(producto("p-3", TipoProducto.ARMA, EstadoProducto.SUSPENDIDO)),
                                PRIMERA_PAGINA,
                                1));

                PaginaDeProductos pagina = servicio.listar(0, 20, null, EstadoProducto.SUSPENDIDO);

                verify(repositorio).findByEstadoIn(Set.of(EstadoProducto.SUSPENDIDO), PRIMERA_PAGINA);
                assertEquals(EstadoProducto.SUSPENDIDO, pagina.content().get(0).estado());
        }

        @Test
        @DisplayName("tipo y estado explicitos se combinan en la misma consulta")
        void combinaTipoYEstadoExplicitos() {
                when(repositorio.findByTipoAndEstadoIn(any(), any(), any()))
                        .thenReturn(new PageImpl<>(List.of(), PRIMERA_PAGINA, 0));

                servicio.listar(0, 20, TipoProducto.HEROE, EstadoProducto.UNICO);

                verify(repositorio).findByTipoAndEstadoIn(
                        TipoProducto.HEROE,
                        Set.of(EstadoProducto.UNICO),
                        PRIMERA_PAGINA);
        }

        @Test
        @DisplayName("pide la pagina y el tamano solicitados con el orden estable y devuelve sus metadatos")
        void pideLaPaginaConElOrdenEstable() {
                Pageable tercera = PageRequest.of(2, 5, ORDEN_ESPERADO);
                when(repositorio.findByEstadoIn(any(), any()))
                        .thenReturn(new PageImpl<>(
                                List.of(producto("p-11", TipoProducto.ITEM, EstadoProducto.ACTIVO)),
                                tercera,
                                11));

                PaginaDeProductos pagina = servicio.listar(2, 5, null, null);

                verify(repositorio).findByEstadoIn(ACTIVO_Y_UNICO, tercera);
                assertEquals(2, pagina.page());
                assertEquals(5, pagina.size());
                assertEquals(11L, pagina.totalElements());
                assertEquals(3, pagina.totalPages());
                assertEquals(List.of("p-11"), ids(pagina));
        }

        @Test
        @DisplayName("un catalogo sin productos listables devuelve una pagina vacia, no un error")
        void catalogoVacio() {
                when(repositorio.findByEstadoIn(any(), any()))
                        .thenReturn(new PageImpl<>(List.of(), PRIMERA_PAGINA, 0));

                PaginaDeProductos pagina = servicio.listar(0, 20, null, null);

                assertTrue(pagina.content().isEmpty());
                assertEquals(0L, pagina.totalElements());
                assertEquals(0, pagina.totalPages());
        }

        private static List<String> ids(PaginaDeProductos pagina) {
                return pagina.content().stream().map(ProductoCreado::id).toList();
        }

        private static Producto producto(String id, TipoProducto tipo, EstadoProducto estado) {
                Instant creadoEn = Instant.parse("2026-09-20T12:00:00Z");
                return new Producto(
                        id,
                        "Producto " + id,
                        "productos/" + id + ".webp",
                        "Producto para verificar el listado",
                        tipo,
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
                        new BigDecimal("12.5"),
                        estado,
                        1,
                        creadoEn,
                        creadoEn);
        }
}
