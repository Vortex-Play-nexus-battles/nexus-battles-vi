package nexus.misiones.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.nexusbattles.plataforma.resiliencia.CortaCircuitos;
import com.nexusbattles.plataforma.resiliencia.RegistroDeDegradacion;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import nexus.misiones.aplicacion.HeroeNoEncontrado;
import nexus.misiones.aplicacion.HeroeOcupado;
import nexus.misiones.aplicacion.InventarioDeHeroes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Pacto de consumidor misiones -> ms-inventario (inventario.yaml 1.6.0; regla 1
 * de plataforma). Lo genera esta prueba en {@code contracts/pactos/} y lo
 * verifica inventario en {@code VerificacionDelPactoDeMisionesTest}.
 *
 * <p>Fija lo que misiones necesita del inventario y nada mas: la consulta del
 * heroe por id (dueno, tipo, disponibilidad y progresion), el bloqueo mientras
 * esta en mision, la liberacion que le suma la experiencia (idempotente por
 * estado) y la entrega del botin con origen MISION. Como en el pacto de
 * ms-subastas, el jugador viaja en el CUERPO como uuid: la liberacion la lanza
 * un trabajo en segundo plano, sin peticion ni token de jugador.
 *
 * <p>No se exige ningun campo que el cliente no lea: un pacto que pide formas
 * que nadie usa ata al proveedor sin proteger a nadie.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "ms-inventario", pactVersion = PactSpecVersion.V3)
class InventarioPactoTest {

    static final String CONSUMIDOR = "misiones";
    static final String HEROE = "heroe-vorn-01";
    static final UUID JUGADOR = UUID.fromString("5f1c2a7e-3d6b-4b9a-8f0e-1c2d3e4f5a6b");
    static final UUID PRODUCTO = UUID.fromString("2239ecfa-3fc4-3f02-9d87-df52cf06665b");
    static final UUID EJECUCION = UUID.fromString("0c7a2d9e-4f8b-4a55-9b65-6f1d3b2f0a11");
    static final String EPICA = "4481eb34-384a-3fa0-ba9a-1aac9562c38f";

    private static ClienteInventario clienteContra(MockServer servidor) {
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        RestClient cliente = RestClient.builder().requestFactory(new JdkClientHttpRequestFactory(http)).build();
        RegistroDeDegradacion registro = new RegistroDeDegradacion();
        return new ClienteInventario(cliente, servidor.getUrl(),
                new CortaCircuitos("inventario", "Inventario", 100, Duration.ofSeconds(30), Clock.systemUTC(), registro),
                new CortaCircuitos("inventario-entregas", "Entregas", 100, Duration.ofSeconds(30), Clock.systemUTC(),
                        registro));
    }

