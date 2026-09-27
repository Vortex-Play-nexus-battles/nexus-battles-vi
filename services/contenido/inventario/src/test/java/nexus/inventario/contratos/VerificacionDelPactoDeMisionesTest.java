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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import nexus.inventario.aplicacion.CatalogoDeProductosEnMemoria;
import nexus.inventario.aplicacion.FuenteDeTablaDeNiveles;
import nexus.inventario.aplicacion.RepositorioDeEntregasEnMemoria;
import nexus.inventario.aplicacion.ResolutorDeProducto;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.Entrega;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.RepositorioDeEntregas;
import nexus.inventario.dominio.TablaDeNiveles;
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
 * ms-inventario cumple lo que misiones espera (contracts/pactos/misiones-ms-inventario.json,
 * inventario.yaml 1.6.0, B9).
 *
 * <p>Mismo montaje que {@link VerificacionDelPactoDeSubastasTest}: servicio
 * arrancado, casos de uso reales y solo los puertos sustituidos —el repositorio
 * por el de memoria y la tabla de niveles de heroes por la del documento
 * (100 x 1,2^(n-1), tope 8)—. Sin Mongo y sin Docker, asi que no se salta en
 * ninguna maquina. Cada peticion lleva una credencial de servicio real de
 * misiones (rol SERVICIO, azp = misiones).
 *
 * <p><b>Todas las interacciones, tambien la entrega de la epica.</b> Mientras
 * {@code POST /api/v1/inventario/entregas} no existia en esta rama, un filtro
 * dejaba fuera el estado «el jugador puede recibir productos del catalogo».
 * Con B4 fusionado la entrega es la real ({@code EntregarProductos}: valida
 * contra el catalogo, registra la entrega y la aplica en una escritura), asi
 * que el filtro sobra: aqui se sustituyen tambien sus dos puertos, la
 * coleccion {@code entregas} por la de memoria y el catalogo por un doble que
 * solo conoce la epica que misiones entrega, «Segundo impulso» del catalogo
 * oficial (contracts/esquemas/catalogo-oficial.yaml).
 */
@Provider("ms-inventario")
@Consumer("misiones")
@PactFolder("../../../contracts/pactos")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.data.mongodb.auto-index-creation=false")
@DisplayName("Pacto: ms-inventario cumple lo que misiones espera")
class VerificacionDelPactoDeMisionesTest {

    /** Los mismos identificadores fijos que usa el consumidor (InventarioPactoTest de misiones). */
    private static final String HEROE = "heroe-vorn-01";
    private static final String JUGADOR = "5f1c2a7e-3d6b-4b9a-8f0e-1c2d3e4f5a6b";
    private static final String PRODUCTO = "2239ecfa-3fc4-3f02-9d87-df52cf06665b";
    private static final String PRODUCTO_HISTORICO = "p-heroe-historico";
    private static final String EJECUCION = "0c7a2d9e-4f8b-4a55-9b65-6f1d3b2f0a11";
    private static final String OTRA_EJECUCION = "5e2b8c1a-7d3f-4c11-8a2e-9b0c4d5e6f70";
    /**
     * La epica que misiones entrega en el pacto: «Segundo impulso»
     * ({@code epica-guerrero-armas-segundo-impulso} en
     * services/contenido/productos/docs/catalogo-inicial-identificadores.md).
     */
    private static final String EPICA = "4481eb34-384a-3fa0-ba9a-1aac9562c38f";

    @DynamicPropertySource
    static void identidad(DynamicPropertyRegistry registro) {
        EmisorDeTokensDePrueba.registrarJwks(registro);
    }

    @LocalServerPort
    private int puerto;

    @Autowired
    private VerificacionDelPactoDeSubastasTest.RepositorioReiniciable repositorio;

    @Autowired
    private EntregasReiniciables entregas;

