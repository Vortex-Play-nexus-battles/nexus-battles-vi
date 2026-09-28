package com.nexusbattles.ms_ecommerce.integracion;

import com.nexusbattles.ms_ecommerce.catalogo.ReservasDeTiraje;
import com.nexusbattles.ms_ecommerce.integracion.correo.ClienteDeCorreo;
import com.nexusbattles.ms_ecommerce.integracion.finanzas.ClienteDeFinanzas;
import com.nexusbattles.ms_ecommerce.integracion.identidad.ClienteDeIdentidad;
import com.nexusbattles.ms_ecommerce.integracion.inventario.ClienteDeInventario;
import com.nexusbattles.ms_ecommerce.integracion.parametros.ClienteDeParametros;
import com.nexusbattles.ms_ecommerce.traza.Traza;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Los clientes de la compra contra un servidor HTTP simulado, con el mismo
 * constructor que se despliega ({@link ConfiguracionDeIntegraciones#constructor}).
 *
 * <p>Lo que se fija en cada uno es la frontera entre «el otro servicio decidio»
 * (un resultado) y «el otro servicio no respondio» (una excepcion que deja la
 * orden en su estado para reintentar): confundirlas seria reembolsar una
 * compra porque un servicio tardo, o reintentar para siempre una que se agoto.
 */
@DisplayName("Clientes HTTP de la compra: decisiones frente a averias")
class ClientesDeLaCompraTest {

    private static final String BASE = "http://otro.test";
    private static final PropiedadesDeLaTienda.Http TIEMPOS =
            new PropiedadesDeLaTienda.Http(Duration.ofSeconds(2), Duration.ofSeconds(5));
    private static final String PRODUCTO = "5b0a3c1e-8d7f-4e2a-9c6b-1f0e2d3c4b5a";
    private static final String JUGADOR = "7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55";

    private MockRestServiceServer servidor;

    private RestClient cliente(String base) {
        RestClient.Builder constructor = ConfiguracionDeIntegraciones.constructor(base, TIEMPOS);
        servidor = MockRestServiceServer.bindTo(constructor).build();
        return constructor.build();
    }

    @AfterEach
    void todoLoEsperado() {
        if (servidor != null) {
            servidor.verify();
        }
        Traza.cerrar();
    }

    @Nested
    @DisplayName("reserva de tiraje (productos)")
    class Reservas {

        private final String ruta = BASE + "/api/v1/productos/" + PRODUCTO + "/adquisiciones";

        @Test
        @DisplayName("200 ACEPTADA con la clave en la cabecera y la traza de la compra")
        void aceptada() {
            ReservasDeTiraje reservas = new ReservasDeTiraje(cliente(BASE));
            Traza.abrir("0af7651916cd43dd8448eb211c80319c");
            servidor.expect(requestTo(ruta))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("Idempotency-Key", "orden-1-l1-u1"))
                    .andExpect(header("traceparent", org.hamcrest.Matchers.startsWith(
                            "00-0af7651916cd43dd8448eb211c80319c-")))
                    .andRespond(withSuccess("{\"estado\":\"ACEPTADA\",\"mensaje\":\"ok\"}", MediaType.APPLICATION_JSON));

            assertThat(reservas.reservarUnaUnidad(PRODUCTO, "orden-1-l1-u1")).isEqualTo(ReservasDeTiraje.Resultado.ACEPTADA);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"AGOTADO", "SUSPENDIDO"})
        @DisplayName("409 con el resultado del catalogo: su decision, no una averia")
        void rechazos(String estado) {
            ReservasDeTiraje reservas = new ReservasDeTiraje(cliente(BASE));
            servidor.expect(requestTo(ruta)).andRespond(withStatus(HttpStatus.CONFLICT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"estado\":\"" + estado + "\",\"mensaje\":\"no\"}"));

            assertThat(reservas.reservarUnaUnidad(PRODUCTO, "k").name()).isEqualTo(estado);
        }

        @Test
        @DisplayName("409 problem details (la clave ya se uso con otro producto) y 400: rechazada")
        void peticionRechazada() {
            ReservasDeTiraje reservas = new ReservasDeTiraje(cliente(BASE));
            servidor.expect(requestTo(ruta)).andRespond(withStatus(HttpStatus.CONFLICT)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body("{\"type\":\"urn:nexus:problema:clave-de-idempotencia-reutilizada\",\"status\":409}"));
            servidor.expect(requestTo(ruta)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

            assertThat(reservas.reservarUnaUnidad(PRODUCTO, "k")).isEqualTo(ReservasDeTiraje.Resultado.RECHAZADA);
            assertThat(reservas.reservarUnaUnidad(PRODUCTO, "k")).isEqualTo(ReservasDeTiraje.Resultado.RECHAZADA);
        }

        @Test
        @DisplayName("404: el producto ya no existe")
        void inexistente() {
            ReservasDeTiraje reservas = new ReservasDeTiraje(cliente(BASE));
            servidor.expect(requestTo(ruta)).andRespond(withResourceNotFound());

            assertThat(reservas.reservarUnaUnidad(PRODUCTO, "k")).isEqualTo(ReservasDeTiraje.Resultado.INEXISTENTE);
        }

        @Test
        @DisplayName("200 sin cuerpo legible sigue siendo la reserva hecha")
        void aceptadaSinCuerpo() {
            ReservasDeTiraje reservas = new ReservasDeTiraje(cliente(BASE));
            servidor.expect(requestTo(ruta)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

            assertThat(reservas.reservarUnaUnidad(PRODUCTO, "k")).isEqualTo(ReservasDeTiraje.Resultado.ACEPTADA);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(ints = {401, 403, 429, 500, 503})
        @DisplayName("credencial rechazada, limite o 5xx: averia, se reintenta mas tarde")
        void averias(int estado) {
            ReservasDeTiraje reservas = new ReservasDeTiraje(cliente(BASE));
            servidor.expect(requestTo(ruta)).andRespond(withStatus(HttpStatus.valueOf(estado)));

            assertThatThrownBy(() -> reservas.reservarUnaUnidad(PRODUCTO, "k"))
                    .isInstanceOfSatisfying(ServicioNoDisponibleException.class,
                            e -> assertThat(e.servicio()).isEqualTo("productos"));
        }

        @Test
        @DisplayName("un tiempo agotado tambien es averia")
        void tiempoAgotado() {
            ReservasDeTiraje reservas = new ReservasDeTiraje(cliente(BASE));
            servidor.expect(requestTo(ruta)).andRespond(withException(new SocketTimeoutException("lento")));

            assertThatThrownBy(() -> reservas.reservarUnaUnidad(PRODUCTO, "k"))
                    .isInstanceOf(ServicioNoDisponibleException.class);
        }
    }

    @Nested
    @DisplayName("inventario: entrega y lo que tiene un jugador")
    class Inventario {

        @Test
        @DisplayName("la entrega manda uid, origen COMPRA, referencia, productos y la clave; 201 y 200 son entregada")
        void entrega() {
            ClienteDeInventario inventario = new ClienteDeInventario(cliente(BASE));
            for (HttpStatus estado : List.of(HttpStatus.CREATED, HttpStatus.OK)) {
                servidor.expect(requestTo(BASE + "/api/v1/inventario/entregas"))
                        .andExpect(method(HttpMethod.POST))
                        .andExpect(header("Idempotency-Key", "orden-abc"))
                        .andExpect(jsonPath("$.uid").value(JUGADOR))
                        .andExpect(jsonPath("$.origen").value("COMPRA"))
                        .andExpect(jsonPath("$.referencia").value("abc"))
                        .andExpect(jsonPath("$.productos[0].productoId").value(PRODUCTO))
                        .andExpect(jsonPath("$.productos[0].cantidad").value(2))
                        .andRespond(withStatus(estado).contentType(MediaType.APPLICATION_JSON).body("{}"));
            }

            for (int i = 0; i < 2; i++) {
                assertThat(inventario.entregar(JUGADOR, "abc", List.of(new ClienteDeInventario.Unidades(PRODUCTO, 2)),
                        "orden-abc")).isEqualTo(ClienteDeInventario.ResultadoDeEntrega.ENTREGADA);
            }
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(ints = {400, 409, 422})
        @DisplayName("400, 409 y 422: el inventario se nego, rechazada")
        void entregaRechazada(int estado) {
            ClienteDeInventario inventario = new ClienteDeInventario(cliente(BASE));
            servidor.expect(requestTo(BASE + "/api/v1/inventario/entregas"))
                    .andRespond(withStatus(HttpStatus.valueOf(estado)));

            assertThat(inventario.entregar(JUGADOR, "abc", List.of(new ClienteDeInventario.Unidades(PRODUCTO, 1)), "k"))
                    .isEqualTo(ClienteDeInventario.ResultadoDeEntrega.RECHAZADA);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(ints = {401, 403, 500, 503})
        @DisplayName("401, 403 y 5xx: averia")
        void entregaConAveria(int estado) {
            ClienteDeInventario inventario = new ClienteDeInventario(cliente(BASE));
            servidor.expect(requestTo(BASE + "/api/v1/inventario/entregas"))
                    .andRespond(withStatus(HttpStatus.valueOf(estado)));

            assertThatThrownBy(() -> inventario.entregar(JUGADOR, "abc",
                    List.of(new ClienteDeInventario.Unidades(PRODUCTO, 1)), "k"))
                    .isInstanceOf(ServicioNoDisponibleException.class);
        }

        @Test
        @DisplayName("lo que tiene un jugador: todas las paginas, con el uid en X-User-Name")
        void productosDelJugador() {
            ClienteDeInventario inventario = new ClienteDeInventario(cliente(BASE));
            servidor.expect(requestTo(BASE + "/api/v1/inventario/elementos?pagina=0"))
                    .andExpect(header("X-User-Name", JUGADOR))
                    .andRespond(withSuccess("""
                            {"elementos":[{"productoId":"a"},{"productoId":"b"},{"productoId":"a"}],
                             "numero":0,"tamanio":16,"totalElementos":4,"totalPaginas":2,"ultima":false}""",
                            MediaType.APPLICATION_JSON));
            servidor.expect(requestTo(BASE + "/api/v1/inventario/elementos?pagina=1"))
                    .andRespond(withSuccess("""
                            {"elementos":[{"productoId":"c"},{}],"numero":1,"tamanio":16,
                             "totalElementos":4,"totalPaginas":2,"ultima":true}""", MediaType.APPLICATION_JSON));

            assertThat(inventario.productosDe(JUGADOR)).containsExactlyInAnyOrder("a", "b", "c");
        }

        @Test
        @DisplayName("inventario caido al consultar: averia")
        void consultaCaida() {
            ClienteDeInventario inventario = new ClienteDeInventario(cliente(BASE));
            servidor.expect(requestTo(BASE + "/api/v1/inventario/elementos?pagina=0"))
                    .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

            assertThatThrownBy(() -> inventario.productosDe(JUGADOR)).isInstanceOf(ServicioNoDisponibleException.class);
        }
    }

    @Nested
    @DisplayName("ms-finanzas: el asiento del cobro")
    class Finanzas {

        @Test
        @DisplayName("manda el refId (la orden), el uid, el monto, la moneda, el concepto y el resultado; 201 o 409 es registrado")
        void asiento() {
            ClienteDeFinanzas finanzas = new ClienteDeFinanzas(cliente(BASE + "/api/v1"));
            for (HttpStatus estado : List.of(HttpStatus.CREATED, HttpStatus.CONFLICT)) {
                servidor.expect(requestTo(BASE + "/api/v1/transacciones"))
                        .andExpect(method(HttpMethod.POST))
                        .andExpect(jsonPath("$.refId").value("orden-1"))
                        .andExpect(jsonPath("$.uidUsuario").value(JUGADOR))
                        .andExpect(jsonPath("$.monto").value(45000))
                        .andExpect(jsonPath("$.moneda").value("COP"))
                        .andExpect(jsonPath("$.concepto").value("Compra en la tienda: Espada"))
                        .andExpect(jsonPath("$.resultado").value("APROBADO"))
                        .andExpect(jsonPath("$.pasarelaRefExterna").value("SIM-1"))
                        .andRespond(withStatus(estado));
            }

            for (int i = 0; i < 2; i++) {
                finanzas.registrar("orden-1", JUGADOR, new BigDecimal("45000"), "COP", "Compra en la tienda: Espada",
                        ClienteDeFinanzas.Resultado.APROBADO, "SIM-1");
            }
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(ints = {400, 401, 403, 500})
        @DisplayName("cualquier otra respuesta es averia: la orden reintenta")
        void averia(int estado) {
            ClienteDeFinanzas finanzas = new ClienteDeFinanzas(cliente(BASE + "/api/v1"));
            servidor.expect(requestTo(BASE + "/api/v1/transacciones")).andRespond(withStatus(HttpStatus.valueOf(estado)));

            assertThatThrownBy(() -> finanzas.registrar("r", JUGADOR, BigDecimal.ONE, "COP", "c",
                    ClienteDeFinanzas.Resultado.RECHAZADO, null))
                    .isInstanceOfSatisfying(ServicioNoDisponibleException.class,
                            e -> assertThat(e.servicio()).isEqualTo("ms-finanzas"));
        }
    }

    @Nested
    @DisplayName("ms-identidad: el contacto del comprador")
    class Identidad {

        @Test
        @DisplayName("200: correo y apodo; el toString no ensena el correo")
        void contacto() {
            ClienteDeIdentidad identidad = new ClienteDeIdentidad(cliente(BASE));
            servidor.expect(requestTo(BASE + "/api/v1/internal/usuarios/" + JUGADOR + "/contacto"))
                    .andRespond(withSuccess("{\"uid\":\"" + JUGADOR + "\",\"email\":\"ana@nexus.test\","
                            + "\"apodo\":\"ana\",\"estado\":\"ACTIVO\"}", MediaType.APPLICATION_JSON));

            ClienteDeIdentidad.Contacto contacto = identidad.contacto(JUGADOR).orElseThrow();

            assertThat(contacto.email()).isEqualTo("ana@nexus.test");
            assertThat(contacto.apodo()).isEqualTo("ana");
            assertThat(contacto.toString()).doesNotContain("ana@nexus.test");
        }

        @Test
        @DisplayName("404 o sin correo: no hay a quien escribir")
        void sinContacto() {
            ClienteDeIdentidad identidad = new ClienteDeIdentidad(cliente(BASE));
            servidor.expect(requestTo(BASE + "/api/v1/internal/usuarios/x/contacto")).andRespond(withResourceNotFound());
            servidor.expect(requestTo(BASE + "/api/v1/internal/usuarios/y/contacto"))
                    .andRespond(withSuccess("{\"uid\":\"y\"}", MediaType.APPLICATION_JSON));

            assertThat(identidad.contacto("x")).isEmpty();
            assertThat(identidad.contacto("y")).isEmpty();
        }

        @Test
        @DisplayName("un 5xx es averia")
        void averia() {
            ClienteDeIdentidad identidad = new ClienteDeIdentidad(cliente(BASE));
            servidor.expect(requestTo(BASE + "/api/v1/internal/usuarios/x/contacto"))
                    .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

            assertThatThrownBy(() -> identidad.contacto("x")).isInstanceOf(ServicioNoDisponibleException.class);
        }
    }

    @Nested
    @DisplayName("correo: la confirmacion de compra")
    class Correo {

        private ClienteDeCorreo.ConfirmacionDeCompra confirmacion() {
            return new ClienteDeCorreo.ConfirmacionDeCompra("ana@nexus.test", "ana", new BigDecimal("12000"), "COP",
                    "Compra en la tienda: Espada x2", "2026-09-25T07:00:00-05:00",
                    List.of(new ClienteDeCorreo.Linea("Espada", 2, new BigDecimal("6000"), new BigDecimal("12000"))),
                    "orden-1");
        }

        @Test
        @DisplayName("202: aceptado; manda lineas, total, orden y la clave compra-{id}")
        void aceptado() {
            ClienteDeCorreo correo = new ClienteDeCorreo(cliente(BASE));
            servidor.expect(requestTo(BASE + "/api/v1/correos/confirmacion-compra"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("Idempotency-Key", "compra-orden-1"))
                    .andExpect(jsonPath("$.email").value("ana@nexus.test"))
                    .andExpect(jsonPath("$.monto").value(12000))
                    .andExpect(jsonPath("$.lineas[0].nombre").value("Espada"))
                    .andExpect(jsonPath("$.lineas[0].cantidad").value(2))
                    .andExpect(jsonPath("$.lineas[0].subtotal").value(12000))
                    .andExpect(jsonPath("$.orden").value("orden-1"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andRespond(withStatus(HttpStatus.ACCEPTED));

            assertThat(correo.enviarConfirmacionDeCompra(confirmacion(), "compra-orden-1"))
                    .isEqualTo(ClienteDeCorreo.Resultado.ACEPTADO);
            assertThat(confirmacion().toString()).doesNotContain("ana@nexus.test");
        }

        @Test
        @DisplayName("400: rechazado (los datos no sirven; no se repite)")
        void rechazado() {
            ClienteDeCorreo correo = new ClienteDeCorreo(cliente(BASE));
            servidor.expect(requestTo(BASE + "/api/v1/correos/confirmacion-compra"))
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST));

            assertThat(correo.enviarConfirmacionDeCompra(confirmacion(), "k")).isEqualTo(ClienteDeCorreo.Resultado.RECHAZADO);
        }

        @Test
        @DisplayName("503: averia")
        void averia() {
            ClienteDeCorreo correo = new ClienteDeCorreo(cliente(BASE));
            servidor.expect(requestTo(BASE + "/api/v1/correos/confirmacion-compra"))
                    .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

            assertThatThrownBy(() -> correo.enviarConfirmacionDeCompra(confirmacion(), "k"))
                    .isInstanceOf(ServicioNoDisponibleException.class);
        }
    }

    @Nested
    @DisplayName("admin-parametros: el valor de un parametro")
    class Parametros {

        private PropiedadesDeLaTienda propiedades(String parametros) {
            return new PropiedadesDeLaTienda(TIEMPOS,
                    new PropiedadesDeLaTienda.Servicios("i", "f", "c", "id", parametros),
                    new PropiedadesDeLaTienda.Credencial("", "", ""), new PropiedadesDeLaTienda.Correo(true),
                    new PropiedadesDeLaTienda.Ordenes(Duration.ofMinutes(2), Duration.ofMinutes(30),
                            Duration.ofSeconds(15), Duration.ofMinutes(15), 20),
                    ZoneId.of("America/Bogota"));
        }

        @Test
        @DisplayName("el valor vigente, o vacio si no existe (404) o no tiene valor")
        void valor() {
            ClienteDeParametros parametros = new ClienteDeParametros(cliente(BASE + "/api/v1"),
                    propiedades(BASE + "/api/v1"));
            servidor.expect(requestTo(BASE + "/api/v1/parametros/tienda.tasa-cop-usd/valor"))
                    .andRespond(withSuccess("{\"clave\":\"tienda.tasa-cop-usd\",\"valor\":\"4000\",\"tipo\":\"DECIMAL\","
                            + "\"version\":2}", MediaType.APPLICATION_JSON));
            servidor.expect(requestTo(BASE + "/api/v1/parametros/tienda.tasa-cop-eur/valor"))
                    .andRespond(withSuccess("{\"clave\":\"tienda.tasa-cop-eur\",\"valor\":null,\"version\":1}",
                            MediaType.APPLICATION_JSON));
            servidor.expect(requestTo(BASE + "/api/v1/parametros/otra/valor")).andRespond(withResourceNotFound());

            assertThat(parametros.valor("tienda.tasa-cop-usd")).contains("4000");
            assertThat(parametros.valor("tienda.tasa-cop-eur")).isEmpty();
            assertThat(parametros.valor("otra")).isEmpty();
        }

        @Test
        @DisplayName("un 5xx es averia; PARAMETROS_URL vacia es «no se consulta»")
        void averiaYSinConfigurar() {
            ClienteDeParametros parametros = new ClienteDeParametros(cliente(BASE + "/api/v1"),
                    propiedades(BASE + "/api/v1"));
            servidor.expect(requestTo(BASE + "/api/v1/parametros/x/valor"))
                    .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

            assertThatThrownBy(() -> parametros.valor("x")).isInstanceOf(ServicioNoDisponibleException.class);
            assertThat(new ClienteDeParametros(RestClient.create(), propiedades("")).valor("x")).isEmpty();
        }
    }
}
