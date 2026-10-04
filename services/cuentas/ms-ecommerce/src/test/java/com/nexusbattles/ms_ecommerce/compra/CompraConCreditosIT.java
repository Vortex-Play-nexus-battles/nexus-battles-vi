package com.nexusbattles.ms_ecommerce.compra;

import com.jayway.jsonpath.JsonPath;
import com.nexusbattles.ms_ecommerce.seguridad.TokensDePrueba;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-44 (auditoria del 4-oct, cambio autorizado n.º 5): pagar el carrito con los
 * creditos del juego, de punta a punta dentro del servicio. PostgreSQL de
 * verdad con Flyway (V1..V5), la cadena de seguridad con tokens firmados y los
 * clientes HTTP reales; simulados solo los servicios con los que habla la
 * compra ({@link ServiciosSimulados}), incluido el libro de creditos de
 * ms-finanzas, idempotente por {@code refId} como el de verdad.
 *
 * <p>Lo que se mira es lo que el jugador nota y lo que no se puede romper: el
 * saldo baja UNA vez y exactamente el precio del catalogo, el producto llega
 * UNA vez al inventario, y ni el doble clic, ni el reintento, ni una respuesta
 * perdida, ni una caida a mitad de compra cobran dos veces. Si no se puede
 * entregar, los creditos vuelven.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "tienda.ordenes.reanudacion.habilitada=false",
        "tienda.ordenes.reintento-inicial=0s",
        "tienda.credencial.client-id=ms-ecommerce",
        "tienda.credencial.client-secret=secreto-de-prueba-de-la-tienda"})
@Testcontainers
@Import({TokensDePrueba.Decodificador.class, CompraConCreditosIT.RelojControlado.class})
@DisplayName("Compra con creditos del juego (D-44): cotizacion, cobro en ms-finanzas, entrega y devolucion")
class CompraConCreditosIT {

    private static final String ESPADA = "5b0a3c1e-8d7f-4e2a-9c6b-1f0e2d3c4b5a";
    private static final String ESCUDO = "0e9d8c7b-6a5f-4e3d-8c2b-1a0f9e8d7c6b";
    private static final String HEROE_PREMIUM = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d";
    private static final String TARJETA_APROBADA = "4242 4242 4242 4242";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> BASE = new PostgreSQLContainer<>("postgres:15-alpine");

    static final ServiciosSimulados SERVICIOS = new ServiciosSimulados();

    @TestConfiguration(proxyBeanMethods = false)
    static class RelojControlado {
        @Bean
        @Primary
        RelojDePrueba relojDePrueba() {
            return new RelojDePrueba();
        }
    }

    @DynamicPropertySource
    static void servicios(DynamicPropertyRegistry registro) {
        String base = SERVICIOS.base();
        registro.add("catalogo.productos.url", () -> base);
        registro.add("tienda.servicios.inventario", () -> base);
        registro.add("tienda.servicios.finanzas", () -> base + "/api/v1");
        registro.add("tienda.servicios.correo", () -> base);
        registro.add("tienda.servicios.identidad", () -> base);
        registro.add("tienda.servicios.parametros", () -> base + "/api/v1");
        registro.add("tienda.credencial.url", () -> base + "/api/v1/auth/token");
    }

    @AfterAll
    static void pararServicios() {
        SERVICIOS.parar();
    }

    @LocalServerPort
    private int puerto;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CompraService compras;

    @Autowired
    private RelojDePrueba reloj;

    private String uid;
    private String token;

    @BeforeEach
    void limpiar() {
        SERVICIOS.reiniciar();
        jdbc.update("delete from lineas_orden");
        jdbc.update("delete from ordenes");
        jdbc.update("delete from items_carrito");
        jdbc.update("delete from carritos");
        jdbc.update("delete from lista_deseos");
        reloj.avanzar(Duration.ofMinutes(2));
        uid = UUID.randomUUID().toString();
        token = "Bearer " + TokensDePrueba.deJugador("comprador", UUID.fromString(uid));
        // Precios en creditos del catalogo, como los de la semilla (preciosDemostracion).
        SERVICIOS.producto(ESPADA, "Espada de fuego", "45000", 300, false, 5, null);
        SERVICIOS.producto(ESCUDO, "Escudo de roble", "20000", 250, false, -1, """
                {"porcentaje":10,"desde":"2020-01-01T00:00:00Z","hasta":"2099-01-01T00:00:00Z","vigente":true}""");
        SERVICIOS.producto(HEROE_PREMIUM, "Heroe de edicion limitada", "90000", null, true, -1, null);
        SERVICIOS.saldo(uid, 1000);
    }

