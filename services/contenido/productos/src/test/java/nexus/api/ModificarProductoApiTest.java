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
                // Como el guardado real con @Version (B4): devuelve la version siguiente.
                when(productoRepository.save(any(Producto.class)))
                        .thenAnswer(invocacion -> versionSiguiente(invocacion.getArgument(0, Producto.class)));
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

        // --- B4 ---

        @Test
        @DisplayName("B4: si otro escribio el producto mientras se editaba, 409 con Problem Details")
        void conflictoDeVersionEs409() throws Exception {
                Producto existente = productoArma();
                when(productoRepository.findById(existente.id())).thenReturn(Optional.of(existente));
                when(productoRepository.save(any(Producto.class)))
                        .thenThrow(new org.springframework.dao.OptimisticLockingFailureException("otra version"));

                modificarComo("ROLE_ADMINISTRADOR", existente.id(), "{\"nombre\": \"Espada solar+1\"}")
                        .andExpect(status().isConflict())
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                        .andExpect(jsonPath("$.type").value("urn:nexus:problema:conflicto-de-version"));
        }

        @Test
        @DisplayName("B4: el autor del cambio es el uid del token y queda en el producto y en el respaldo")
        void elAutorEsElDelToken() throws Exception {
                Producto existente = productoArma();
                when(productoRepository.findById(existente.id())).thenReturn(Optional.of(existente));

                mvc.perform(patch("/api/v1/productos/" + existente.id())
                                .with(jwt().jwt(token -> token.subject("lyra").claim("uid", "uid-de-lyra"))
                                        .authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nombre\": \"Espada solar+1\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.modificadoPor").exists());

                org.mockito.ArgumentCaptor<RespaldoProducto> respaldo =
                        org.mockito.ArgumentCaptor.forClass(RespaldoProducto.class);
                org.mockito.Mockito.verify(respaldoProductoRepository).save(respaldo.capture());
                org.junit.jupiter.api.Assertions.assertNotNull(respaldo.getValue().autor());
        }

        @Test
        @DisplayName("B4: una promocion con porcentaje fuera de 1..90 o fechas al reves se rechaza con 400")
        void promocionInvalida() throws Exception {
                Producto existente = productoArma();
                when(productoRepository.findById(existente.id())).thenReturn(Optional.of(existente));

                modificarComo("ROLE_ADMINISTRADOR", existente.id(), """
                                {"promocion": {"porcentaje": 95, "desde": "2026-10-01T00:00:00Z", "hasta": "2026-10-08T00:00:00Z"}}
                                """)
                        .andExpect(status().isBadRequest());
                modificarComo("ROLE_ADMINISTRADOR", existente.id(), """
                                {"promocion": {"porcentaje": 20, "desde": "2026-10-08T00:00:00Z", "hasta": "2026-10-01T00:00:00Z"}}
                                """)
                        .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("B4: una promocion valida se guarda y la respuesta dice si esta vigente")
        void promocionValida() throws Exception {
                Producto existente = productoArma();
                when(productoRepository.findById(existente.id())).thenReturn(Optional.of(existente));

                modificarComo("ROLE_ADMINISTRADOR", existente.id(), """
                                {"promocion": {"porcentaje": 25, "desde": "2020-01-01T00:00:00Z", "hasta": "2999-01-01T00:00:00Z"}}
                                """)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.promocion.porcentaje").value(25))
                        .andExpect(jsonPath("$.promocion.vigente").value(true));
        }

        // RG-085 / RF-MOT-36: la unica fuente de epicas es derrotar al Master, asi
        // que la administracion tampoco puede ponerles precio al modificarlas.
        @Test
        @DisplayName("RG-085: ponerle precio en creditos a una epica se rechaza con 400 y no se guarda nada")
        void rechazaPrecioEnCreditosSobreEpica() throws Exception {
                Producto epica = productoEpica(0, BigDecimal.ZERO);
                when(productoRepository.findById(epica.id())).thenReturn(Optional.of(epica));

                modificarComo("ROLE_ADMINISTRADOR", epica.id(), "{\"precioCreditos\": 500}")
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                        .andExpect(jsonPath("$.type").value("urn:nexus:problema:solicitud-invalida"))
                        .andExpect(jsonPath("$.detail")
                                .value(org.hamcrest.Matchers.containsString("derrotando al M\u00e1ster")));

                org.mockito.Mockito.verify(productoRepository, org.mockito.Mockito.never()).save(any(Producto.class));
                org.mockito.Mockito.verifyNoInteractions(respaldoProductoRepository);
        }

        @Test
        @DisplayName("RG-085: ponerle precio en moneda real a una epica se rechaza con 400")
        void rechazaPrecioEnMonedaRealSobreEpica() throws Exception {
                Producto epica = productoEpica(0, BigDecimal.ZERO);
                when(productoRepository.findById(epica.id())).thenReturn(Optional.of(epica));

                modificarComo("ROLE_ADMINISTRADOR", epica.id(), "{\"precioMonedaReal\": 10000}")
                        .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("RG-085: hacer premium a una epica se rechaza con 400")
        void rechazaPremiumSobreEpica() throws Exception {
                Producto epica = productoEpica(0, BigDecimal.ZERO);
                when(productoRepository.findById(epica.id())).thenReturn(Optional.of(epica));

                modificarComo("ROLE_ADMINISTRADOR", epica.id(), "{\"premium\": true}")
                        .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("RG-085: una epica sin precio se sigue pudiendo editar (nombre) sin que la regla estorbe")
        void editarEpicaSinPrecioSigueFuncionando() throws Exception {
                Producto epica = productoEpica(0, BigDecimal.ZERO);
                when(productoRepository.findById(epica.id())).thenReturn(Optional.of(epica));

                modificarComo("ROLE_ADMINISTRADOR", epica.id(), "{\"nombre\": \"Epica renombrada\"}")
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.nombre").value("Epica renombrada"));
        }

        private ResultActions modificarComo(String autoridad, String id, String cuerpo) throws Exception {
                return mvc.perform(patch("/api/v1/productos/" + id)
                        .with(jwt().authorities(new SimpleGrantedAuthority(autoridad)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo));
        }

        private static Producto versionSiguiente(Producto p) {
                return new Producto(p.id(), p.nombre(), p.imagen(), p.descripcion(), p.tipo(), p.tiraje(),
                        p.precioCreditos(), p.precioMonedaReal(), p.premium(), p.prototipo(), p.heroe(),
                        p.costoPoder(), p.multiplicadorNivel(), p.turnosCarga(), p.turnosRecarga(),
                        p.efectoGeneral(), p.efectoPotenciado(), p.defensa(), p.parte(), p.efecto(),
                        p.poderDeAtaque(), p.tasaDeCaida(), p.estado(), p.version() + 1, p.creadoEn(),
                        p.modificadoEn(), p.promocion(), p.origen(), p.semillaVersion(), p.modificadoPor(),
                        p.estadoAnteriorSuspension(), p.reservasRecientes());
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

        private static Producto productoEpica(int precioCreditos, BigDecimal precioMonedaReal) {
                Instant ahora = Instant.parse("2026-08-27T18:00:00Z");
                return new Producto(
                        UUID.randomUUID().toString(),            // id
                        "Epica de prueba",                       // nombre
                        "productos/epica-prueba.webp",           // imagen
                        "Epica de prueba",                       // descripcion
                        TipoProducto.EPICA,                      // tipo
                        -1,                                      // tiraje
                        precioCreditos,                          // precioCreditos
                        precioMonedaReal,                        // precioMonedaReal
                        false,                                   // premium
                        null,                                    // prototipo
                        "550e8400-e29b-41d4-a716-446655440000",  // heroe
                        null,                                    // costoPoder
                        null,                                    // multiplicadorNivel
                        null,                                    // turnosCarga
                        2,                                       // turnosRecarga
                        "Aumenta el poder de todo el equipo",    // efectoGeneral
                        "Duplica el poder durante dos turnos",   // efectoPotenciado
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
