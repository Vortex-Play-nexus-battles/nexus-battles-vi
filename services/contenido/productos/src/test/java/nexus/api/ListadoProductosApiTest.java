package nexus.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import nexus.aplicacion.ListarProductosServicio;
import nexus.dominio.EstadoProducto;
import nexus.dominio.Producto;
import nexus.dominio.TipoProducto;
import nexus.persistencia.ProductoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /api/v1/productos} — listado publico y paginado del catalogo
 * (R16, contrato productos 1.2.0), sobre la cadena de seguridad real.
 *
 * <p>Misma configuracion de contexto que ProductosApiTest, para que Spring
 * reutilice el contexto en cache en vez de levantar otro.
 */
@SpringBootTest(properties = "KEYCLOAK_JWK_SET_URI=http://localhost/prueba/jwks")
@AutoConfigureMockMvc
class ListadoProductosApiTest {

        private static final String LISTADO = "/api/v1/productos";

        private static final Pageable PRIMERA_PAGINA =
                PageRequest.of(0, 20, ListarProductosServicio.ORDEN_DEL_LISTADO);

        private static final Set<EstadoProducto> ACTIVO_Y_UNICO =
                Set.of(EstadoProducto.ACTIVO, EstadoProducto.UNICO);

        @Autowired
        private MockMvc mvc;

        @MockitoBean
        private JwtDecoder jwtDecoder;

        @MockitoBean
        private ProductoRepository productoRepository;

        @Test
        @DisplayName("sin token lista el catalogo: 200 con solo ACTIVO y UNICO en la primera pagina de 20")
        void listaSinTokenSoloActivosYUnicos() throws Exception {
                when(productoRepository.findByEstadoIn(any(), any()))
                        .thenReturn(new PageImpl<>(
                                List.of(
                                        producto("p-1", TipoProducto.ARMA, EstadoProducto.ACTIVO),
                                        producto("p-2", TipoProducto.HEROE, EstadoProducto.UNICO)),
                                PRIMERA_PAGINA,
                                2));

                mvc.perform(get(LISTADO))
                        .andExpect(status().isOk())
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.content", hasSize(2)))
                        .andExpect(jsonPath("$.content[0].id").value("p-1"))
                        .andExpect(jsonPath("$.content[0].nombre").value("Producto p-1"))
                        .andExpect(jsonPath("$.content[0].tipo").value("ARMA"))
                        .andExpect(jsonPath("$.content[0].estado").value("ACTIVO"))
                        .andExpect(jsonPath("$.content[1].id").value("p-2"))
                        .andExpect(jsonPath("$.content[1].estado").value("UNICO"))
                        .andExpect(jsonPath("$.page").value(0))
                        .andExpect(jsonPath("$.size").value(20))
                        .andExpect(jsonPath("$.totalElements").value(2))
                        .andExpect(jsonPath("$.totalPages").value(1));

                verify(productoRepository).findByEstadoIn(ACTIVO_Y_UNICO, PRIMERA_PAGINA);
                verify(productoRepository, never()).findByTipoAndEstadoIn(any(), any(), any());
        }

