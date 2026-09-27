package nexus.contratos;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Consumer;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import nexus.aplicacion.AdquirirProductoServicio;
import nexus.dominio.AdquisicionRegistrada;
import nexus.dominio.EstadoProducto;
import nexus.persistencia.AdquisicionRegistradaRepository;
import nexus.productos.dominio.CatalogoProductos;
import nexus.productos.dominio.DisponibilidadProducto;
import nexus.productos.dominio.EstadoAdquisicion;
import nexus.productos.dominio.RepositorioDisponibilidadEnMemoria;
import nexus.productos.dominio.RepositorioDisponibilidadProductos;
import nexus.productos.dominio.ResultadoAdquisicion;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Productos reserva tiraje como ms-ecommerce cree que lo reserva — B5. Cierra
 * la brecha que {@code tests/contratos/pactos-verificados.py} reportaba para
 * {@code contracts/pactos/ms-ecommerce-productos.json}: al cobrar una orden la
 * tienda reserva una unidad por {@code POST /api/v1/productos/{id}/adquisiciones}
 * (productos 1.4.0) y nadie comprobaba que este servicio respondiera lo que la
 * tienda lee.
 *
 * <h2>Lo que la tienda necesita de esta puerta</h2>
 *
 * La {@code Idempotency-Key} derivada de la orden, la linea y la unidad; 200
 * con {@code estado} ACEPTADA; 409 con el MISMO cuerpo ({@code estado} AGOTADO
 * o SUSPENDIDO), que es lo que decide entre entregar y reembolsar; y 404 si el
 * producto no existe. Del cuerpo se exige {@code estado} por valor y
 * {@code mensaje} solo por tipo: el texto es de productos.
 *
 * <h2>Mismo montaje que la verificacion de inventario</h2>
 *
 * Como {@code VerificacionDelPactoDeSubastasTest} de inventario: el servicio
 * arrancado en un puerto real, la cadena de seguridad de verdad y los casos de
 * uso de verdad —{@link AdquirirProductoServicio} (la clave con su resultado,
 * el 404 que no la consume) y {@link CatalogoProductos} (reserva, agotado,
 * suspension)—. Solo se sustituyen sus dos almacenes, reiniciables entre
 * estados:
 * <ul>
 *   <li>{@link RepositorioDisponibilidadProductos}: el
 *       {@link RepositorioDisponibilidadEnMemoria} del propio dominio, con la
 *       misma regla que la version de Mongo (una reserva por clave, SUSPENDIDO
 *       antes que AGOTADO);</li>
 *   <li>{@link AdquisicionRegistradaRepository}: el registro de claves sobre un
 *       mapa, igual que en {@code AdquirirProductoServicioTest}. Es un
 *       repositorio de Spring Data y el caso de uso solo usa cuatro de sus
 *       operaciones.</li>
 * </ul>
 * Sin MongoDB ni Testcontainers: la verificacion no se salta en una maquina
 * sin Docker, que es el verde vacio que vigila el guardian de pactos. La
 * atomicidad de la reserva contra Mongo real la cubre {@code CatalogoContraMongoIT}.
 *
 * <h2>El producto</h2>
 *
 * El identificador es el que grabo el consumidor. El almacen de tiraje solo
 * conoce identificador, unidades y estado, asi que ningun estado inventa
 * nombre, precio ni estadisticas: se siembra la disponibilidad y se llega a
 * cada situacion por las operaciones del dominio (otra compra se lleva la
 * ultima unidad; un administrador suspende).
 *
 * <h2>La autorizacion</h2>
 *
 * El pacto no graba {@code Authorization}, como los de ms-subastas. La ruta es
 * solo de servicios (rol SERVICIO), asi que cada peticion se firma con un token
 * de servicio de ms-ecommerce del emisor de prueba (RS256 y JWKS por HTTP, como
 * en produccion): lo que se verifica es la reserva, no un 401 o un 403.
 */
@Provider("productos")
@Consumer("ms-ecommerce")
@PactFolder("../../../contracts/pactos")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "catalogo.semilla.habilitada=false")
@DisplayName("Pacto: productos cumple lo que ms-ecommerce espera de la reserva de tiraje")
class VerificacionDelPactoDeEcommerceTest {

    /** El mismo identificador fijo que graba el consumidor (ProductosPactoTest de ms-ecommerce). */
    private static final String PRODUCTO = "5b0a3c1e-8d7f-4e2a-9c6b-1f0e2d3c4b5a";

    /** El cliente de servicio de la tienda: firma cada peticion y queda como solicitante. */
    private static final String TIENDA = "ms-ecommerce";

    /** Unidades que le quedan al producto cuando la reserva se acepta. */
    private static final int TIRAJE = 3;

    /** Otra compra (otra orden, con la forma de clave de la tienda) que se llevo la ultima unidad. */
    private static final String CLAVE_DE_OTRA_COMPRA = "orden-0b7e3f52-9a41-4c8d-b6e2-5d1f0a9c3e71-l1-u1";

    @DynamicPropertySource
    static void identidad(DynamicPropertyRegistry registro) {
        EmisorDeTokensDePrueba.registrarJwks(registro);
    }

    @LocalServerPort
    private int puerto;

    @Autowired
    private DisponibilidadReiniciable disponibilidad;

    @Autowired
    private RegistroDeAdquisicionesEnMemoria registro;

