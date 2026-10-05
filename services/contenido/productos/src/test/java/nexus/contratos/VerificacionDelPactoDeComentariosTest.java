package nexus.contratos;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Consumer;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import nexus.dominio.EstadoProducto;
import nexus.dominio.Producto;
import nexus.dominio.TipoProducto;
import nexus.persistencia.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Productos cumple lo que comentarios cree que promete — B3.
 *
 * <p>Comentarios pregunta aqui, antes de dejar comentar o calificar un
 * producto, si el producto existe ({@code contracts/pactos/comentarios-productos.json},
 * lo genera {@code CatalogoPactoTest} en services/plataforma/comentarios). Lo que
 * pacta es poco y es justo lo que lee: {@code GET /api/v1/productos/{id}}
 * publico, 200 con el {@code id} del producto, y 404 cuando no existe. Si esta
 * ruta pasara a pedir token, a devolver otro codigo para un producto
 * inexistente o a llamar de otra forma al identificador, esta prueba se pone
 * roja aqui, en el modulo de quien hizo el cambio.
 *
 * <h2>Servicio arrancado, repositorio simulado, sin Mongo</h2>
 *
 * <p>Mismo camino que {@code ProductosRestAssuredTest} y que la verificacion de
 * inventario: el servicio de verdad —controlador, seguridad, conversores,
 * manejador de errores con sus {@code type}— sobre un {@code ProductoRepository}
 * simulado, asi que no hace falta MongoDB ni se salta en una maquina sin
 * Docker (la trampa de la que avisa {@code tests/contratos/pactos-verificados.py}).
 * El caso de uso que responde es el real ({@code ConsultarProductoServicio}):
 * lo unico simulado es el almacen, que cada {@code @State} siembra.
 *
 * <p>Modulo {@code junit5} de pact-jvm y no {@code junit5spring}: ese esta
 * compilado contra {@code javax.servlet} y revienta en Spring Boot 4 (ver la
 * verificacion de ms-finanzas).
 */
@Provider("productos")
@Consumer("comentarios")
@PactFolder("../../../contracts/pactos")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "KEYCLOAK_JWK_SET_URI=http://localhost/prueba/jwks")
@DisplayName("Pacto: productos cumple lo que comentarios espera")
class VerificacionDelPactoDeComentariosTest {

    /** El mismo identificador que usa el consumidor: «Espada de una mano», del catalogo inicial. */
    private static final String EXISTE = "1647b2ea-096d-37e7-b580-0172e4c62313";

    @LocalServerPort
    private int puerto;

    /** La ruta es publica: el pacto no lleva token y no se verifica ninguno. */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ProductoRepository productoRepository;

    @BeforeEach
    void apuntarAlServicioArrancado(PactVerificationContext contexto) {
        contexto.setTarget(new HttpTestTarget("localhost", puerto));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verificarCadaInteraccion(PactVerificationContext contexto) {
        contexto.verifyInteraction();
    }

    // ------------------------------------------------------------- estados

    @State("el producto existe en el catalogo")
    void existe() {
        when(productoRepository.findById(EXISTE)).thenReturn(Optional.of(espadaDeUnaMano()));
    }

    @State("el producto no existe en el catalogo")
    void noExiste() {
        when(productoRepository.findById(anyString())).thenReturn(Optional.empty());
    }

    private static Producto espadaDeUnaMano() {
        Instant ahora = Instant.parse("2026-09-25T10:00:00Z");
        return new Producto(
                EXISTE,
                "Espada de una mano",
                "productos/espada-de-una-mano.webp",
                "Arma del catalogo inicial del Guerrero Tanque",
                TipoProducto.ARMA,
                -1,
                100,
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
                10,
                new BigDecimal("50"),
                EstadoProducto.ACTIVO,
                0,
                ahora,
                ahora);
    }
}
