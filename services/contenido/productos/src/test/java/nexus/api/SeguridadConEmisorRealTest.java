package nexus.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.nexusbattles.comun.seguridad.ConversorRolesJwt;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import nexus.dominio.Producto;
import nexus.persistencia.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Productos frente al emisor REAL del sistema — R9.7.
 *
 * <h2>El defecto que esta clase existe para fijar</h2>
 *
 * {@code SeguridadConJwksRealTest}, que sigue viva al lado, prueba el
 * decodificador real contra un JWKS propio y tokens con la forma de
 * <b>Keycloak</b>: el rol dentro de {@code realm_access.roles}. Esa era la
 * unica forma que este servicio entendia, porque su conversor leia solo ese
 * bloque.
 *
 * <p>Pero el emisor del sistema <b>nunca fue Keycloak</b>. Es ms-identidad
 * (ADR-002 / ADR-005), y sus tokens tienen otra forma:
 *
 * <pre>
 *   ms-identidad -&gt; { "sub": "lyra", "uid": "&lt;uuid&gt;", "rol": "ADMINISTRADOR" }
 *   Keycloak     -&gt; { "sub": "&lt;uuid&gt;", "realm_access": { "roles": [...] } }
 * </pre>
 *
 * <p>Con el conversor anterior, un token legitimo de ms-identidad se
 * autenticaba —la firma es valida— y llegaba con <b>cero authorities</b>: un
 * administrador de verdad recibia <b>403</b> al crear un producto. Apuntar el
 * {@code jwk-set-uri} al emisor correcto, por si solo, no lo habria arreglado:
 * lo habria movido de 401 a 403, que es un fallo mas dificil de leer.
 *
 * <p>Por eso R9.7 son dos cosas y no una: el JWKS correcto <b>y</b> el
 * conversor compartido. Esta clase fija la segunda; el {@code jwk-set-uri} de
 * {@code application.properties}, la primera.
 *
 * <p>Los tokens los firma {@link EmisorDeTokensDePrueba}, el mismo emisor de
 * prueba que ya usan inventario, salas-partidas y los demas: reproduce la forma
 * de ms-identidad, no una inventada, y sirve su JWKS por HTTP igual que
 * {@code GET /api/v1/auth/jwks}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Productos con tokens del emisor real (ms-identidad)")
class SeguridadConEmisorRealTest {

    private static final EmisorDeTokensDePrueba EMISOR = EmisorDeTokensDePrueba.emisor();

    /** Mismo cuerpo valido que usa ProductosApiTest: aqui se prueba la puerta, no la validacion. */
    private static final String ARMA_VALIDA = """
            {
              "nombre": "Arma de prueba",
              "imagen": "productos/arma-prueba.webp",
              "descripcion": "Arma creada para verificar la puerta de seguridad",
              "tipo": "ARMA",
              "tiraje": 100,
              "precioCreditos": 500,
              "premium": false,
              "poderDeAtaque": 25,
              "tasaDeCaida": 10
            }
            """;

