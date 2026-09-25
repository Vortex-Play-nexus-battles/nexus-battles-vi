package nexus.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import nexus.dominio.EstadoProducto;
import nexus.dominio.Producto;
import nexus.dominio.RespaldoProducto;
import nexus.dominio.TipoProducto;
import nexus.persistencia.ProductoRepository;
import nexus.persistencia.RespaldoProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = "KEYCLOAK_JWK_SET_URI=http://localhost/prueba/jwks")
@AutoConfigureMockMvc
class ModificarProductoApiTest {

        @Autowired
        private MockMvc mvc;

        @MockitoBean
        private JwtDecoder jwtDecoder;

        @MockitoBean
        private ProductoRepository productoRepository;

        @MockitoBean
        private RespaldoProductoRepository respaldoProductoRepository;

        @BeforeEach
        void simularPersistencia() {
                when(productoRepository.save(any(Producto.class)))
                        .thenAnswer(invocacion -> invocacion.getArgument(0, Producto.class));
                when(respaldoProductoRepository.save(any(RespaldoProducto.class)))
                        .thenAnswer(invocacion -> invocacion.getArgument(0, RespaldoProducto.class));
        }

        @Test
        @DisplayName("rechaza la modificacion cuando no se envia un token")
        void requiereAutenticacion() throws Exception {
                String id = UUID.randomUUID().toString();

                mvc.perform(patch("/api/v1/productos/" + id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nombre\": \"Nuevo nombre\"}"))
                        .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("rechaza un jugador autenticado sin permiso administrativo")
        void rechazaUsuarioSinPermiso() throws Exception {
                String id = UUID.randomUUID().toString();

                modificarComo("ROLE_JUGADOR", id, "{\"nombre\": \"Nuevo nombre\"}")
                        .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("modifica exitosamente un campo y devuelve el producto actualizado")
        void modificaExitosamente() throws Exception {
                Producto existente = productoArma();
                when(productoRepository.findById(existente.id()))
                        .thenReturn(Optional.of(existente));

                modificarComo(
                                "ROLE_ADMINISTRADOR",
                                existente.id(),
                                "{\"nombre\": \"Espada solar+1\"}")
                        .andExpect(status().isOk())
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.id").value(existente.id()))
                        .andExpect(jsonPath("$.nombre").value("Espada solar+1"))
                        .andExpect(jsonPath("$.version").value(existente.version() + 1));
        }

        @Test
        @DisplayName("responde 404 con Problem Details cuando el producto no existe")
        void modificaProductoInexistente() throws Exception {
                String id = UUID.randomUUID().toString();
                when(productoRepository.findById(id)).thenReturn(Optional.empty());

                modificarComo("ROLE_ADMINISTRADOR", id, "{\"nombre\": \"Nuevo nombre\"}")
                        .andExpect(status().isNotFound())
                        .andExpect(content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON))
                        .andExpect(jsonPath("$.type")
                                .value("urn:nexus:problema:producto-no-encontrado"));
        }

        @Test
        @DisplayName("rechaza un cuerpo vacio (ningun campo presente)")
        void rechazaCuerpoVacio() throws Exception {
                String id = UUID.randomUUID().toString();
                when(productoRepository.findById(id)).thenReturn(Optional.of(productoArma()));

                modificarComo("ROLE_ADMINISTRADOR", id, "{}")
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON))
                        .andExpect(jsonPath("$.type")
                                .value("urn:nexus:problema:solicitud-invalida"));
        }

        @Test
        @DisplayName("rechaza defensa sobre un producto HEROE con 400, de extremo a extremo por HTTP")
        void rechazaDefensaSobreHeroeExtremoAExtremo() throws Exception {
                Producto heroe = productoHeroe();
                when(productoRepository.findById(heroe.id())).thenReturn(Optional.of(heroe));

                modificarComo("ROLE_ADMINISTRADOR", heroe.id(), "{\"defensa\": 50}")
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON))
                        .andExpect(jsonPath("$.type")
                                .value("urn:nexus:problema:solicitud-invalida"));
        }

        private ResultActions modificarComo(String autoridad, String id, String cuerpo) throws Exception {
                return mvc.perform(patch("/api/v1/productos/" + id)
                        .with(jwt().authorities(new SimpleGrantedAuthority(autoridad)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo));
        }

        private static Producto productoArma() {
                Instant ahora = Instant.parse("2026-08-27T18:00:00Z");
                return new Producto(
                        UUID.randomUUID().toString(),            // id
                        "Espada solar",                          // nombre
                        "productos/espada-solar.webp",           // imagen
                        "Arma de prueba",                        // descripcion
                        TipoProducto.ARMA,                       // tipo
                        100,                                     // tiraje
                        500,                                     // precioCreditos
                        null,                                    // precioMonedaReal
                        false,                                   // premium
                        null,                                    // prototipo
                        null,                                    // heroe
                        null,                                    // costoPoder
                        null,                                    // multiplicadorNivel
                        null,                                    // turnosCarga
                        null,                                    // turnosRecarga
                        null,                                    // efectoGeneral
                        null,                                    // efectoPotenciado
                        null,                                    // defensa
                        null,                                    // parte
                        null,                                    // efecto
                        40,                                      // poderDeAtaque
                        new BigDecimal("12.5"),                  // tasaDeCaida
                        EstadoProducto.ACTIVO,                   // estado
                        1,                                       // version
                        ahora,                                   // creadoEn
                        ahora);                                  // modificadoEn
        }

        private static Producto productoHeroe() {
                Instant ahora = Instant.parse("2026-08-27T18:00:00Z");
                return new Producto(
                        UUID.randomUUID().toString(),            // id
                        "Heroe de prueba",                       // nombre
                        "productos/heroe-prueba.webp",           // imagen
                        "Heroe de prueba",                       // descripcion
                        TipoProducto.HEROE,                      // tipo
                        -1,                                      // tiraje
                        1000,                                    // precioCreditos
                        null,                                    // precioMonedaReal
                        false,                                   // premium
                        "Guerrero Tanque",                       // prototipo
                        null,                                    // heroe
                        null,                                    // costoPoder
                        null,                                    // multiplicadorNivel
                        null,                                    // turnosCarga
                        null,                                    // turnosRecarga
                        null,                                    // efectoGeneral
                        null,                                    // efectoPotenciado
                        null,                                    // defensa
                        null,                                    // parte
                        null,                                    // efecto
                        null,                                    // poderDeAtaque
                        null,                                    // tasaDeCaida
                        EstadoProducto.ACTIVO,                   // estado
                        1,                                       // version
                        ahora,                                   // creadoEn
                        ahora);                                  // modificadoEn
        }
}
