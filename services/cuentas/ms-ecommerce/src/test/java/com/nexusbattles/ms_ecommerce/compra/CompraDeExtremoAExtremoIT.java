package com.nexusbattles.ms_ecommerce.compra;

import com.jayway.jsonpath.JsonPath;
import com.nexusbattles.ms_ecommerce.compra.pago.PasarelaSimulada;
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
 * La compra de punta a punta dentro del servicio (B5): PostgreSQL de verdad con
 * Flyway (V1..V4), Tomcat, la cadena de seguridad con tokens firmados, los
 * clientes HTTP con sus tiempos de espera y la credencial de servicio pedida
 * por client_credentials. Lo unico simulado son los seis servicios con los que
 * habla la compra ({@link ServiciosSimulados}), que contestan como sus
 * contratos y cuentan los efectos de verdad.
 *
 * <p>Lo que se prueba a proposito es la duplicacion: la misma clave dos veces,
 * dos peticiones a la vez, y el reintento tras una caida en cada paso (reserva,
 * entrega, asiento, correo) — siempre con un cobro, una reserva por unidad y
 * una entrega.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "tienda.ordenes.reanudacion.habilitada=false",
        "tienda.ordenes.reintento-inicial=0s",
        "tienda.credencial.client-id=ms-ecommerce",
        "tienda.credencial.client-secret=secreto-de-prueba-de-la-tienda"})
@Testcontainers
@Import({TokensDePrueba.Decodificador.class, CompraDeExtremoAExtremoIT.RelojControlado.class})
@DisplayName("Compra de punta a punta: orden, pasarela simulada, reserva, entrega, asiento y correo")
class CompraDeExtremoAExtremoIT {

