package nexus.inventario.contratos;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Consumer;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nexus.inventario.aplicacion.CatalogoDeProductosEnMemoria;
import nexus.inventario.aplicacion.EntregarProductos;
import nexus.inventario.aplicacion.RepositorioDeEntregasEnMemoria;
import nexus.inventario.aplicacion.RepositorioInventariosEnMemoria;
import nexus.inventario.aplicacion.ResolutorDeProducto;
import nexus.inventario.aplicacion.SolicitudDeEntrega;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.Entrega;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.LineaDeEntrega;
import nexus.inventario.dominio.OrigenDeEntrega;
import nexus.inventario.dominio.RepositorioDeEntregas;
import nexus.inventario.dominio.RepositorioDeInventarios;
import org.apache.hc.core5.http.HttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * El inventario entrega lo que ms-ecommerce cree que entrega — B5. Cierra la
 * brecha que {@code tests/contratos/pactos-verificados.py} reportaba para
 * {@code contracts/pactos/ms-ecommerce-inventario.json}: la compra cobrada
 * entrega por {@code POST /api/v1/inventario/entregas} (inventario 1.5.0) y
 * nadie comprobaba que el inventario respondiera lo que la tienda lee.
 *
 * <h2>Lo que la tienda necesita de esta puerta</h2>
 *
 * Solo el codigo: 201 entrega nueva, 200 la misma clave otra vez (el reintento
 * de la tarea programada es seguro), 409 un producto suspendido y 422 uno que
 * no existe (los dos compensan la orden). El pacto no pide cuerpo de respuesta
 * a proposito, con el mismo criterio que R11.4 aplico al de ms-subastas.
 *
 * <h2>Mismo montaje que la verificacion de subastas</h2>
 *
 * Igual que {@link VerificacionDelPactoDeSubastasTest}: el servicio arrancado
 * en un puerto real, la cadena de seguridad de verdad y el caso de uso de
 * verdad ({@link EntregarProductos}: huella del cuerpo, planeacion de
 * elementos, idempotencia por clave). Solo se sustituyen sus tres puertos por
 * los dobles en memoria que ya usan las pruebas de aplicacion, envueltos para
 * poder reiniciarlos entre estados:
 * <ul>
 *   <li>{@link RepositorioDeInventarios}: {@link RepositorioInventariosEnMemoria};</li>
 *   <li>{@link RepositorioDeEntregas}: {@link RepositorioDeEntregasEnMemoria},
 *       una por clave, como el indice unico de Mongo;</li>
 *   <li>{@link ResolutorDeProducto}: {@link CatalogoDeProductosEnMemoria}, el
 *       doble del servicio de productos. Lo que se verifica aqui es la puerta
 *       del inventario, no la de productos: esa tiene su propio pacto
 *       ({@code ms-ecommerce-productos.json}).</li>
 * </ul>
 * Sin MongoDB ni Testcontainers, por las mismas dos razones: la verificacion
 * no se salta en una maquina sin Docker (un verde que no verifico nada, lo que
 * vigila el guardian de pactos) y corre donde Mongo 8 no arranca.
 *
 * <h2>El producto es del catalogo oficial</h2>
 *
 * El identificador es el que grabo el consumidor. Lo que el catalogo responde
 * de el es un arma del documento del curso —«Kit de urgencias», del Medico,
 * en {@code contracts/esquemas/catalogo-oficial.yaml}—: ningun estado inventa
 * un producto. El inventario solo lee de ahi el tipo, el nombre y el estado.
 *
 * <h2>La autorizacion</h2>
 *
 * El pacto no graba {@code Authorization}, como los de ms-subastas. La ruta
 * exige un servicio o un administrador, asi que cada peticion se firma con un
 * token de servicio de ms-ecommerce del emisor de prueba (RS256 y JWKS por
 * HTTP, como en produccion): lo que se verifica es la respuesta del inventario,
 * no un 401. El {@code azp} queda como solicitante de la entrega.
 *
 * <p>{@code @Consumer} acota la clase a los pactos de ms-ecommerce: si otro
 * servicio publica despues su propio pacto de entregas contra «inventario»
 * (el cofre, el premio de un torneo), tendra su propia verificacion con sus
 * propios estados.
 */
@Provider("inventario")
@Consumer("ms-ecommerce")
@PactFolder("../../../contracts/pactos")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.data.mongodb.auto-index-creation=false")
@DisplayName("Pacto: inventario cumple lo que ms-ecommerce espera de la entrega de una compra")
class VerificacionDelPactoDeEcommerceTest {

    /** Los mismos valores fijos que graba el consumidor (InventarioPactoTest de ms-ecommerce). */
    private static final UUID JUGADOR = UUID.fromString("7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55");
    private static final String ORDEN = "8f14e45f-ceea-467a-a8a1-6a6d1e1b0c3d";
    private static final String PRODUCTO = "5b0a3c1e-8d7f-4e2a-9c6b-1f0e2d3c4b5a";
    private static final int UNIDADES = 2;
    private static final String CLAVE = "orden-" + ORDEN;

    /** El cliente de servicio de la tienda: firma cada peticion y queda como solicitante. */
    private static final String TIENDA = "ms-ecommerce";

    /** Arma del Medico en el catalogo oficial (Tabla de armas del documento). */
    private static final String NOMBRE_OFICIAL = "Kit de urgencias";

    @DynamicPropertySource
    static void identidad(DynamicPropertyRegistry registro) {
        EmisorDeTokensDePrueba.registrarJwks(registro);
    }

    @LocalServerPort
    private int puerto;

    @Autowired
    private InventariosReiniciables inventarios;

    @Autowired
    private EntregasReiniciables registroDeEntregas;