    @DynamicPropertySource
    static void apuntarAlEmisorReal(DynamicPropertyRegistry registro) {
        EmisorDeTokensDePrueba.registrarJwks(registro);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ConversorRolesJwt conversor;

    /** El catalogo no es el sujeto de esta prueba: lo que se ejercita es la cadena. */
    @MockitoBean
    private ProductoRepository repositorio;

    @BeforeEach
    void simularPersistencia() {
        when(repositorio.save(any(Producto.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0, Producto.class));
    }

    @Nested
    @DisplayName("El rol viaja en `rol`, no en `realm_access`")
    class FormaDeMsIdentidad {

        @Test
        @DisplayName("un ADMINISTRADOR de ms-identidad crea el producto: 201 (antes de R9.7 era 403)")
        void administradorCrea() throws Exception {
            mvc.perform(post("/api/v1/productos")
                            .header("Authorization", "Bearer " + EMISOR.tokenDeUsuario(
                                    "raiz", UUID.randomUUID(), "ADMINISTRADOR"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ARMA_VALIDA))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.nombre").value("Arma de prueba"));
        }

        @Test
        @DisplayName("un SUPER_ADMINISTRADOR de ms-identidad tambien: 201")
        void superAdministradorCrea() throws Exception {
            mvc.perform(post("/api/v1/productos")
                            .header("Authorization", "Bearer " + EMISOR.tokenDeUsuario(
                                    "raiz", UUID.randomUUID(), "SUPER_ADMINISTRADOR"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ARMA_VALIDA))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("un JUGADOR de ms-identidad sigue sin poder crear: 403")
        void jugadorNoCrea() throws Exception {
            mvc.perform(post("/api/v1/productos")
                            .header("Authorization", "Bearer "
                                    + EMISOR.tokenDeJugador("lyra", UUID.randomUUID()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ARMA_VALIDA))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.type").value("urn:nexus:problema:acceso-denegado"));
        }

        @Test
        @DisplayName("las estadisticas aceptan a cualquier usuario autenticado del emisor real")
        void estadisticasConJugador() throws Exception {
            when(repositorio.count()).thenReturn(7L);

            mvc.perform(get("/api/v1/productos/estadisticas")
                            .header("Authorization", "Bearer "
                                    + EMISOR.tokenDeJugador("lyra", UUID.randomUUID())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(7));
        }
    }

    @Nested
    @DisplayName("Identidad: `uid` manda, `sub` es el apodo (ADR-002)")
    class Identidad {

        @Test
        @DisplayName("el principal es el uid, no el apodo mutable del sujeto")
        void principalEsElUid() {
            UUID uid = UUID.randomUUID();
            Jwt jwt = EMISOR.decodificador()
                    .decode(EMISOR.tokenDeUsuario("lyra", uid, "ADMINISTRADOR"));

            Authentication autenticacion = conversor.convert(jwt);

            // `sub` es el apodo y un apodo se puede cambiar; por eso ADR-002
            // manda usar `uid` como identificador estable. Guardar el sujeto
            // como si fuera un UUID —el error que ADR-002 documenta— reventaria
            // aqui con un UUID.fromString("lyra").
            assertEquals("lyra", jwt.getSubject(), "el sujeto sigue siendo el apodo");
            assertEquals(uid.toString(), autenticacion.getName(),
                    "el principal tiene que ser el uid estable, no el apodo");
        }

        @Test
        @DisplayName("un token de servicio (ADR-005) llega con ROLE_SERVICIO y azp")
        void tokenDeServicio() {
            Jwt jwt = EMISOR.decodificador().decode(EMISOR.tokenDeServicio("ms-subastas"));

            Authentication autenticacion = conversor.convert(jwt);

            assertEquals("ms-subastas", jwt.getClaimAsString("azp"));
            assertTrue(
                    autenticacion.getAuthorities().stream()
                            .anyMatch(a -> "ROLE_SERVICIO".equals(a.getAuthority())),
                    "un token de servicio debe traer ROLE_SERVICIO; trajo "
                            + autenticacion.getAuthorities());
        }
    }

    @Nested
    @DisplayName("Lo que no debe entrar, sigue sin entrar")
    class NoEntra {

        @Test
        @DisplayName("sin token: 401")
        void sinToken() throws Exception {
            mvc.perform(post("/api/v1/productos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ARMA_VALIDA))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.type").value("urn:nexus:problema:no-autenticado"));
        }

        @Test
        @DisplayName("firmado por otra clave: 401 — la firma se verifica de verdad")
        void firmaDesconocida() throws Exception {
            mvc.perform(post("/api/v1/productos")
                            .header("Authorization", "Bearer "
                                    + EMISOR.tokenFirmadoPorOtro("raiz", UUID.randomUUID()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ARMA_VALIDA))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("caducado: 401 — la vigencia se comprueba de verdad")
        void caducado() throws Exception {
            mvc.perform(post("/api/v1/productos")
                            .header("Authorization", "Bearer "
                                    + EMISOR.tokenCaducado("raiz", UUID.randomUUID()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ARMA_VALIDA))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("el catalogo por id sigue siendo publico (contrato 1.1.0)")
        void catalogoSiguePublico() throws Exception {
            when(repositorio.findById("p-1")).thenReturn(Optional.empty());

            // 404 y no 401: la ruta es publica y el token no hace falta.
            mvc.perform(get("/api/v1/productos/{id}", "p-1"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("el listado del catalogo tambien es publico (contrato 1.2.0, R16)")
        void listadoPublico() throws Exception {
            when(repositorio.findByEstadoIn(any(), any()))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            // 200 y no 401: GET de la coleccion se abrio junto a GET /{id}; el
            // POST de la misma ruta sigue en 401/403 (ver sinToken y jugadorNoCrea).
            mvc.perform(get("/api/v1/productos"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }
}