    private static final String TARJETA_APROBADA = "4242 4242 4242 4242";
    private static final String TARJETA_RECHAZADA = "4000 0000 0000 0002";
    private static final String TARJETA_CAIDA = "4000 0000 0000 0069";
    private static final String ESPADA = "5b0a3c1e-8d7f-4e2a-9c6b-1f0e2d3c4b5a";
    private static final String ESCUDO = "0e9d8c7b-6a5f-4e3d-8c2b-1a0f9e8d7c6b";

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
    private PasarelaSimulada pasarela;

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
        // Las copias de 30 s (catalogo, inventario) y la de un minuto (tasas)
        // no pasan de una prueba a otra.
        reloj.avanzar(Duration.ofMinutes(2));
        uid = UUID.randomUUID().toString();
        token = "Bearer " + TokensDePrueba.deJugador("comprador", UUID.fromString(uid));
        SERVICIOS.producto(ESPADA, "Espada de fuego", "45000", 5);
        SERVICIOS.producto(ESCUDO, "Escudo de roble", "20000", -1, """
                {"porcentaje":10,"desde":"2020-01-01T00:00:00Z","hasta":"2099-01-01T00:00:00Z","vigente":true}""");
    }

    // ------------------------------------------------------------ apoyo HTTP

    /**
     * Con la fabrica del JDK: el classpath de pruebas trae Apache HttpClient 5
     * (Pact), que repetiria solo un 503 con {@code Retry-After}.
     */
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

    private static String pago(String tarjeta, String moneda) {
        return """
                {"titular":"Ana Perez","numeroTarjeta":"%s","vencimiento":"12/30","codigoSeguridad":"123","moneda":"%s"}"""
                .formatted(tarjeta, moneda);
    }

    private ResponseEntity<String> pagar(String clave, String tarjeta) {
        return pagar(clave, tarjeta, "COP");
    }

    private ResponseEntity<String> pagar(String clave, String tarjeta, String moneda) {
        return enviar("POST", "/checkout", pago(tarjeta, moneda), Map.of("Idempotency-Key", clave));
    }

    private static String texto(ResponseEntity<String> respuesta, String ruta) {
        return JsonPath.read(respuesta.getBody(), ruta);
    }

    private static Integer entero(ResponseEntity<String> respuesta, String ruta) {
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
    @DisplayName("compra completa en COP: un cobro, una reserva por unidad, una entrega, asiento, correo y carrito vacio")
    void compraCompleta() {
        alCarrito(ESPADA, 2);
        alCarrito(ESCUDO, 1);
        long cobrosAntes = pasarela.cobrosAprobados();

        ResponseEntity<String> respuesta = pagar("clave-de-compra-0001", TARJETA_APROBADA);

        assertThat(respuesta.getStatusCode().value()).as(respuesta.getBody()).isEqualTo(201);
        String ordenId = texto(respuesta, "$.id");
        assertThat(texto(respuesta, "$.estado")).isEqualTo("COMPLETA");
        assertThat(texto(respuesta, "$.moneda")).isEqualTo("COP");
        // 2 x 45000 + (20000 - 10 %) = 108000: el precio lo calcula el servidor.
        assertThat(numero(respuesta, "$.total")).isEqualByComparingTo("108000");
        assertThat(entero(respuesta, "$.lineas[1].descuentoPorcentaje")).isEqualTo(10);
        assertThat(texto(respuesta, "$.medioDePago.marca")).isEqualTo("VISA");
        assertThat(texto(respuesta, "$.medioDePago.ultimos4")).isEqualTo("4242");
        assertThat(texto(respuesta, "$.correoConfirmacion")).isEqualTo("ENVIADO");

        assertThat(pasarela.cobrosAprobados() - cobrosAntes).isEqualTo(1);
        assertThat(SERVICIOS.llamadasDeReserva.get()).isEqualTo(3);
        assertThat(SERVICIOS.unidadesDescontadas.get()).as("el escudo es ilimitado").isEqualTo(2);
        assertThat(SERVICIOS.reservas).containsKeys("orden-" + ordenId + "-l1-u1", "orden-" + ordenId + "-l1-u2",
                "orden-" + ordenId + "-l2-u1");
        assertThat(SERVICIOS.entregasAplicadas).singleElement().satisfies(entrega -> {
            assertThat(JsonPath.<String>read(entrega, "$.uid")).isEqualTo(uid);
            assertThat(JsonPath.<String>read(entrega, "$.origen")).isEqualTo("COMPRA");
            assertThat(JsonPath.<String>read(entrega, "$.referencia")).isEqualTo(ordenId);
        });
        assertThat(SERVICIOS.entregas).containsOnlyKeys("orden-" + ordenId);
        assertThat(SERVICIOS.asientos).containsOnlyKeys(ordenId);
        String asiento = SERVICIOS.asientos.get(ordenId);
        assertThat(JsonPath.<String>read(asiento, "$.resultado")).isEqualTo("APROBADO");
        assertThat(JsonPath.<String>read(asiento, "$.uidUsuario")).isEqualTo(uid);
        assertThat(new BigDecimal(JsonPath.read(asiento, "$.monto").toString())).isEqualByComparingTo("108000");
        assertThat(SERVICIOS.correos).containsOnlyKeys("compra-" + ordenId);
        String correo = SERVICIOS.correos.get("compra-" + ordenId);
        assertThat(JsonPath.<String>read(correo, "$.email")).isEqualTo("comprador@nexus.test");
        assertThat(JsonPath.<List<Object>>read(correo, "$.lineas")).hasSize(2);
        assertThat(JsonPath.<String>read(correo, "$.orden")).isEqualTo(ordenId);
        assertThat(JsonPath.<String>read(correo, "$.fechaHora")).endsWith("-05:00");
        assertThat(SERVICIOS.sinCredencial).as("toda llamada entre servicios lleva la credencial").isEmpty();
        // Regla 5: todas las llamadas de la compra llevan la traza de la peticion que la empezo.
        assertThat(SERVICIOS.trazas.stream()
                .filter(linea -> linea.contains("/adquisiciones") || linea.contains("/entregas")
                        || linea.contains("/transacciones") || linea.contains("/contacto") || linea.contains("/correos/"))
                .map(linea -> linea.split(" ")[1].split("-")[1])
                .distinct()
                .toList()).hasSize(1);

        assertThat(lineasDelCarrito()).isZero();
        // Del medio de pago solo quedan marca y cuatro ultimos.
        String fila = jdbc.queryForObject("select row_to_json(o)::text from ordenes o where id = ?::uuid",
                String.class, ordenId);
        assertThat(fila).doesNotContain("4242424242424242").doesNotContain("4242 4242").doesNotContain("\"123\"")
                .doesNotContain("Ana Perez");
    }

    @Test
    @DisplayName("la misma clave otra vez: 200 con la misma orden, sin cobrar, reservar ni entregar de nuevo")
    void mismaClaveNoRepite() {
        alCarrito(ESPADA, 1);
        String ordenId = texto(pagar("clave-repetida-0001", TARJETA_APROBADA), "$.id");
        long cobros = pasarela.cobrosAprobados();
        int reservas = SERVICIOS.llamadasDeReserva.get();

        // El carrito vuelve a tener algo: la clave sigue siendo la misma compra.
        // Otro producto: la espada ya es suya y no se puede volver a añadir
        // (RF-CAR-004, contrato 1.5.0).
        alCarrito(ESCUDO, 1);
        ResponseEntity<String> repetida = pagar("clave-repetida-0001", TARJETA_APROBADA);

        assertThat(repetida.getStatusCode().value()).isEqualTo(200);
        assertThat(texto(repetida, "$.id")).isEqualTo(ordenId);
        assertThat(pasarela.cobrosAprobados()).isEqualTo(cobros);
        assertThat(SERVICIOS.llamadasDeReserva.get()).isEqualTo(reservas);
        assertThat(SERVICIOS.entregasAplicadas).hasSize(1);
        assertThat(lineasDelCarrito()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from ordenes", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("dos pagos simultaneos con la misma clave: una sola orden, un solo cobro y una sola entrega")
    void dosPagosSimultaneos() throws Exception {
        alCarrito(ESPADA, 1);
        long cobrosAntes = pasarela.cobrosAprobados();
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        Callable<Integer> pago = () -> {
            salida.await();
            return pagar("clave-concurrente-0001", TARJETA_APROBADA).getStatusCode().value();
        };
        try {
            Future<Integer> uno = hilos.submit(pago);
            Future<Integer> otro = hilos.submit(pago);
            salida.countDown();
            List<Integer> estados = new ArrayList<>(List.of(uno.get(), otro.get()));

            // La otra: 200 si la primera ya termino, o 409 compra-en-curso si aun la tenia.
            assertThat(estados).isSubsetOf(201, 200, 409).contains(201);
            assertThat(estados.stream().filter(e -> e == 201).count()).isEqualTo(1);
        } finally {
            hilos.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select count(*) from ordenes", Integer.class)).isEqualTo(1);
        assertThat(pasarela.cobrosAprobados() - cobrosAntes).isEqualTo(1);
        assertThat(SERVICIOS.entregasAplicadas).hasSize(1);
        assertThat(SERVICIOS.unidadesDescontadas.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("con un pago en proceso, otro con otra clave espera: 409 compra-en-curso")
    void otraClaveConUnPagoEnCurso() {
        alCarrito(ESPADA, 1);
        // Una orden del jugador con la concesion tomada: un pago en proceso.
        jdbc.update("""
                insert into ordenes (id, usuario_id, clave_idempotencia, estado, moneda, total, creada_en,
                                     bloqueada_hasta, version)
                values (?::uuid, ?, 'otra-clave-0001', 'PENDIENTE', 'COP', 1000, now(), ?, 0)""",
                UUID.randomUUID().toString(), uid, java.sql.Timestamp.from(reloj.instant().plusSeconds(60)));

        ResponseEntity<String> respuesta = pagar("clave-nueva-00001", TARJETA_APROBADA);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
        assertThat(texto(respuesta, "$.type")).isEqualTo("urn:nexus:problema:compra-en-curso");
        assertThat(respuesta.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotBlank();
    }

    @Test
    @DisplayName("tarjeta 0002: 402 pago-rechazado, orden RECHAZADA con asiento RECHAZADO y el carrito intacto")
    void rechazoDeLaPasarela() {
        alCarrito(ESPADA, 1);

        ResponseEntity<String> respuesta = pagar("clave-rechazada-01", TARJETA_RECHAZADA);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(402);
        assertThat(texto(respuesta, "$.type")).isEqualTo("urn:nexus:problema:pago-rechazado");
        assertThat(texto(respuesta, "$.estado")).isEqualTo("RECHAZADA");
        assertThat(texto(respuesta, "$.motivo")).contains("fondos insuficientes");
        String ordenId = texto(respuesta, "$.ordenId");
        assertThat(estadoDe(ordenId)).isEqualTo("RECHAZADA");
        assertThat(lineasDelCarrito()).isEqualTo(1);
        assertThat(SERVICIOS.llamadasDeReserva.get()).isZero();
        assertThat(SERVICIOS.entregasAplicadas).isEmpty();
        assertThat(JsonPath.<String>read(SERVICIOS.asientos.get(ordenId), "$.resultado")).isEqualTo("RECHAZADO");
        assertThat(SERVICIOS.correos).isEmpty();

        // La misma clave devuelve la orden rechazada (200); otro intento lleva otra clave.
        assertThat(pagar("clave-rechazada-01", TARJETA_APROBADA).getStatusCode().value()).isEqualTo(200);
        assertThat(pagar("clave-otro-intento", TARJETA_APROBADA).getStatusCode().value()).isEqualTo(201);
    }

    @Test
    @DisplayName("tarjeta 0069: 503 pasarela-no-disponible, orden PENDIENTE; la misma clave reintenta y cobra una vez")
    void pasarelaCaidaYReintento() {
        alCarrito(ESPADA, 1);
        long cobrosAntes = pasarela.cobrosAprobados();

        ResponseEntity<String> caida = pagar("clave-caida-000001", TARJETA_CAIDA);

        assertThat(caida.getStatusCode().value()).isEqualTo(503);
        assertThat(texto(caida, "$.type")).isEqualTo("urn:nexus:problema:pasarela-no-disponible");
        assertThat(caida.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
        String ordenId = texto(caida, "$.ordenId");
        assertThat(estadoDe(ordenId)).isEqualTo("PENDIENTE");
        assertThat(lineasDelCarrito()).isEqualTo(1);

        ResponseEntity<String> reintento = pagar("clave-caida-000001", TARJETA_APROBADA);

        assertThat(reintento.getStatusCode().value()).isEqualTo(201);
        assertThat(texto(reintento, "$.id")).isEqualTo(ordenId);
        assertThat(texto(reintento, "$.estado")).isEqualTo("COMPLETA");
        assertThat(pasarela.cobrosAprobados() - cobrosAntes).isEqualTo(1);
        assertThat(SERVICIOS.entregasAplicadas).hasSize(1);
    }

    @Test
    @DisplayName("una PENDIENTE que nadie reintenta caduca a los 30 minutos: RECHAZADA, sin asiento")
    void pendienteCaduca() {
        alCarrito(ESPADA, 1);
        String ordenId = texto(pagar("clave-caduca-00001", TARJETA_CAIDA), "$.ordenId");

        reloj.avanzar(Duration.ofMinutes(31));
        compras.reanudarPendientes();

        assertThat(estadoDe(ordenId)).isEqualTo("RECHAZADA");
        assertThat(jdbc.queryForObject("select asiento from ordenes where id = ?::uuid", String.class, ordenId))
                .isEqualTo("NO_APLICA");
        assertThat(SERVICIOS.asientos).isEmpty();
    }

    @Nested
    @DisplayName("una averia despues de cobrar: la orden espera y la tarea la termina con las mismas claves")
    class Reanudacion {

        @Test
        @DisplayName("el catalogo cae a mitad de la reserva: se retoma desde la unidad que faltaba")
        void reservaCaida() {
            alCarrito(ESPADA, 3);
            SERVICIOS.fallosDeReserva.set(1);

            ResponseEntity<String> respuesta = pagar("clave-reserva-caida", TARJETA_APROBADA);

            assertThat(respuesta.getStatusCode().value()).isEqualTo(201);
            String ordenId = texto(respuesta, "$.id");
            assertThat(texto(respuesta, "$.estado")).isEqualTo("COBRADA");
            assertThat(lineasDelCarrito()).as("lo cobrado sale del carrito aunque falte entregar").isZero();

            assertThat(compras.reanudarPendientes()).isEqualTo(1);

            assertThat(estadoDe(ordenId)).isEqualTo("COMPLETA");
            assertThat(SERVICIOS.unidadesDescontadas.get()).isEqualTo(3);
            assertThat(SERVICIOS.entregasAplicadas).hasSize(1);
        }

        @Test
        @DisplayName("el inventario cae al entregar: reintento con la misma clave, una sola entrega")
        void entregaCaida() {
            alCarrito(ESPADA, 1);
            SERVICIOS.fallosDeEntrega.set(1);
            long cobros = pasarela.cobrosAprobados();

            String ordenId = texto(pagar("clave-entrega-caida", TARJETA_APROBADA), "$.id");
            assertThat(estadoDe(ordenId)).isEqualTo("COBRADA");

            compras.reanudarPendientes();

            assertThat(estadoDe(ordenId)).isEqualTo("COMPLETA");
            assertThat(SERVICIOS.llamadasDeEntrega.get()).isEqualTo(2);
            assertThat(SERVICIOS.entregasAplicadas).hasSize(1);
            assertThat(SERVICIOS.llamadasDeReserva.get()).as("la reserva ya estaba hecha").isEqualTo(1);
            assertThat(pasarela.cobrosAprobados() - cobros).isEqualTo(1);
            assertThat(jdbc.queryForObject("select intentos from ordenes where id = ?::uuid", Integer.class, ordenId))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("ms-finanzas y correo caen: ENTREGADA; al retomar, un asiento y un correo")
        void asientoYCorreoCaidos() {
            alCarrito(ESPADA, 1);
            SERVICIOS.fallosDeAsiento.set(1);
            SERVICIOS.fallosDeCorreo.set(1);

            ResponseEntity<String> respuesta = pagar("clave-asiento-caido", TARJETA_APROBADA);
            String ordenId = texto(respuesta, "$.id");
            assertThat(texto(respuesta, "$.estado")).isEqualTo("ENTREGADA");
            assertThat(texto(respuesta, "$.correoConfirmacion")).isEqualTo("PENDIENTE");

            compras.reanudarPendientes();

            assertThat(estadoDe(ordenId)).isEqualTo("COMPLETA");
            assertThat(SERVICIOS.asientos).hasSize(1);
            assertThat(SERVICIOS.correos).hasSize(1);
            assertThat(SERVICIOS.llamadasDeCorreo.get()).isEqualTo(2);
            assertThat(SERVICIOS.entregasAplicadas).hasSize(1);
        }

        @Test
        @DisplayName("mientras no toca el reintento, la tarea no la retoma")
        void respetaLaEspera() {
            alCarrito(ESPADA, 1);
            SERVICIOS.fallosDeEntrega.set(5);
            String ordenId = texto(pagar("clave-espera-000001", TARJETA_APROBADA), "$.id");
            jdbc.update("update ordenes set proximo_intento_en = ? where id = ?::uuid",
                    java.sql.Timestamp.from(reloj.instant().plusSeconds(600)), ordenId);

            assertThat(compras.reanudarPendientes()).isZero();
            assertThat(estadoDe(ordenId)).isEqualTo("COBRADA");
        }
    }

    @Nested
    @DisplayName("compensacion: se cobro y no se pudo entregar")
    class Compensacion {

        @Test
        @DisplayName("se agoto al reservar: reembolso, REEMBOLSADA con motivo y 409 compra-reembolsada")
        void agotadoAlReservar() {
            alCarrito(ESPADA, 1);
            SERVICIOS.agotarAlReservar = true;
            long reembolsos = pasarela.reembolsos();

            ResponseEntity<String> respuesta = pagar("clave-agotada-0001", TARJETA_APROBADA);

            assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
            assertThat(texto(respuesta, "$.type")).isEqualTo("urn:nexus:problema:compra-reembolsada");
            assertThat(texto(respuesta, "$.estado")).isEqualTo("REEMBOLSADA");
            assertThat(texto(respuesta, "$.motivo")).contains("se agotó");
            assertThat(pasarela.reembolsos() - reembolsos).isEqualTo(1);
            assertThat(SERVICIOS.entregasAplicadas).isEmpty();
            assertThat(SERVICIOS.asientos).as("el libro no tiene reembolsos: no se escribe el APROBADO").isEmpty();
            assertThat(jdbc.queryForObject("select correo from ordenes where id = ?::uuid", String.class,
                    texto(respuesta, "$.ordenId"))).isEqualTo("OMITIDO");
        }

        @Test
        @DisplayName("el inventario se niega a entregar (producto suspendido): tambien se compensa")
        void entregaRechazada() {
            alCarrito(ESPADA, 1);
            SERVICIOS.rechazoDeEntrega = 409;

            ResponseEntity<String> respuesta = pagar("clave-no-entrega-01", TARJETA_APROBADA);

            assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
            assertThat(texto(respuesta, "$.estado")).isEqualTo("REEMBOLSADA");
        }
    }

    @Nested
    @DisplayName("precio del servidor y validaciones antes de cobrar")
    class Validaciones {

        @Test
        @DisplayName("en USD con la tasa de admin-parametros; el asiento y el correo van en USD")
        void enDolares() {
            SERVICIOS.tasaUsd = "4000";
            alCarrito(ESPADA, 1);

            ResponseEntity<String> respuesta = pagar("clave-dolares-0001", TARJETA_APROBADA, "USD");

            assertThat(respuesta.getStatusCode().value()).as(respuesta.getBody()).isEqualTo(201);
            assertThat(texto(respuesta, "$.moneda")).isEqualTo("USD");
            assertThat(numero(respuesta, "$.total")).isEqualByComparingTo("11.25");
            assertThat(numero(respuesta, "$.tasaDeCambio")).isEqualByComparingTo("4000");
            String ordenId = texto(respuesta, "$.id");
            assertThat(JsonPath.<String>read(SERVICIOS.asientos.get(ordenId), "$.moneda")).isEqualTo("USD");
            assertThat(JsonPath.<String>read(SERVICIOS.correos.get("compra-" + ordenId), "$.moneda")).isEqualTo("USD");
        }

        @Test
        @DisplayName("USD sin tasa: 422 moneda-no-disponible y no se crea orden")
        void dolaresSinTasa() {
            alCarrito(ESPADA, 1);

            ResponseEntity<String> respuesta = pagar("clave-sin-tasa-001", TARJETA_APROBADA, "USD");

            assertThat(respuesta.getStatusCode().value()).isEqualTo(422);
            assertThat(texto(respuesta, "$.type")).isEqualTo("urn:nexus:problema:moneda-no-disponible");
            assertThat(jdbc.queryForObject("select count(*) from ordenes", Integer.class)).isZero();
        }

        @Test
        @DisplayName("sin Idempotency-Key o demasiado corta: 400 clave-de-idempotencia-requerida")
        void sinClave() {
            alCarrito(ESPADA, 1);

            ResponseEntity<String> sinClave = enviar("POST", "/checkout", pago(TARJETA_APROBADA, "COP"), Map.of());
            ResponseEntity<String> corta = pagar("corta", TARJETA_APROBADA);

            assertThat(sinClave.getStatusCode().value()).isEqualTo(400);
            assertThat(JsonPath.<String>read(sinClave.getBody(), "$.type"))
                    .isEqualTo("urn:nexus:problema:clave-de-idempotencia-requerida");
            assertThat(corta.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        @DisplayName("tarjeta que no pasa Luhn: 400 datos-de-pago-invalidos con el campo y sin el numero")
        void tarjetaInvalida() {
            alCarrito(ESPADA, 1);

            ResponseEntity<String> respuesta = pagar("clave-luhn-000001", "4242 4242 4242 4241");

            assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
            assertThat(texto(respuesta, "$.type")).isEqualTo("urn:nexus:problema:datos-de-pago-invalidos");
            assertThat(texto(respuesta, "$.campo")).isEqualTo("numeroTarjeta");
            assertThat(respuesta.getBody()).doesNotContain("4241");
            assertThat(jdbc.queryForObject("select count(*) from ordenes", Integer.class)).isZero();
        }

        @Test
        @DisplayName("carrito vacio: 400 carrito-vacio")
        void carritoVacio() {
            ResponseEntity<String> respuesta = pagar("clave-vacio-000001", TARJETA_APROBADA);

            assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
            assertThat(texto(respuesta, "$.type")).isEqualTo("urn:nexus:problema:carrito-vacio");
        }

        @Test
        @DisplayName("un producto que se agoto antes de pagar: 409 y ni orden ni cobro")
        void agotadoAntesDePagar() {
            alCarrito(ESPADA, 1);
            SERVICIOS.tirajes.get(ESPADA).set(0);
            long cobros = pasarela.cobrosAprobados();

            ResponseEntity<String> respuesta = pagar("clave-agotado-0001", TARJETA_APROBADA);

            assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
            assertThat(texto(respuesta, "$.type")).isEqualTo("urn:nexus:problema:producto-agotado");
            assertThat(jdbc.queryForObject("select count(*) from ordenes", Integer.class)).isZero();
            assertThat(pasarela.cobrosAprobados()).isEqualTo(cobros);
        }

        @Test
        @DisplayName("la misma clave con otra moneda: 409 clave-de-idempotencia-reutilizada")
        void mismaClaveOtraMoneda() {
            SERVICIOS.tasaUsd = "4000";
            alCarrito(ESPADA, 1);
            pagar("clave-dos-monedas-1", TARJETA_APROBADA, "COP");

            ResponseEntity<String> respuesta = pagar("clave-dos-monedas-1", TARJETA_APROBADA, "USD");

            assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
            assertThat(texto(respuesta, "$.type"))
                    .isEqualTo("urn:nexus:problema:clave-de-idempotencia-reutilizada");
        }
    }

    @Nested
    @DisplayName("ordenes del jugador")
    class Ordenes {

        @Test
        @DisplayName("GET /ordenes lista las suyas, la mas reciente primero; la de otro es 404")
        void propias() {
            alCarrito(ESPADA, 1);
            String primera = texto(pagar("clave-orden-uno-01", TARJETA_APROBADA), "$.id");
            reloj.avanzar(Duration.ofSeconds(5));
            alCarrito(ESCUDO, 1);
            String segunda = texto(pagar("clave-orden-dos-01", TARJETA_APROBADA), "$.id");

            ResponseEntity<String> lista = get("/ordenes");
            assertThat(JsonPath.<List<String>>read(lista.getBody(), "$[*].id")).containsExactly(segunda, primera);
            assertThat(get("/ordenes/" + primera).getStatusCode().value()).isEqualTo(200);

            String otro = "Bearer " + TokensDePrueba.deJugador("otro", UUID.randomUUID());
            ResponseEntity<String> ajena = enviar("GET", "/ordenes/" + primera, null,
                    Map.of(HttpHeaders.AUTHORIZATION, otro));
            assertThat(ajena.getStatusCode().value()).isEqualTo(404);
            assertThat(JsonPath.<String>read(ajena.getBody(), "$.type")).isEqualTo("urn:nexus:problema:orden-inexistente");
            assertThat(get("/ordenes/no-es-un-id").getStatusCode().value()).isEqualTo(404);
            assertThat(JsonPath.<List<Object>>read(enviar("GET", "/ordenes", null,
                    Map.of(HttpHeaders.AUTHORIZATION, otro)).getBody(), "$")).isEmpty();
        }

        @Test
        @DisplayName("sin token 401; con token de servicio 403")
        void seguridad() {
            ResponseEntity<String> sinToken = cliente().get().uri("/ordenes").retrieve()
                    .onStatus(e -> true, (p, r) -> { }).toEntity(String.class);
            ResponseEntity<String> deServicio = enviar("POST", "/checkout", pago(TARJETA_APROBADA, "COP"),
                    Map.of(HttpHeaders.AUTHORIZATION, "Bearer " + TokensDePrueba.deServicio("salas-partidas"),
                            "Idempotency-Key", "clave-de-servicio-1"));

            assertThat(sinToken.getStatusCode().value()).isEqualTo(401);
            assertThat(deServicio.getStatusCode().value()).isEqualTo(403);
        }
    }

    @Nested
    @DisplayName("carrito, lista de deseos y vitrina con sesion")
    class CarritoYDeseos {

        @Test
        @DisplayName("PUT cantidad fija la cantidad, respeta 1..20 y el tiraje; el total lo recalcula el servidor")
        void cantidad() {
            alCarrito(ESPADA, 1);
            Number itemId = JsonPath.read(get("/carrito").getBody(), "$.items[0].id");

            ResponseEntity<String> tres = enviar("PUT", "/carrito/items/" + itemId + "/cantidad",
                    "{\"cantidad\":3}", Map.of());
            ResponseEntity<String> seis = enviar("PUT", "/carrito/items/" + itemId + "/cantidad",
                    "{\"cantidad\":6}", Map.of());
            ResponseEntity<String> cero = enviar("PUT", "/carrito/items/" + itemId + "/cantidad",
                    "{\"cantidad\":0}", Map.of());
            ResponseEntity<String> ajena = enviar("PUT", "/carrito/items/99999/cantidad", "{\"cantidad\":2}", Map.of());

            assertThat(tres.getStatusCode().value()).isEqualTo(200);
            assertThat(new BigDecimal(JsonPath.read(tres.getBody(), "$.total").toString())).isEqualByComparingTo("135000");
            assertThat(JsonPath.<Integer>read(tres.getBody(), "$.items[0].maximo")).isEqualTo(5);
            assertThat(seis.getStatusCode().value()).isEqualTo(409);
            assertThat(JsonPath.<String>read(seis.getBody(), "$.type")).isEqualTo("urn:nexus:problema:tiraje-insuficiente");
            assertThat(JsonPath.<Integer>read(seis.getBody(), "$.disponibles")).isEqualTo(5);
            assertThat(cero.getStatusCode().value()).isEqualTo(400);
            assertThat(JsonPath.<String>read(cero.getBody(), "$.type")).isEqualTo("urn:nexus:problema:cantidad-fuera-de-rango");
            assertThat(ajena.getStatusCode().value()).isEqualTo(404);
            assertThat(JsonPath.<Integer>read(get("/carrito").getBody(), "$.items[0].cantidad")).isEqualTo(3);
        }

        @Test
        @DisplayName("el carrito en USD convierte precios y total")
        void carritoEnDolares() {
            SERVICIOS.tasaUsd = "4000";
            alCarrito(ESPADA, 2);

            ResponseEntity<String> carrito = get("/carrito?moneda=USD");

            assertThat(JsonPath.<String>read(carrito.getBody(), "$.moneda")).isEqualTo("USD");
            assertThat(new BigDecimal(JsonPath.read(carrito.getBody(), "$.total").toString())).isEqualByComparingTo("22.50");
        }

        @Test
        @DisplayName("lista de deseos: PUT y DELETE idempotentes, GET la devuelve con nombre; un id que no existe es 404")
        void listaDeDeseos() {
            assertThat(enviar("PUT", "/lista-deseos/" + ESPADA, null, Map.of()).getStatusCode().value()).isEqualTo(200);
            ResponseEntity<String> otraVez = enviar("PUT", "/lista-deseos/" + ESPADA, null, Map.of());
            assertThat(otraVez.getStatusCode().value()).isEqualTo(200);
            assertThat(JsonPath.<String>read(otraVez.getBody(), "$.nombre")).isEqualTo("Espada de fuego");

            ResponseEntity<String> lista = get("/lista-deseos");
            assertThat(JsonPath.<List<String>>read(lista.getBody(), "$[*].productoId")).containsExactly(ESPADA);

            ResponseEntity<String> inexistente = enviar("PUT", "/lista-deseos/no-existe", null, Map.of());
            assertThat(inexistente.getStatusCode().value()).isEqualTo(404);
            assertThat(JsonPath.<String>read(inexistente.getBody(), "$.type"))
                    .isEqualTo("urn:nexus:problema:producto-inexistente");

            assertThat(enviar("DELETE", "/lista-deseos/" + ESPADA, null, Map.of()).getStatusCode().value()).isEqualTo(204);
            assertThat(enviar("DELETE", "/lista-deseos/" + ESPADA, null, Map.of()).getStatusCode().value()).isEqualTo(204);
            assertThat(JsonPath.<List<Object>>read(get("/lista-deseos").getBody(), "$")).isEmpty();
        }

        @Test
        @DisplayName("la vitrina con sesion marca lo deseado y lo que ya se compro; sin sesion, nada")
        void marcasEnLaVitrina() {
            enviar("PUT", "/lista-deseos/" + ESCUDO, null, Map.of());
            alCarrito(ESPADA, 1);
            pagar("clave-para-marcas-1", TARJETA_APROBADA);
            reloj.avanzar(Duration.ofMinutes(1));

            ResponseEntity<String> conSesion = get("/vitrina");
            ResponseEntity<String> sinSesion = cliente().get().uri("/vitrina").retrieve().toEntity(String.class);

            assertThat(JsonPath.<List<Boolean>>read(conSesion.getBody(),
                    "$.content[?(@.id=='" + ESCUDO + "')].enListaDeseos")).containsExactly(true);
            assertThat(JsonPath.<List<Boolean>>read(conSesion.getBody(),
                    "$.content[?(@.id=='" + ESPADA + "')].esPropio")).containsExactly(true);
            assertThat(JsonPath.<List<Boolean>>read(sinSesion.getBody(), "$.content[*].esPropio")).containsOnly(false);
            assertThat(JsonPath.<List<Boolean>>read(sinSesion.getBody(), "$.content[*].enListaDeseos"))
                    .containsOnly(false);
            assertThat(JsonPath.<List<String>>read(sinSesion.getBody(), "$.monedasDisponibles")).containsExactly("COP");
        }
    }
}