    @Autowired
    private CatalogoReiniciable catalogo;

    @Autowired
    private EntregarProductos entregas;

    @BeforeEach
    void apuntarAlServicioArrancado(PactVerificationContext contexto) {
        contexto.setTarget(new HttpTestTarget("localhost", puerto));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verificarCadaInteraccion(PactVerificationContext contexto, HttpRequest peticion) {
        peticion.addHeader("Authorization",
                "Bearer " + EmisorDeTokensDePrueba.emisor().tokenDeServicio(TIENDA));
        contexto.verifyInteraction();
    }

    // ------------------------------------------------------------- estados

    @State("los productos de la entrega existen y no estan suspendidos")
    void productoActivo() {
        reiniciar().registrar(PRODUCTO, delCatalogo("ACTIVO"));
    }

    @State("la entrega con esa clave ya se hizo con el mismo cuerpo")
    void entregaYaHecha() {
        productoActivo();
        // Se llega por el propio caso de uso, no guardando una Entrega a mano:
        // la huella del cuerpo, los elementos planeados y la anotacion en el
        // inventario del jugador salen de la misma logica que en produccion. La
        // peticion del pacto es el reintento de esta entrega.
        entregas.entregar(
                new SolicitudDeEntrega(JUGADOR, OrigenDeEntrega.COMPRA, ORDEN,
                        List.of(new LineaDeEntrega(PRODUCTO, UNIDADES))),
                CLAVE,
                TIENDA);
    }

    @State("un producto de la entrega esta suspendido")
    void productoSuspendido() {
        reiniciar().registrar(PRODUCTO, delCatalogo("SUSPENDIDO"));
    }

    @State("un producto de la entrega no existe en el catalogo")
    void productoInexistente() {
        // Un catalogo que no conoce el producto: el doble responde como
        // productos cuando da 404 (ProductoNoEncontradoException).
        reiniciar();
    }

    /** Vacia los tres puertos; devuelve el catalogo, que es lo que cada estado siembra. */
    private CatalogoDeProductosEnMemoria reiniciar() {
        inventarios.reiniciar();
        registroDeEntregas.reiniciar();
        return catalogo.reiniciar();
    }

    private static ResolutorDeProducto.DetalleProducto delCatalogo(String estado) {
        return new ResolutorDeProducto.DetalleProducto(NOMBRE_OFICIAL, "ARMA", null, estado);
    }

    // ------------------------------------------------------------- dobles

    /*
     * Pact llama al @State antes de cada interaccion y el contexto de Spring es
     * uno solo para las cuatro: sin reiniciar, la entrega que dejo hecha un
     * estado haria que la interaccion siguiente, con la misma clave, respondiera
     * 200 en vez de 201. Las referencias son volatile porque el estado se
     * siembra en el hilo de la prueba y la peticion la atiende un hilo de Tomcat.
     */

    /** {@link RepositorioInventariosEnMemoria}, reiniciable entre estados. */
    static final class InventariosReiniciables implements RepositorioDeInventarios {

        private volatile RepositorioInventariosEnMemoria actual = new RepositorioInventariosEnMemoria();

        void reiniciar() {
            actual = new RepositorioInventariosEnMemoria();
        }

        @Override
        public Inventario guardar(Inventario inventario) {
            return actual.guardar(inventario);
        }

        @Override
        public Optional<Inventario> buscarPorPropietario(String propietarioId) {
            return actual.buscarPorPropietario(propietarioId);
        }

        @Override
        public Optional<Inventario> buscarPorElementoId(String elementoId) {
            return actual.buscarPorElementoId(elementoId);
        }

        @Override
        public List<Inventario> buscarTodosPorElementoId(String elementoId) {
            return actual.buscarTodosPorElementoId(elementoId);
        }

        @Override
        public List<ElementoInventario> buscarElementos(String propietarioId, String criterio) {
            return actual.buscarElementos(propietarioId, criterio);
        }
    }

    /** {@link RepositorioDeEntregasEnMemoria} (una entrega por clave), reiniciable entre estados. */
    static final class EntregasReiniciables implements RepositorioDeEntregas {

        private volatile RepositorioDeEntregasEnMemoria actual = new RepositorioDeEntregasEnMemoria();

        void reiniciar() {
            actual = new RepositorioDeEntregasEnMemoria();
        }

        @Override
        public Optional<Entrega> buscarPorClave(String clave) {
            return actual.buscarPorClave(clave);
        }

        @Override
        public void registrar(Entrega entrega) {
            actual.registrar(entrega);
        }

        @Override
        public void completar(String entregaId, Instant entregadaEn) {
            actual.completar(entregaId, entregadaEn);
        }
    }

    /** {@link CatalogoDeProductosEnMemoria}, el doble del servicio de productos, reiniciable entre estados. */
    static final class CatalogoReiniciable implements ResolutorDeProducto {

        private volatile CatalogoDeProductosEnMemoria actual = new CatalogoDeProductosEnMemoria();

        CatalogoDeProductosEnMemoria reiniciar() {
            actual = new CatalogoDeProductosEnMemoria();
            return actual;
        }

        @Override
        public DetalleProducto resolver(String productoId) {
            return actual.resolver(productoId);
        }
    }

    @TestConfiguration
    static class PuertosEnMemoria {

        @Bean
        @Primary
        InventariosReiniciables inventariosReiniciables() {
            return new InventariosReiniciables();
        }

        @Bean
        @Primary
        EntregasReiniciables entregasReiniciables() {
            return new EntregasReiniciables();
        }

        @Bean
        @Primary
        CatalogoReiniciable catalogoReiniciable() {
            return new CatalogoReiniciable();
        }
    }
}