        @Test
        @DisplayName("filtra por tipo sin salir de los estados que se listan por omision")
        void filtraPorTipo() throws Exception {
                when(productoRepository.findByTipoAndEstadoIn(any(), any(), any()))
                        .thenReturn(new PageImpl<>(
                                List.of(producto("p-1", TipoProducto.ARMA, EstadoProducto.ACTIVO)),
                                PRIMERA_PAGINA,
                                1));

                mvc.perform(get(LISTADO).param("tipo", "ARMA"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content", hasSize(1)))
                        .andExpect(jsonPath("$.content[0].tipo").value("ARMA"))
                        .andExpect(jsonPath("$.totalElements").value(1));

                verify(productoRepository).findByTipoAndEstadoIn(
                        TipoProducto.ARMA,
                        ACTIVO_Y_UNICO,
                        PRIMERA_PAGINA);
        }

        @Test
        @DisplayName("pagina con page y size y devuelve los metadatos de esa pagina")
        void paginaConPageYSize() throws Exception {
                Pageable segunda = PageRequest.of(1, 2, ListarProductosServicio.ORDEN_DEL_LISTADO);
                when(productoRepository.findByEstadoIn(any(), any()))
                        .thenReturn(new PageImpl<>(
                                List.of(
                                        producto("p-3", TipoProducto.ITEM, EstadoProducto.ACTIVO),
                                        producto("p-4", TipoProducto.EPICA, EstadoProducto.UNICO)),
                                segunda,
                                5));

                mvc.perform(get(LISTADO).param("page", "1").param("size", "2"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content", hasSize(2)))
                        .andExpect(jsonPath("$.content[0].id").value("p-3"))
                        .andExpect(jsonPath("$.page").value(1))
                        .andExpect(jsonPath("$.size").value(2))
                        .andExpect(jsonPath("$.totalElements").value(5))
                        .andExpect(jsonPath("$.totalPages").value(3));

                verify(productoRepository).findByEstadoIn(ACTIVO_Y_UNICO, segunda);
        }

        @Test
        @DisplayName("con estado explicito lista solo ese estado, incluido SUSPENDIDO")
        void estadoExplicito() throws Exception {
                when(productoRepository.findByEstadoIn(any(), any()))
                        .thenReturn(new PageImpl<>(
                                List.of(producto("p-9", TipoProducto.ARMA, EstadoProducto.SUSPENDIDO)),
                                PRIMERA_PAGINA,
                                1));

                mvc.perform(get(LISTADO).param("estado", "SUSPENDIDO"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content[0].estado").value("SUSPENDIDO"));

                verify(productoRepository).findByEstadoIn(
                        Set.of(EstadoProducto.SUSPENDIDO),
                        PRIMERA_PAGINA);
        }

        @ParameterizedTest(name = "size={0} responde 400 con Problem Details")
        @ValueSource(strings = {"0", "51", "-1"})
        void rechazaSizeFueraDeRango(String size) throws Exception {
                mvc.perform(get(LISTADO).param("size", size))
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON))
                        .andExpect(jsonPath("$.type")
                                .value("urn:nexus:problema:solicitud-invalida"))
                        .andExpect(jsonPath("$.title").value("Solicitud inválida"))
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.detail")
                                .value(containsString("size debe estar entre 1 y 50")))
                        .andExpect(jsonPath("$.instance").value(LISTADO));

                verifyNoInteractions(productoRepository);
        }

        @Test
        @DisplayName("un page negativo responde 400 con Problem Details")
        void rechazaPageNegativo() throws Exception {
                mvc.perform(get(LISTADO).param("page", "-1"))
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON))
                        .andExpect(jsonPath("$.type")
                                .value("urn:nexus:problema:solicitud-invalida"))
                        .andExpect(jsonPath("$.detail")
                                .value(containsString("page debe ser mayor o igual que 0")));

                verifyNoInteractions(productoRepository);
        }

        @Test
        @DisplayName("un tipo fuera de la enumeracion responde 400 y nombra los valores admitidos")
        void rechazaTipoNoPermitido() throws Exception {
                mvc.perform(get(LISTADO).param("tipo", "POCION"))
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON))
                        .andExpect(jsonPath("$.type")
                                .value("urn:nexus:problema:solicitud-invalida"))
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.detail").value(containsString("tipo")))
                        .andExpect(jsonPath("$.detail").value(containsString("HEROE")))
                        .andExpect(jsonPath("$.instance").value(LISTADO));

                verifyNoInteractions(productoRepository);
        }

        @Test
        @DisplayName("un size que no es un numero responde 400, no 500")
        void rechazaSizeNoNumerico() throws Exception {
                mvc.perform(get(LISTADO).param("size", "muchos"))
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON))
                        .andExpect(jsonPath("$.type")
                                .value("urn:nexus:problema:solicitud-invalida"))
                        .andExpect(jsonPath("$.detail").value(containsString("size")));

                verifyNoInteractions(productoRepository);
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
