package nexus.inventario.contratos;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import java.util.List;
import java.util.Optional;
import nexus.inventario.aplicacion.RepositorioInventariosEnMemoria;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.RepositorioDeInventarios;
import nexus.inventario.dominio.TipoElementoInventario;
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
 * ms-inventario cumple lo que ms-subastas cree que promete — cierra la brecha
 * que {@code tests/contratos/pactos-verificados.py} reportaba en cada corrida
 * de CI: "el pacto existe y nadie lo verifica".
 *
 * <h2>Casos de uso reales, repositorio en memoria</h2>
 *
 * La verificacion de ms-finanzas simula la capa de servicio con un
 * {@code @MockitoBean}: el pacto describe la puerta, no el almacen. Aqui se va
 * un paso mas adentro a proposito — los tres casos de uso son los de verdad y
 * solo el puerto {@link RepositorioDeInventarios} se sustituye por el doble en
 * memoria que ya usan las pruebas de aplicacion. Cada {@code @State} siembra
 * el inventario que su nombre describe, y es la logica real la que decide.
 *
 * <p>La diferencia no es teorica: con los casos de uso simulados, la
 * interaccion de liberacion habria pasado en verde. Con los reales se vio que
 * el pacto grababa un {@code DELETE} sin {@code Idempotency-Key}, que
 * {@code GestionarBloqueoSubasta.liberar} rechaza con 400. El consumidor si
 * manda la clave; era el pacto el que no la declaraba. Un doble que responde
 * lo que se le pide no habria tenido nada que objetar.
 *
 * <h2>Sin MongoDB, y sin Testcontainers</h2>
 *
 * Nada de esto toca Mongo: el cliente de Spring Data se crea pero nunca
 * conecta, porque el unico repositorio que se usa es el de memoria y la
 * creacion automatica de indices —lo unico que conectaria al arrancar— se
 * apaga. Eso tiene dos ventajas que importan aqui:
 * <ul>
 *   <li>La prueba no se salta en una maquina sin Docker. El guardian de
 *       pactos advierte justo de eso: una verificacion con
 *       {@code @Testcontainers(disabledWithoutDocker = true)} sale verde sin
 *       haber verificado nada.</li>
 *   <li>Corre donde Mongo 8 no arranca. Docker Desktop con kernel 6.19 o
 *       superior no puede levantar esta version de MongoDB (SERVER-121912),
 *       que es por lo que las pruebas {@code *MongoIT} de este modulo fallan
 *       en algunas maquinas.</li>
 * </ul>
 *
 * <h2>La autorizacion</h2>
 *
 * El pacto no graba la cabecera {@code Authorization}, igual que el de
 * finanzas. Las seis rutas exigen {@code azp = ms-subastas}, asi que cada
 * peticion se firma con un token de servicio real del emisor de prueba —misma
 * firma RS256 y mismo JWKS por HTTP que en produccion—. Lo que se verifica es
 * la respuesta del servicio, no un 401.
 */
@Provider("ms-inventario")
@PactFolder("../../../contracts/pactos")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.data.mongodb.auto-index-creation=false")
@DisplayName("Pacto: ms-inventario cumple lo que ms-subastas espera")
class VerificacionDelPactoDeSubastasTest {

    /** Los mismos identificadores fijos que usa el consumidor. */
    private static final String ELEMENTO = "elem-hacha-01";
    private static final String VENDEDOR = "77777777-0000-0000-0000-0000000000cc";
    private static final String PRODUCTO = "bbbbbbbb-0000-0000-0000-000000000002";
    private static final String SUBASTA = "aaaaaaaa-0000-0000-0000-000000000001";
    private static final String NUEVO_DUENO = "99999999-0000-0000-0000-0000000000ee";

    @DynamicPropertySource
    static void identidad(DynamicPropertyRegistry registro) {
        EmisorDeTokensDePrueba.registrarJwks(registro);
    }

    @LocalServerPort
    private int puerto;

    @Autowired
    private RepositorioReiniciable repositorio;

    @BeforeEach
    void apuntarAlServicioArrancado(PactVerificationContext contexto) {
        contexto.setTarget(new HttpTestTarget("localhost", puerto));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verificarCadaInteraccion(PactVerificationContext contexto, HttpRequest peticion) {
        peticion.addHeader("Authorization",
                "Bearer " + EmisorDeTokensDePrueba.emisor().tokenDeServicio("ms-subastas"));
        contexto.verifyInteraction();
    }

    // ------------------------------------------------------------- estados

    @State("el elemento existe, esta disponible y es del propietario indicado")
    void disponibleDelVendedor() {
        repositorio.reiniciar().guardar(Inventario.vacio(VENDEDOR).agregar(hacha()));
    }

    @State("el elemento existe")
    void existe() {
        disponibleDelVendedor();
    }

    @State("el elemento no existe")
    void noExiste() {
        repositorio.reiniciar();
    }

    @State("el elemento esta bloqueado por esa subasta")
    void bloqueadoPorLaSubasta() {
        // Se llega al bloqueo por el propio agregado y no construyendo el
        // elemento con la subasta puesta: asi el estado pasa por las mismas
        // reglas que en produccion (no equipado, disponible).
        repositorio.reiniciar().guardar(
                Inventario.vacio(VENDEDOR).agregar(hacha()).bloquearEnSubasta(ELEMENTO, SUBASTA));
    }

    @State("el elemento esta bloqueado por esa subasta y va a adjudicarse")
    void bloqueadoYPorAdjudicar() {
        bloqueadoPorLaSubasta();
    }

    @State("ese elemento ya se transfirio con esa misma clave de idempotencia")
    void yaTransferido() {
        // La idempotencia de la transferencia la da el estado, no la clave: si
        // el elemento ya es del nuevo dueno, repetir termina bien sin moverlo.
        repositorio.reiniciar().guardar(Inventario.vacio(NUEVO_DUENO).agregar(hacha()));
    }

    private static ElementoInventario hacha() {
        return new ElementoInventario(ELEMENTO, PRODUCTO, TipoElementoInventario.ITEM, "Hacha de obsidiana");
    }

    // ------------------------------------------------------------- doble

    /**
     * El repositorio en memoria de las pruebas de aplicacion, reiniciable entre
     * estados. Pact llama al {@code @State} antes de cada interaccion y el
     * contexto de Spring es uno solo para las seis: sin reiniciar, lo que
     * sembro un estado se colaria en el siguiente.
     */
    static final class RepositorioReiniciable implements RepositorioDeInventarios {

        private RepositorioInventariosEnMemoria actual = new RepositorioInventariosEnMemoria();

        RepositorioInventariosEnMemoria reiniciar() {
            actual = new RepositorioInventariosEnMemoria();
            return actual;
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

    @TestConfiguration
    static class RepositorioEnMemoria {

        @Bean
        @Primary
        RepositorioReiniciable repositorioReiniciable() {
            return new RepositorioReiniciable();
        }
    }
}