    @Autowired
    private CatalogoProductos catalogo;

    @Autowired
    private AdquirirProductoServicio adquisiciones;

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

    @State("el producto existe y le quedan unidades")
    void conUnidades() {
        reiniciar();
        catalogo.registrar(DisponibilidadProducto.nueva(PRODUCTO, TIRAJE, EstadoProducto.ACTIVO));
    }

    @State("el producto existe y esta agotado")
    void agotado() {
        reiniciar();
        catalogo.registrar(DisponibilidadProducto.nueva(PRODUCTO, 1, EstadoProducto.ACTIVO));
        // Se llega a cero reservando, como en produccion: la ultima unidad se
        // la lleva otra compra por el mismo caso de uso. Un tiraje 0 sembrado a
        // mano ni siquiera se puede crear (DisponibilidadProducto.nueva lo
        // rechaza), y la clave del pacto es otra, asi que no la reconoce como
        // suya.
        ResultadoAdquisicion ultima = adquisiciones.adquirir(PRODUCTO, CLAVE_DE_OTRA_COMPRA, TIENDA);
        if (ultima.estado() != EstadoAdquisicion.ACEPTADA) {
            throw new IllegalStateException("el estado no se pudo montar: la otra compra recibio " + ultima);
        }
    }

    @State("el producto existe y esta suspendido")
    void suspendido() {
        conUnidades();
        // Suspendido por la operacion de dominio de PUT /suspender. Le quedan
        // unidades: el 409 sale de la suspension, no del tiraje.
        catalogo.suspender(PRODUCTO);
    }

    @State("el producto no existe")
    void noExiste() {
        reiniciar();
    }

    /**
     * Pact llama al {@code @State} antes de cada interaccion y el contexto de
     * Spring es uno solo para las cuatro. Las cuatro usan la MISMA clave: sin
     * vaciar el registro, la segunda interaccion recibiria el resultado que
     * guardo la primera, que es justo lo que la idempotencia promete.
     */
    private void reiniciar() {
        disponibilidad.reiniciar();
        registro.reiniciar();
    }

    // ------------------------------------------------------------- dobles

    /**
     * {@link RepositorioDisponibilidadEnMemoria}, reiniciable entre estados. La
     * referencia es volatile porque el estado se siembra en el hilo de la
     * prueba y la reserva la atiende un hilo de Tomcat.
     */
    static final class DisponibilidadReiniciable implements RepositorioDisponibilidadProductos {

        private volatile RepositorioDisponibilidadEnMemoria actual = new RepositorioDisponibilidadEnMemoria();

        void reiniciar() {
            actual = new RepositorioDisponibilidadEnMemoria();
        }

        @Override
        public void guardar(DisponibilidadProducto producto) {
            actual.guardar(producto);
        }

        @Override
        public Optional<DisponibilidadProducto> buscarPorId(String productoId) {
            return actual.buscarPorId(productoId);
        }

        @Override
        public ResultadoAdquisicion adquirirUnaUnidad(String productoId, String clave) {
            return actual.adquirirUnaUnidad(productoId, clave);
        }
    }

    /**
     * El registro de claves de adquisicion sobre un mapa, como en
     * {@code AdquirirProductoServicioTest}: {@code insert} falla con la clave
     * repetida (el indice unico de {@code _id}), {@code save} guarda el
     * resultado y {@code deleteById} devuelve la clave de un 404.
     */
    static final class RegistroDeAdquisicionesEnMemoria {

        private final Map<String, AdquisicionRegistrada> registradas = new ConcurrentHashMap<>();
        private final AdquisicionRegistradaRepository repositorio = mock(AdquisicionRegistradaRepository.class);

        RegistroDeAdquisicionesEnMemoria() {
            when(repositorio.findById(anyString()))
                    .thenAnswer(i -> Optional.ofNullable(registradas.get(i.getArgument(0, String.class))));
            when(repositorio.insert(any(AdquisicionRegistrada.class))).thenAnswer(i -> {
                AdquisicionRegistrada nueva = i.getArgument(0, AdquisicionRegistrada.class);
                if (registradas.putIfAbsent(nueva.clave(), nueva) != null) {
                    throw new DuplicateKeyException("clave repetida " + nueva.clave());
                }
                return nueva;
            });
            when(repositorio.save(any(AdquisicionRegistrada.class))).thenAnswer(i -> {
                AdquisicionRegistrada guardada = i.getArgument(0, AdquisicionRegistrada.class);
                registradas.put(guardada.clave(), guardada);
                return guardada;
            });
            doAnswer(i -> registradas.remove(i.getArgument(0, String.class)))
                    .when(repositorio).deleteById(anyString());
        }

        AdquisicionRegistradaRepository repositorio() {
            return repositorio;
        }

        void reiniciar() {
            registradas.clear();
        }
    }

    @TestConfiguration
    static class AlmacenesEnMemoria {

        @Bean
        @Primary
        DisponibilidadReiniciable disponibilidadReiniciable() {
            return new DisponibilidadReiniciable();
        }

        @Bean
        RegistroDeAdquisicionesEnMemoria registroDeAdquisicionesEnMemoria() {
            return new RegistroDeAdquisicionesEnMemoria();
        }

        @Bean
        @Primary
        AdquisicionRegistradaRepository registroDeAdquisiciones(RegistroDeAdquisicionesEnMemoria registro) {
            return registro.repositorio();
        }
    }
}