    @BeforeEach
    void apuntarAlServicioArrancado(PactVerificationContext contexto) {
        contexto.setTarget(new HttpTestTarget("localhost", puerto));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verificarCadaInteraccion(PactVerificationContext contexto, HttpRequest peticion) {
        peticion.addHeader("Authorization",
                "Bearer " + EmisorDeTokensDePrueba.emisor().tokenDeServicio("misiones"));
        contexto.verifyInteraction();
    }

    // ------------------------------------------------------------- estados

    @State("el heroe es del jugador y esta libre")
    void libre() {
        repositorio.reiniciar().guardar(Inventario.vacio(JUGADOR).agregar(vorn()));
    }

    @State("el heroe no existe")
    void noExiste() {
        repositorio.reiniciar();
    }

    @State("el heroe es del jugador y su producto conserva un id historico")
    void productoHistorico() {
        // Inventario historico: el producto no tiene id UUID, asi que la
        // consulta interna responde 409 y misiones lo busca en la vitrina del
        // jugador (con su credencial y X-User-Name).
        repositorio.reiniciar().guardar(Inventario.vacio(JUGADOR)
                .agregar(new ElementoInventario(HEROE, PRODUCTO_HISTORICO, TipoElementoInventario.HEROE, "Vorn")));
    }

    @State("el heroe esta en otra mision")
    void enOtraMision() {
        repositorio.reiniciar().guardar(
                Inventario.vacio(JUGADOR).agregar(vorn()).bloquearEnMision(HEROE, OTRA_EJECUCION));
    }

    @State("el heroe esta en esa mision")
    void enEsaMision() {
        // Se llega por el agregado, como en produccion: las mismas reglas.
        repositorio.reiniciar().guardar(
                Inventario.vacio(JUGADOR).agregar(vorn()).bloquearEnMision(HEROE, EJECUCION));
    }

    @State("el heroe ya volvio de esa mision")
    void yaVolvio() {
        // La liberacion ya se aplico: libre y con la experiencia sumada. Repetir
        // no vuelve a sumar (idempotente por estado).
        repositorio.reiniciar().guardar(Inventario.vacio(JUGADOR).agregar(vorn())
                .bloquearEnMision(HEROE, EJECUCION)
                .liberarDeMision(HEROE, EJECUCION, 2, 30.5));
    }

    @State("el jugador puede recibir productos del catalogo")
    void puedeRecibir() {
        // POST /entregas (B4): el jugador ya tiene inventario, la clave de la
        // entrega no se ha usado y el catalogo conoce la epica (ver Dobles).
        repositorio.reiniciar().guardar(Inventario.vacio(JUGADOR).agregar(vorn()));
        entregas.reiniciar();
    }

    private static ElementoInventario vorn() {
        return new ElementoInventario(HEROE, PRODUCTO, TipoElementoInventario.HEROE, "Vorn");
    }

    // ------------------------------------------------------------- dobles

    /**
     * La coleccion {@code entregas} en memoria (el doble de las pruebas de
     * aplicacion de B4), reiniciable entre estados como el repositorio de
     * inventarios: el contexto de Spring es uno para todas las interacciones.
     */
    static final class EntregasReiniciables implements RepositorioDeEntregas {

        private RepositorioDeEntregasEnMemoria actual = new RepositorioDeEntregasEnMemoria();

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

    @TestConfiguration
    static class Dobles {

        @Bean
        @Primary
        VerificacionDelPactoDeSubastasTest.RepositorioReiniciable repositorioReiniciable() {
            return new VerificacionDelPactoDeSubastasTest.RepositorioReiniciable();
        }

        @Bean
        @Primary
        EntregasReiniciables entregasReiniciables() {
            return new EntregasReiniciables();
        }

        /** El catalogo, solo con la epica que misiones entrega: activa, como en el catalogo oficial. */
        @Bean
        @Primary
        ResolutorDeProducto catalogoConLaEpica() {
            return new CatalogoDeProductosEnMemoria().registrar(EPICA,
                    new ResolutorDeProducto.DetalleProducto("Segundo impulso", "EPICA", null, "ACTIVO"));
        }

        /** La tabla que publica heroes: 100 x 1,2^(n-1) para los niveles 1 a 7. */
        @Bean
        @Primary
        FuenteDeTablaDeNiveles tablaDelDocumento() {
            List<Double> paraSubir = new ArrayList<>();
            for (int nivel = 1; nivel <= 7; nivel++) {
                paraSubir.add(100 * Math.pow(1.2, nivel - 1));
            }
            TablaDeNiveles tabla = new TablaDeNiveles(paraSubir);
            return () -> tabla;
        }
    }
}