    // ------------------------------------------------------------ apoyo HTTP

    private RestClient cliente() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + puerto + "/ecommerce/api/v1")
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
    }

    private ResponseEntity<String> enviar(String metodo, String ruta, String cuerpo, Map<String, String> cabeceras) {
        RestClient.RequestBodySpec peticion = cliente().method(org.springframework.http.HttpMethod.valueOf(metodo))
                .uri(ruta)
                .header(HttpHeaders.AUTHORIZATION, cabeceras.getOrDefault(HttpHeaders.AUTHORIZATION, token));
        cabeceras.forEach((nombre, valor) -> {
            if (!nombre.equals(HttpHeaders.AUTHORIZATION)) {
                peticion.header(nombre, valor);
            }
        });
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).body(cuerpo);
        }
        return peticion.retrieve().onStatus(estado -> true, (p, r) -> { }).toEntity(String.class);
    }

    private ResponseEntity<String> get(String ruta) {
        return enviar("GET", ruta, null, Map.of());
    }

    private void alCarrito(String producto, int cantidad) {
        ResponseEntity<String> respuesta = enviar("POST", "/carrito/items",
                "{\"productoId\":\"" + producto + "\",\"cantidad\":" + cantidad + "}", Map.of());
        assertThat(respuesta.getStatusCode().value()).as(respuesta.getBody()).isEqualTo(200);
    }

    private ResponseEntity<String> pagarConCreditos(String clave) {
        return enviar("POST", "/checkout/creditos", null, Map.of("Idempotency-Key", clave));
    }

    private ResponseEntity<String> pagarConTarjeta(String clave) {
        return enviar("POST", "/checkout", """
                {"titular":"Ana Perez","numeroTarjeta":"%s","vencimiento":"12/30","codigoSeguridad":"123","moneda":"COP"}"""
                .formatted(TARJETA_APROBADA), Map.of("Idempotency-Key", clave));
    }

    private static String texto(ResponseEntity<String> respuesta, String ruta) {
        return JsonPath.read(respuesta.getBody(), ruta);
    }

    private static BigDecimal numero(ResponseEntity<String> respuesta, String ruta) {
        Object valor = JsonPath.read(respuesta.getBody(), ruta);
        return new BigDecimal(valor.toString());
    }

    private String estadoDe(String ordenId) {
        return jdbc.queryForObject("select estado from ordenes where id = ?::uuid", String.class, ordenId);
    }

    private int lineasDelCarrito() {
        return JsonPath.<List<Object>>read(get("/carrito").getBody(), "$.items").size();
    }

    // ---------------------------------------------------------------- pruebas

    @Test
    @DisplayName("compra completa: el saldo baja una vez y exactamente el precio en creditos; entrega una vez; sin asiento ni correo")
    void compraCompleta() {
        alCarrito(ESPADA, 2);
        alCarrito(ESCUDO, 1);

        ResponseEntity<String> respuesta = pagarConCreditos("creditos-compra-0001");

        assertThat(respuesta.getStatusCode().value()).as(respuesta.getBody()).isEqualTo(201);
        String ordenId = texto(respuesta, "$.id");
        assertThat(texto(respuesta, "$.estado")).isEqualTo("COMPLETA");
        assertThat(texto(respuesta, "$.formaDePago")).isEqualTo("CREDITOS");
        assertThat(texto(respuesta, "$.moneda")).isEqualTo("CREDITOS");
        // 2 x 300 + (250 - 10 % = 225) = 825: el precio es el precioCreditos del catalogo.
        assertThat(numero(respuesta, "$.total")).isEqualByComparingTo("825");
        assertThat(numero(respuesta, "$.lineas[0].precioUnitario")).isEqualByComparingTo("300");
        assertThat(numero(respuesta, "$.lineas[1].precioUnitario")).isEqualByComparingTo("225");
        assertThat(JsonPath.<Integer>read(respuesta.getBody(), "$.lineas[1].descuentoPorcentaje")).isEqualTo(10);
        assertThat(JsonPath.<Object>read(respuesta.getBody(), "$.medioDePago")).isNull();
        assertThat(texto(respuesta, "$.correoConfirmacion")).isEqualTo("OMITIDO");

        assertThat(SERVICIOS.debitos).containsOnlyKeys("tienda-orden-" + ordenId);
        assertThat(SERVICIOS.debitos.get("tienda-orden-" + ordenId).monto()).isEqualTo(825);
        assertThat(SERVICIOS.debitosAplicados.get()).isEqualTo(1);
        assertThat(SERVICIOS.saldoDe(uid)).isEqualTo(175);
        assertThat(SERVICIOS.unidadesDescontadas.get()).as("la espada tiene tiraje; el escudo es ilimitado")
                .isEqualTo(2);
        assertThat(SERVICIOS.entregasAplicadas).singleElement().satisfies(entrega -> {
            assertThat(JsonPath.<String>read(entrega, "$.uid")).isEqualTo(uid);
            assertThat(JsonPath.<String>read(entrega, "$.origen")).isEqualTo("COMPRA");
            assertThat(JsonPath.<String>read(entrega, "$.referencia")).isEqualTo(ordenId);
        });
        assertThat(SERVICIOS.asientos).as("el libro de moneda real no registra una compra en creditos").isEmpty();
        assertThat(SERVICIOS.correos).isEmpty();
        assertThat(SERVICIOS.sinCredencial).as("toda llamada entre servicios lleva la credencial").isEmpty();
        assertThat(lineasDelCarrito()).isZero();
        assertThat(jdbc.queryForObject("select forma_de_pago from ordenes where id = ?::uuid", String.class, ordenId))
                .isEqualTo("CREDITOS");

        ResponseEntity<String> mias = get("/ordenes");
        assertThat(JsonPath.<List<String>>read(mias.getBody(), "$[*].formaDePago")).containsExactly("CREDITOS");
    }

    @Test
    @DisplayName("la cotizacion: lineas, total, saldo actual y saldo despues, sin cobrar ni crear orden")
    void cotizacion() {
        alCarrito(ESPADA, 2);
        alCarrito(ESCUDO, 1);

        ResponseEntity<String> respuesta = get("/checkout/creditos");

        assertThat(respuesta.getStatusCode().value()).as(respuesta.getBody()).isEqualTo(200);
        assertThat(JsonPath.<Boolean>read(respuesta.getBody(), "$.pagable")).isTrue();
        assertThat(JsonPath.<List<String>>read(respuesta.getBody(), "$.lineas[*].productoId"))
                .containsExactly(ESPADA, ESCUDO);
        assertThat(numero(respuesta, "$.lineas[1].precioCreditos")).isEqualByComparingTo("225");
        assertThat(numero(respuesta, "$.totalCreditos")).isEqualByComparingTo("825");
        assertThat(numero(respuesta, "$.saldoDisponible")).isEqualByComparingTo("1000");
        assertThat(numero(respuesta, "$.saldoDespues")).isEqualByComparingTo("175");
        assertThat(JsonPath.<Boolean>read(respuesta.getBody(), "$.alcanza")).isTrue();
        assertThat(SERVICIOS.llamadasDeDebito.get()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from ordenes", Integer.class)).isZero();
    }

    @Test
    @DisplayName("la vitrina ensena el precio en creditos; null en un producto premium")
    void vitrina() {
        ResponseEntity<String> pagina = enviar("GET", "/vitrina", null, Map.of());

        assertThat(JsonPath.<List<Object>>read(pagina.getBody(),
                "$.content[?(@.id == '" + ESPADA + "')].precioCreditos")).containsExactly(300);
        assertThat(JsonPath.<List<Object>>read(pagina.getBody(),
                "$.content[?(@.id == '" + ESCUDO + "')].precioCreditos")).containsExactly(225);
        assertThat(JsonPath.<List<Object>>read(pagina.getBody(),
                "$.content[?(@.id == '" + HEROE_PREMIUM + "')].precioCreditos")).containsExactly((Object) null);
    }

    @Nested
    @DisplayName("nunca dos cobros")
    class Idempotencia {

        @Test
        @DisplayName("doble clic con la misma clave: 200 con la misma orden, sin cobrar ni entregar otra vez")
        void dobleClic() {
            alCarrito(ESPADA, 1);
            String ordenId = texto(pagarConCreditos("creditos-doble-0001"), "$.id");

            ResponseEntity<String> otraVez = pagarConCreditos("creditos-doble-0001");

            assertThat(otraVez.getStatusCode().value()).isEqualTo(200);
            assertThat(texto(otraVez, "$.id")).isEqualTo(ordenId);
            assertThat(SERVICIOS.debitosAplicados.get()).isEqualTo(1);
            assertThat(SERVICIOS.saldoDe(uid)).isEqualTo(700);
            assertThat(SERVICIOS.entregasAplicadas).hasSize(1);
        }

        @Test
        @DisplayName("dos clics a la vez con la misma clave: una orden, un cobro, una entrega")
        void dosALaVez() throws Exception {
            alCarrito(ESPADA, 1);
            CountDownLatch salida = new CountDownLatch(1);
            ExecutorService hilos = Executors.newFixedThreadPool(2);
            Callable<Integer> pago = () -> {
                salida.await();
                return pagarConCreditos("creditos-concurrente-1").getStatusCode().value();
            };
            try {
                Future<Integer> uno = hilos.submit(pago);
                Future<Integer> otro = hilos.submit(pago);
                salida.countDown();
                List<Integer> estados = new ArrayList<>(List.of(uno.get(), otro.get()));

                assertThat(estados).isSubsetOf(201, 200, 409).contains(201);
                assertThat(estados.stream().filter(e -> e == 201).count()).isEqualTo(1);
            } finally {
                hilos.shutdownNow();
            }
            assertThat(jdbc.queryForObject("select count(*) from ordenes", Integer.class)).isEqualTo(1);
            assertThat(SERVICIOS.debitosAplicados.get()).isEqualTo(1);
            assertThat(SERVICIOS.saldoDe(uid)).isEqualTo(700);
            assertThat(SERVICIOS.entregasAplicadas).hasSize(1);
        }

        @Test
        @DisplayName("ms-finanzas no responde: 503 creditos-no-disponibles, PENDIENTE; la misma clave cobra una vez")
        void finanzasCaida() {
            alCarrito(ESPADA, 1);
            SERVICIOS.fallosDeDebito.set(1);

            ResponseEntity<String> caida = pagarConCreditos("creditos-caida-00001");

            assertThat(caida.getStatusCode().value()).isEqualTo(503);
            assertThat(texto(caida, "$.type")).isEqualTo("urn:nexus:problema:creditos-no-disponibles");
            assertThat(caida.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotBlank();
            String ordenId = texto(caida, "$.ordenId");
            assertThat(estadoDe(ordenId)).isEqualTo("PENDIENTE");
            assertThat(SERVICIOS.saldoDe(uid)).isEqualTo(1000);
            assertThat(lineasDelCarrito()).isEqualTo(1);

            ResponseEntity<String> reintento = pagarConCreditos("creditos-caida-00001");

            assertThat(reintento.getStatusCode().value()).isEqualTo(201);
            assertThat(texto(reintento, "$.id")).isEqualTo(ordenId);
            assertThat(texto(reintento, "$.estado")).isEqualTo("COMPLETA");
            assertThat(SERVICIOS.debitosAplicados.get()).isEqualTo(1);
            assertThat(SERVICIOS.saldoDe(uid)).isEqualTo(700);
        }

        @Test
        @DisplayName("se cobro pero la respuesta se perdio: el reintento encuentra el mismo refId y no cobra otra vez")
        void respuestaPerdida() {
            alCarrito(ESPADA, 1);
            SERVICIOS.debitosSinRespuesta.set(1);

            ResponseEntity<String> perdida = pagarConCreditos("creditos-perdida-001");

            assertThat(perdida.getStatusCode().value()).isEqualTo(503);
            assertThat(SERVICIOS.saldoDe(uid)).as("ms-finanzas si desconto").isEqualTo(700);

            ResponseEntity<String> reintento = pagarConCreditos("creditos-perdida-001");

            assertThat(reintento.getStatusCode().value()).isEqualTo(201);
            assertThat(texto(reintento, "$.estado")).isEqualTo("COMPLETA");
            assertThat(SERVICIOS.debitosAplicados.get()).isEqualTo(1);
            assertThat(SERVICIOS.llamadasDeDebito.get()).isEqualTo(2);
            assertThat(SERVICIOS.saldoDe(uid)).isEqualTo(700);
            assertThat(SERVICIOS.entregasAplicadas).hasSize(1);
        }

        @Test
        @DisplayName("una PENDIENTE que nadie reintenta y SI se cobro: al caducar se concilia y se entrega")
        void pendienteCobradaSeConcilia() {
            alCarrito(ESPADA, 1);
            SERVICIOS.debitosSinRespuesta.set(1);
            String ordenId = texto(pagarConCreditos("creditos-concilia-01"), "$.ordenId");

            reloj.avanzar(Duration.ofMinutes(31));
            compras.reanudarPendientes();
            compras.reanudarPendientes();

            assertThat(estadoDe(ordenId)).isEqualTo("COMPLETA");
            assertThat(SERVICIOS.debitosAplicados.get()).isEqualTo(1);
            assertThat(SERVICIOS.llamadasDeDebito.get()).as("conciliar no vuelve a cobrar").isEqualTo(1);
            assertThat(SERVICIOS.saldoDe(uid)).isEqualTo(700);
            assertThat(SERVICIOS.entregasAplicadas).hasSize(1);
        }

        @Test
        @DisplayName("una PENDIENTE que nadie reintenta y NO se cobro: al caducar queda RECHAZADA sin cobrar nada")
        void pendienteSinCobroCaduca() {
            alCarrito(ESPADA, 1);
            SERVICIOS.fallosDeDebito.set(1);
            String ordenId = texto(pagarConCreditos("creditos-caduca-001"), "$.ordenId");

            reloj.avanzar(Duration.ofMinutes(31));
            compras.reanudarPendientes();

            assertThat(estadoDe(ordenId)).isEqualTo("RECHAZADA");
            assertThat(SERVICIOS.debitos).isEmpty();
            assertThat(SERVICIOS.saldoDe(uid)).isEqualTo(1000);
            assertThat(SERVICIOS.entregasAplicadas).isEmpty();
            assertThat(lineasDelCarrito()).as("nada se cobro: el carrito sigue").isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("rechazos y devolucion")
    class Rechazos {

        @Test
        @DisplayName("saldo insuficiente: 402 saldo-insuficiente, orden RECHAZADA, nada cobrado y el carrito intacto")
        void saldoInsuficiente() {
            SERVICIOS.saldo(uid, 100);
            alCarrito(ESPADA, 1);

            ResponseEntity<String> respuesta = pagarConCreditos("creditos-pobre-0001");

            assertThat(respuesta.getStatusCode().value()).isEqualTo(402);
            assertThat(texto(respuesta, "$.type")).isEqualTo("urn:nexus:problema:saldo-insuficiente");
            assertThat(texto(respuesta, "$.estado")).isEqualTo("RECHAZADA");
            assertThat(JsonPath.<Integer>read(respuesta.getBody(), "$.totalCreditos")).isEqualTo(300);
            assertThat(SERVICIOS.saldoDe(uid)).isEqualTo(100);
            assertThat(SERVICIOS.llamadasDeReserva.get()).isZero();
            assertThat(SERVICIOS.entregasAplicadas).isEmpty();
            assertThat(lineasDelCarrito()).isEqualTo(1);
        }

        @Test
        @DisplayName("se cobro y se agoto al reservar: los creditos vuelven (reverso), REEMBOLSADA y 409")
        void devolucion() {
            alCarrito(ESPADA, 1);
            SERVICIOS.agotarAlReservar = true;

            ResponseEntity<String> respuesta = pagarConCreditos("creditos-agotada-01");

            assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
            assertThat(texto(respuesta, "$.type")).isEqualTo("urn:nexus:problema:compra-reembolsada");
            assertThat(texto(respuesta, "$.estado")).isEqualTo("REEMBOLSADA");
            assertThat(SERVICIOS.reversosAplicados.get()).isEqualTo(1);
            assertThat(SERVICIOS.saldoDe(uid)).as("el saldo vuelve entero").isEqualTo(1000);
            assertThat(SERVICIOS.entregasAplicadas).isEmpty();
        }

        @Test
        @DisplayName("la devolucion espera si ms-finanzas cae, y la tarea la termina una sola vez")
        void devolucionConCaida() {
            alCarrito(ESPADA, 1);
            SERVICIOS.agotarAlReservar = true;
            SERVICIOS.fallosDeReverso.set(1);

            ResponseEntity<String> respuesta = pagarConCreditos("creditos-agotada-02");
            assertThat(respuesta.getStatusCode().value()).as("la devolucion esta en curso").isEqualTo(409);
            String ordenId = texto(respuesta, "$.ordenId");
            assertThat(estadoDe(ordenId)).isEqualTo("COMPENSACION_PENDIENTE");
            assertThat(SERVICIOS.saldoDe(uid)).isEqualTo(700);

            compras.reanudarPendientes();
            compras.reanudarPendientes();

            assertThat(estadoDe(ordenId)).isEqualTo("REEMBOLSADA");
            assertThat(SERVICIOS.reversosAplicados.get()).isEqualTo(1);
            assertThat(SERVICIOS.saldoDe(uid)).isEqualTo(1000);
        }

        @Test
        @DisplayName("un producto sin precio en creditos (premium): 409, sin orden y sin cobro")
        void sinPrecioEnCreditos() {
            alCarrito(ESPADA, 1);
            alCarrito(HEROE_PREMIUM, 1);

            ResponseEntity<String> respuesta = pagarConCreditos("creditos-premium-01");

            assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
            assertThat(texto(respuesta, "$.type")).isEqualTo("urn:nexus:problema:producto-sin-precio-en-creditos");
            assertThat(jdbc.queryForObject("select count(*) from ordenes", Integer.class)).isZero();
            assertThat(SERVICIOS.llamadasDeDebito.get()).isZero();
            ResponseEntity<String> cotizacion = get("/checkout/creditos");
            assertThat(JsonPath.<Boolean>read(cotizacion.getBody(), "$.pagable")).isFalse();
            assertThat(texto(cotizacion, "$.motivo")).isEqualTo("SIN_PRECIO_EN_CREDITOS");
        }

        @Test
        @DisplayName("una clave de un pago con tarjeta no sirve para pagar con creditos, ni al reves: 409")
        void claveDeOtraFormaDePago() {
            alCarrito(ESPADA, 1);
            assertThat(pagarConTarjeta("clave-de-tarjeta-001").getStatusCode().value()).isEqualTo(201);
            alCarrito(ESCUDO, 1);

            ResponseEntity<String> conCreditos = pagarConCreditos("clave-de-tarjeta-001");

            assertThat(conCreditos.getStatusCode().value()).isEqualTo(409);
            assertThat(texto(conCreditos, "$.type")).isEqualTo("urn:nexus:problema:clave-de-idempotencia-reutilizada");
            assertThat(SERVICIOS.llamadasDeDebito.get()).isZero();

            assertThat(pagarConCreditos("clave-de-creditos-01").getStatusCode().value()).isEqualTo(201);
            alCarrito(HEROE_PREMIUM, 1);
            ResponseEntity<String> conTarjeta = pagarConTarjeta("clave-de-creditos-01");
            assertThat(conTarjeta.getStatusCode().value()).isEqualTo(409);
            assertThat(texto(conTarjeta, "$.type")).isEqualTo("urn:nexus:problema:clave-de-idempotencia-reutilizada");
        }

        @Test
        @DisplayName("carrito vacio: 400 carrito-vacio; sin Idempotency-Key: 400")
        void validaciones() {
            ResponseEntity<String> vacio = pagarConCreditos("creditos-vacio-0001");
            ResponseEntity<String> sinClave = enviar("POST", "/checkout/creditos", null, Map.of());

            assertThat(vacio.getStatusCode().value()).isEqualTo(400);
            assertThat(texto(vacio, "$.type")).isEqualTo("urn:nexus:problema:carrito-vacio");
            assertThat(sinClave.getStatusCode().value()).isEqualTo(400);
            assertThat(texto(sinClave, "$.type")).isEqualTo("urn:nexus:problema:clave-de-idempotencia-requerida");
        }

        @Test
        @DisplayName("sin token 401; con token de servicio 403 (un servicio no compra)")
        void seguridad() {
            String deServicio = "Bearer " + TokensDePrueba.deServicio("salas-partidas");
            ResponseEntity<String> sinToken = cliente().post().uri("/checkout/creditos").header("Idempotency-Key",
                    "creditos-anonimo-01").retrieve().onStatus(e -> true, (p, r) -> { }).toEntity(String.class);
            ResponseEntity<String> servicio = enviar("POST", "/checkout/creditos", null,
                    Map.of(HttpHeaders.AUTHORIZATION, deServicio, "Idempotency-Key", "creditos-servicio-1"));
            ResponseEntity<String> cotizacionDeServicio = enviar("GET", "/checkout/creditos", null,
                    Map.of(HttpHeaders.AUTHORIZATION, deServicio));

            assertThat(sinToken.getStatusCode().value()).isEqualTo(401);
            assertThat(servicio.getStatusCode().value()).isEqualTo(403);
            assertThat(cotizacionDeServicio.getStatusCode().value()).isEqualTo(403);
            assertThat(SERVICIOS.llamadasDeDebito.get()).isZero();
        }
    }
}