    // ------------------------------------------------------------ consulta

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact consultaDelHeroe(PactDslWithProvider constructor) {
        return constructor
                .given("el heroe es del jugador y esta libre")
                .uponReceiving("la consulta de un heroe antes de matricularlo")
                .path("/api/v1/inventario/elementos/" + HEROE)
                .method("GET")
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .stringType("elementoId", HEROE)
                        .uuid("productoId", PRODUCTO)
                        .uuid("propietarioUid", JUGADOR)
                        .booleanValue("disponible", true)
                        .stringValue("tipo", "HEROE")
                        .stringType("nombrePropio", "Vorn")
                        .integerType("nivel", 1)
                        .numberType("experiencia", 0))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "consultaDelHeroe")
    void laConsultaDiceDeQuienEsQueEsYSiEstaLibre(MockServer servidor) {
        InventarioDeHeroes.HeroeDelInventario heroe = clienteContra(servidor).consultar(HEROE);

        assertThat(heroe.propietarioUid()).isEqualTo(JUGADOR.toString());
        assertThat(heroe.productoId()).isEqualTo(PRODUCTO.toString());
        assertThat(heroe.esHeroe()).isTrue();
        assertThat(heroe.disponible()).isTrue();
        assertThat(heroe.nivel()).isEqualTo(1);
    }

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact consultaDeUnHeroeInexistente(PactDslWithProvider constructor) {
        return constructor
                .given("el heroe no existe")
                .uponReceiving("la consulta de un heroe que no esta en ningun inventario")
                .path("/api/v1/inventario/elementos/" + HEROE)
                .method("GET")
                .willRespondWith()
                .status(404)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "consultaDeUnHeroeInexistente")
    void unHeroeQueNoExisteEsHeroeNoEncontrado(MockServer servidor) {
        assertThatThrownBy(() -> clienteContra(servidor).consultar(HEROE)).isInstanceOf(HeroeNoEncontrado.class);
    }

    // ------------------------------------------------------------- bloqueo

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact bloqueoDelHeroe(PactDslWithProvider constructor) {
        return constructor
                .given("el heroe es del jugador y esta libre")
                .uponReceiving("el bloqueo del heroe al salir a una mision")
                .path("/api/v1/inventario/elementos/" + HEROE + "/bloqueo-mision")
                .method("PUT")
                .matchHeader("Idempotency-Key", ".+", "mision-" + EJECUCION + "-bloqueo")
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .uuid("propietarioUid", JUGADOR)
                        .uuid("ejecucionId", EJECUCION))
                .willRespondWith()
                .status(200)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "bloqueoDelHeroe")
    void elJugadorYLaEjecucionViajanEnElCuerpo(MockServer servidor) {
        assertThatCode(() -> clienteContra(servidor).bloquear(HEROE, JUGADOR.toString(), EJECUCION))
                .doesNotThrowAnyException();
    }

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact bloqueoDeUnHeroeOcupado(PactDslWithProvider constructor) {
        return constructor
                .given("el heroe esta en otra mision")
                .uponReceiving("el bloqueo de un heroe que ya esta en otra mision")
                .path("/api/v1/inventario/elementos/" + HEROE + "/bloqueo-mision")
                .method("PUT")
                .matchHeader("Idempotency-Key", ".+", "mision-" + EJECUCION + "-bloqueo")
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .uuid("propietarioUid", JUGADOR)
                        .uuid("ejecucionId", EJECUCION))
                .willRespondWith()
                .status(409)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "bloqueoDeUnHeroeOcupado")
    void unHeroeOcupadoEsUn409(MockServer servidor) {
        assertThatThrownBy(() -> clienteContra(servidor).bloquear(HEROE, JUGADOR.toString(), EJECUCION))
                .isInstanceOf(HeroeOcupado.class);
    }

    // ----------------------------------------------------------- liberacion

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact liberacionConExperiencia(PactDslWithProvider constructor) {
        return constructor
                .given("el heroe esta en esa mision")
                .uponReceiving("la liberacion del heroe sumandole la experiencia de la mision")
                .path("/api/v1/inventario/elementos/" + HEROE + "/bloqueo-mision/" + EJECUCION + "/liberacion")
                .method("POST")
                .matchHeader("Idempotency-Key", ".+", "mision-" + EJECUCION + "-liberacion")
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .uuid("propietarioUid", JUGADOR)
                        .decimalType("experiencia", 130.5))
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .booleanValue("disponible", true)
                        .integerType("nivel", 2)
                        .numberType("experiencia", 30.5))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "liberacionConExperiencia")
    void laLiberacionDevuelveElNivelYLaExperienciaYaGuardados(MockServer servidor) {
        InventarioDeHeroes.ProgresionDelHeroe progresion =
                clienteContra(servidor).liberar(HEROE, JUGADOR.toString(), EJECUCION, 130.5);

        assertThat(progresion.nivel()).isEqualTo(2);
        assertThat(progresion.experiencia()).isEqualTo(30.5);
    }

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact liberacionRepetida(PactDslWithProvider constructor) {
        return constructor
                .given("el heroe ya volvio de esa mision")
                .uponReceiving("una liberacion repetida por un reintento")
                .path("/api/v1/inventario/elementos/" + HEROE + "/bloqueo-mision/" + EJECUCION + "/liberacion")
                .method("POST")
                .matchHeader("Idempotency-Key", ".+", "mision-" + EJECUCION + "-liberacion")
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .uuid("propietarioUid", JUGADOR)
                        .decimalType("experiencia", 130.5))
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .booleanValue("disponible", true)
                        .integerType("nivel", 2))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "liberacionRepetida")
    void repetirLaLiberacionEsUn200SinSumarOtraVez(MockServer servidor) {
        assertThat(clienteContra(servidor).liberar(HEROE, JUGADOR.toString(), EJECUCION, 130.5).nivel())
                .isEqualTo(2);
    }

    // -------------------------------------------------------------- entrega

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact entregaDeUnaEpica(PactDslWithProvider constructor) {
        return constructor
                .given("el jugador puede recibir productos del catalogo")
                .uponReceiving("la entrega de la epica ganada en una mision")
                .path("/api/v1/inventario/entregas")
                .method("POST")
                .matchHeader("Idempotency-Key", ".+", "mision-" + EJECUCION + "-epica")
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .uuid("uid", JUGADOR)
                        .stringValue("origen", "MISION")
                        .stringType("referencia", "mision-" + EJECUCION)
                        .minArrayLike("productos", 1)
                        .stringType("productoId", EPICA)
                        .integerType("cantidad", 1)
                        .closeObject()
                        .closeArray())
                .willRespondWith()
                .status(201)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "entregaDeUnaEpica")
    void laEntregaLlevaOrigenMisionYSuClave(MockServer servidor) {
        assertThatCode(() -> clienteContra(servidor).entregar(JUGADOR.toString(), EJECUCION,
                List.of(new InventarioDeHeroes.ProductoAEntregar(EPICA, 1)), "mision-" + EJECUCION + "-epica"))
                .doesNotThrowAnyException();
    }
}
