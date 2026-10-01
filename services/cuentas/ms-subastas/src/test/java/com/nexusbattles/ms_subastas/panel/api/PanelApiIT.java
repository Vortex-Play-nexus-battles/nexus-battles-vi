package com.nexusbattles.ms_subastas.panel.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.ms_subastas.notificaciones.NotificacionPendiente;
import com.nexusbattles.ms_subastas.notificaciones.NotificacionPendienteRepository;
import com.nexusbattles.ms_subastas.notificaciones.RecordatorioDeCierreJob;
import com.nexusbattles.ms_subastas.notificaciones.TipoNotificacion;
import com.nexusbattles.ms_subastas.panel.model.EstadoPendiente;
import com.nexusbattles.ms_subastas.panel.model.PendienteDeRecoger;
import com.nexusbattles.ms_subastas.panel.repository.PendienteDeRecogerRepository;
import com.nexusbattles.ms_subastas.panel.service.VencimientoDePendientesJob;
import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClient;
import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClientFake;
import com.nexusbattles.ms_subastas.pujas.service.PujaApplicationService;
import com.nexusbattles.ms_subastas.soporte.FinanzasFalsa;
import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClient;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClientFake;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El panel personal y las acciones sobre una subasta de B8 de extremo a
 * extremo ({@code ms-subastas-panel.yaml} 1.0.0 y {@code ms-subastas-listado.yaml}
 * 1.1.0): HTTP real con JWT de la forma de ms-identidad, PostgreSQL real y un
 * ms-finanzas falso por HTTP. Cada regla de 7.7 que toca la cancelacion, el
 * seguimiento, los pendientes y el historial se comprueba contra la API
 * publicada, no contra el servicio.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.finanzas.modo=prueba",
                "app.pujas.emision-automatica-intervalo-ms=3600000",
                "app.subastas.cierre-intervalo-ms=3600000",
                "app.notificaciones.drenaje-intervalo-ms=3600000",
                "app.subastas.recordatorio-intervalo-ms=3600000",
                "app.subastas.pendientes-intervalo-ms=3600000",
                "app.correo.drenaje-intervalo-ms=3600000",
                "app.pujas.intervalo-minimo-segundos=0"
        })
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Panel personal y acciones sobre una subasta (B8, 7.7.9 y 7.7.10)")
class PanelApiIT {

    private static final FinanzasFalsa FINANZAS = new FinanzasFalsa();

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        EmisorDeTokensDePrueba.registrarJwks(registro);
        registro.add("app.finanzas.base-url", FINANZAS::base);
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @TestConfiguration
    static class CreditosDePrueba {
        @Bean
        CreditoClientFake creditoClientFake() {
            return new CreditoClientFake();
        }
    }

    @AfterAll
    static void apagarFinanzas() {
        FINANZAS.close();
    }

    @LocalServerPort
    private int puerto;

    @Autowired
    private SubastaRepository subastas;

    @Autowired
    private NotificacionPendienteRepository avisos;

    @Autowired
    private PendienteDeRecogerRepository pendientes;

    @Autowired
    private PujaApplicationService pujas;

    @Autowired
    private InventarioClient inventario;

    @Autowired
    private CreditoClient creditos;

    @Autowired
    private VencimientoDePendientesJob vencimientos;

    @Autowired
    private RecordatorioDeCierreJob recordatorios;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();

    @BeforeEach
    void limpiarFinanzas() {
        FINANZAS.olvidar();
    }

    // --- utilidades -------------------------------------------------------------

    private UUID jugadorConSaldo() {
        UUID jugador = UUID.randomUUID();
        ((CreditoClientFake) creditos).acreditar(jugador, new BigDecimal("10000"));
        return jugador;
    }

    private String token(UUID jugador) {
        return emisor.tokenDeJugador("jugador_" + jugador.toString().substring(0, 6), jugador);
    }

    /** Una subasta publicada «de verdad»: con su elemento bloqueado en inventario y la comision de 48 h pagada. */
    private Subasta publicada(UUID vendedor, Duration faltan) {
        Subasta subasta = new Subasta();
        subasta.setId(UUID.randomUUID());
        subasta.setProductoId(UUID.randomUUID());
        subasta.setElementoInventarioId("elem-" + subasta.getId());
        subasta.setVendedorId(vendedor);
        subasta.setPrecioInicial(new BigDecimal("100"));
        subasta.setOfertaVigente(new BigDecimal("100"));
        subasta.setIncrementoMinimo(new BigDecimal("10"));
        subasta.setPrecioCompraInmediata(new BigDecimal("900"));
        subasta.setEstado(EstadoSubasta.ACTIVA);
        subasta.setFechaPublicacion(Instant.now().minusSeconds(60));
        subasta.setFechaFin(Instant.now().plus(faltan));
        subasta.setComisionCobrada(new BigDecimal("3"));
        subasta.setNombreProducto("Espada del Alba");
        subasta.setApodoVendedor("forjador");
        inventario.reservar(subasta.getElementoInventarioId(), vendedor, subasta.getId(), "publicar-" + subasta.getId());
        return subastas.saveAndFlush(subasta);
    }

    private Subasta publicada(UUID vendedor) {
        return publicada(vendedor, Duration.ofHours(30));
    }

    private HttpResponse<String> pedir(String metodo, String ruta, String cuerpo, String token) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/v1" + ruta))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "k-" + UUID.randomUUID());
        if (token != null) {
            peticion.header("Authorization", "Bearer " + token);
        }
        peticion.method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(cuerpo));
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode json(HttpResponse<String> respuesta) throws Exception {
        return mapper.readTree(respuesta.body());
    }

    private String motivo(HttpResponse<String> respuesta) throws Exception {
        assertTrue(respuesta.headers().firstValue("Content-Type").orElse("").contains("application/problem+json"),
                "regla 4: problem details. " + respuesta.body());
        return json(respuesta).path("motivo").asText();
    }

    /** Los montos viajan como cadena (contrato); se comparan como decimales, sin depender de la escala. */
    private static void assertMonto(String esperado, JsonNode nodo) {
        assertTrue(nodo.isTextual(), "los montos viajan como cadena: " + nodo);
        assertEquals(0, new BigDecimal(esperado).compareTo(new BigDecimal(nodo.asText())),
                "esperaba " + esperado + " y llego " + nodo.asText());
    }

    private void pujar(Subasta subasta, UUID jugador, String monto) throws Exception {
        HttpResponse<String> respuesta = pedir("POST", "/subastas/" + subasta.getId() + "/pujas",
                "{\"monto\":\"" + monto + "\"}", token(jugador));
        assertEquals(201, respuesta.statusCode(), respuesta.body());
    }

    private List<NotificacionPendiente> avisosDe(UUID subastaId, TipoNotificacion tipo) {
        return avisos.findAll().stream()
                .filter(a -> a.getSubastaId().equals(subastaId) && a.getTipo() == tipo)
                .toList();
    }

    // --- cancelar (7.7.10) ------------------------------------------------------

    @Test
    @DisplayName("cancelar sin pujas: cobra el 50 % de la comision en finanzas, libera el producto y avisa")
    void cancelarSinPujas() throws Exception {
        UUID vendedor = jugadorConSaldo();
        Subasta subasta = publicada(vendedor);

        HttpResponse<String> respuesta = pedir("POST", "/subastas/" + subasta.getId() + "/cancelacion", null,
                token(vendedor));

        assertEquals(200, respuesta.statusCode(), respuesta.body());
        JsonNode cancelacion = json(respuesta);
        assertEquals("CANCELADA", cancelacion.path("estado").asText());
        assertMonto("1.50", cancelacion.path("penalizacionCobrada"));
        assertFalse(cancelacion.path("canceladaEn").asText().isBlank());

        List<FinanzasFalsa.Operacion> debitos = FINANZAS.recibidas("debitar");
        assertEquals(1, debitos.size());
        assertEquals("sub-cancelacion-" + subasta.getId(), debitos.getFirst().refId());
        assertEquals(0, new BigDecimal("1.50").compareTo(debitos.getFirst().cuerpo().path("monto").decimalValue()));
        assertEquals(vendedor.toString(), debitos.getFirst().cuerpo().path("uid").asText());
        assertFalse(((InventarioClientFake) inventario).getReservas().containsKey(subasta.getId().toString()),
                "el producto vuelve a estar disponible para el vendedor");
        assertEquals(EstadoSubasta.CANCELADA, subastas.findById(subasta.getId()).orElseThrow().getEstado());
        assertEquals(1, avisosDe(subasta.getId(), TipoNotificacion.SUBASTA_CANCELADA).size());

        // Repetirla (una respuesta perdida) devuelve lo mismo sin cobrar otra vez.
        HttpResponse<String> otraVez = pedir("POST", "/subastas/" + subasta.getId() + "/cancelacion", null,
                token(vendedor));
        assertEquals(200, otraVez.statusCode());
        assertEquals(1, FINANZAS.recibidas("debitar").size());
        assertEquals(1, avisosDe(subasta.getId(), TipoNotificacion.SUBASTA_CANCELADA).size());
    }

    @Test
    @DisplayName("cancelar una ajena es 403 NO_ES_EL_VENDEDOR")
    void cancelarAjena() throws Exception {
        Subasta subasta = publicada(jugadorConSaldo());

        HttpResponse<String> respuesta = pedir("POST", "/subastas/" + subasta.getId() + "/cancelacion", null,
                token(jugadorConSaldo()));

        assertEquals(403, respuesta.statusCode(), respuesta.body());
        assertEquals("NO_ES_EL_VENDEDOR", motivo(respuesta));
        assertTrue(FINANZAS.recibidas().isEmpty());
    }

    @Test
    @DisplayName("con una puja registrada ya no se cancela: 409 CANCELACION_CON_PUJAS")
    void cancelarConPujas() throws Exception {
        UUID vendedor = jugadorConSaldo();
        Subasta subasta = publicada(vendedor);
        pujar(subasta, jugadorConSaldo(), "100");

        HttpResponse<String> respuesta = pedir("POST", "/subastas/" + subasta.getId() + "/cancelacion", null,
                token(vendedor));

        assertEquals(409, respuesta.statusCode(), respuesta.body());
        assertEquals("CANCELACION_CON_PUJAS", motivo(respuesta));
        assertEquals(EstadoSubasta.ACTIVA, subastas.findById(subasta.getId()).orElseThrow().getEstado());
    }

    @Test
    @DisplayName("en las ultimas 6 horas no se cancela: 422 CANCELACION_FUERA_DE_PLAZO")
    void cancelarFueraDePlazo() throws Exception {
        UUID vendedor = jugadorConSaldo();
        Subasta subasta = publicada(vendedor, Duration.ofHours(5));

        HttpResponse<String> respuesta = pedir("POST", "/subastas/" + subasta.getId() + "/cancelacion", null,
                token(vendedor));

        assertEquals(422, respuesta.statusCode(), respuesta.body());
        assertEquals("CANCELACION_FUERA_DE_PLAZO", motivo(respuesta));
        assertTrue(FINANZAS.recibidas().isEmpty());
    }

    @Test
    @DisplayName("sin creditos para la penalizacion: 422 SALDO_INSUFICIENTE y la subasta sigue activa y bloqueada")
    void cancelarSinSaldo() throws Exception {
        UUID vendedor = jugadorConSaldo();
        Subasta subasta = publicada(vendedor);
        FINANZAS.sinSaldo.add(vendedor);

        HttpResponse<String> respuesta = pedir("POST", "/subastas/" + subasta.getId() + "/cancelacion", null,
                token(vendedor));

        assertEquals(422, respuesta.statusCode(), respuesta.body());
        assertEquals("SALDO_INSUFICIENTE", motivo(respuesta));
        assertEquals(EstadoSubasta.ACTIVA, subastas.findById(subasta.getId()).orElseThrow().getEstado());
        assertTrue(((InventarioClientFake) inventario).getReservas().containsKey(subasta.getId().toString()));
    }

    @Test
    @DisplayName("con finanzas caido: 503 sin detalles internos y nada cambia")
    void cancelarConFinanzasCaido() throws Exception {
        UUID vendedor = jugadorConSaldo();
        Subasta subasta = publicada(vendedor);
        FINANZAS.caido.set(true);

        HttpResponse<String> respuesta = pedir("POST", "/subastas/" + subasta.getId() + "/cancelacion", null,
                token(vendedor));

        assertEquals(503, respuesta.statusCode(), respuesta.body());
        assertFalse(respuesta.body().contains("localhost"), "sin detalles internos: " + respuesta.body());
        assertEquals(EstadoSubasta.ACTIVA, subastas.findById(subasta.getId()).orElseThrow().getEstado());
    }

    @Test
    @DisplayName("sin sesion es 401; una subasta que no existe, 404")
    void cancelarSinSesionOInexistente() throws Exception {
        Subasta subasta = publicada(jugadorConSaldo());

        assertEquals(401, pedir("POST", "/subastas/" + subasta.getId() + "/cancelacion", null, null).statusCode());
        HttpResponse<String> inexistente = pedir("POST", "/subastas/" + UUID.randomUUID() + "/cancelacion", null,
                token(jugadorConSaldo()));
        assertEquals(404, inexistente.statusCode(), inexistente.body());
    }

    // --- seguimiento (7.7.9) ------------------------------------------------------

    @Test
    @DisplayName("seguir, verla en la lista y en mi participacion, recibir sus cambios y dejar de seguirla")
    void listaDeSeguimiento() throws Exception {
        UUID vendedor = jugadorConSaldo();
        UUID yo = jugadorConSaldo();
        Subasta subasta = publicada(vendedor);
        String ruta = "/subastas/" + subasta.getId() + "/seguimiento";

        assertEquals(204, pedir("PUT", ruta, null, token(yo)).statusCode());
        assertEquals(204, pedir("PUT", ruta, null, token(yo)).statusCode(), "seguir dos veces no es un error");

        JsonNode lista = json(pedir("GET", "/mis-subastas/seguimiento", null, token(yo)));
        assertEquals(1, lista.size());
        assertEquals(subasta.getId().toString(), lista.get(0).path("subastaId").asText());
        assertEquals("ACTIVA", lista.get(0).path("estado").asText());
        assertTrue(json(pedir("GET", "/subastas/" + subasta.getId() + "/mi-participacion", null, token(yo)))
                .path("siguiendo").asBoolean());

        // 7.7.9: «Notificaciones cuando hay cambios en estas subastas».
        UUID postor = jugadorConSaldo();
        pujar(subasta, postor, "100");
        assertEquals(List.of(yo), avisosDe(subasta.getId(), TipoNotificacion.CAMBIO_EN_SUBASTA_SEGUIDA).stream()
                .map(NotificacionPendiente::getDestinatarioId).toList());
        assertEquals(List.of(vendedor), avisosDe(subasta.getId(), TipoNotificacion.NUEVA_PUJA).stream()
                .map(NotificacionPendiente::getDestinatarioId).toList());
        assertEquals(List.of(postor), avisosDe(subasta.getId(), TipoNotificacion.PUJA_REGISTRADA).stream()
                .map(NotificacionPendiente::getDestinatarioId).toList());

        assertEquals(204, pedir("DELETE", ruta, null, token(yo)).statusCode());
        assertEquals(0, json(pedir("GET", "/mis-subastas/seguimiento", null, token(yo))).size());
        assertEquals(204, pedir("DELETE", ruta, null, token(yo)).statusCode(), "dejar de seguir es idempotente");
    }

    @Test
    @DisplayName("seguir una terminada es 409; una que no existe, 404")
    void seguirTerminadaOInexistente() throws Exception {
        UUID vendedor = jugadorConSaldo();
        Subasta subasta = publicada(vendedor);
        assertEquals(200, pedir("POST", "/subastas/" + subasta.getId() + "/cancelacion", null, token(vendedor))
                .statusCode());

        HttpResponse<String> terminada = pedir("PUT", "/subastas/" + subasta.getId() + "/seguimiento", null,
                token(jugadorConSaldo()));
        assertEquals(409, terminada.statusCode(), terminada.body());
        assertEquals("SUBASTA_NO_ACTIVA", motivo(terminada));

        assertEquals(404, pedir("PUT", "/subastas/" + UUID.randomUUID() + "/seguimiento", null,
                token(jugadorConSaldo())).statusCode());
    }

    // --- mis subastas y reglas ------------------------------------------------------

    @Test
    @DisplayName("mis subastas dicen si se pueden cancelar y cuanto costaria; filtran por estado")
    void misPublicaciones() throws Exception {
        UUID vendedor = jugadorConSaldo();
        Subasta cancelable = publicada(vendedor);
        Subasta porCerrar = publicada(vendedor, Duration.ofHours(2));

        JsonNode mias = json(pedir("GET", "/mis-subastas/publicadas", null, token(vendedor)));

        assertEquals(2, mias.size());
        for (JsonNode mia : mias) {
            if (mia.path("subastaId").asText().equals(cancelable.getId().toString())) {
                assertTrue(mia.path("cancelable").asBoolean());
                assertMonto("1.50", mia.path("penalizacionSiCancela"));
            } else {
                assertEquals(porCerrar.getId().toString(), mia.path("subastaId").asText());
                assertFalse(mia.path("cancelable").asBoolean());
                assertTrue(mia.path("penalizacionSiCancela").isNull());
            }
        }
        assertEquals(0, json(pedir("GET", "/mis-subastas/publicadas?estado=CANCELADA", null, token(vendedor))).size());
        assertEquals(401, pedir("GET", "/mis-subastas/publicadas", null, null).statusCode());
    }

    @Test
    @DisplayName("las reglas vigentes son publicas y no inventan el incremento que falta")
    void reglasVigentes() throws Exception {
        HttpResponse<String> respuesta = pedir("GET", "/subastas/reglas", null, null);

        assertEquals(200, respuesta.statusCode(), respuesta.body());
        JsonNode reglas = json(respuesta);
        assertFalse(reglas.path("incrementoMinimoConfigurado").asBoolean(),
                "sin admin-parametros no hay incremento (decision del PO pendiente)");
        assertTrue(reglas.path("incrementoMinimo").isNull());
        assertEquals(24, reglas.path("duraciones").get(0).path("horas").asInt());
        assertMonto("1", reglas.path("duraciones").get(0).path("comision"));
        assertEquals(48, reglas.path("duraciones").get(1).path("horas").asInt());
        assertMonto("3", reglas.path("duraciones").get(1).path("comision"));
        assertEquals(10, reglas.path("maxSubastasActivasPorJugador").asInt());
        assertEquals(50, reglas.path("penalizacionCancelacionPorcentaje").asInt());
        assertEquals(6, reglas.path("cancelacionProhibidaUltimasHoras").asInt());
        assertEquals(60, reglas.path("recordatorioMinutosAntesDelCierre").asInt());
        assertEquals(7, reglas.path("diasParaRecoger").asInt());
        assertEquals("ENTREGAR", reglas.path("alVencerPendientes").asText());
    }

    // --- ficha (GET /subastas/{id}) -------------------------------------------------

    @Test
    @DisplayName("la ficha es publica, sirve para cerradas y cuenta una visualizacion por jugador")
    void ficha() throws Exception {
        UUID vendedor = jugadorConSaldo();
        Subasta subasta = publicada(vendedor);
        UUID curioso = jugadorConSaldo();

        JsonNode anonima = json(pedir("GET", "/subastas/" + subasta.getId(), null, null));
        assertEquals("ACTIVA", anonima.path("estado").asText());
        assertMonto("100", anonima.path("pujaMinimaSiguiente"));
        assertEquals("forjador", anonima.path("vendedorApodo").asText());
        assertTrue(anonima.path("compraInmediataDisponible").asBoolean());
        assertEquals(0, anonima.path("vistas").asInt());

        assertEquals(1, json(pedir("GET", "/subastas/" + subasta.getId(), null, token(curioso))).path("vistas").asInt());
        assertEquals(1, json(pedir("GET", "/subastas/" + subasta.getId(), null, token(curioso))).path("vistas").asInt());
        assertEquals(1, json(pedir("GET", "/subastas/" + subasta.getId(), null, token(vendedor))).path("vistas").asInt(),
                "el vendedor no cuenta");

        assertEquals(200, pedir("POST", "/subastas/" + subasta.getId() + "/cancelacion", null, token(vendedor))
                .statusCode());
        JsonNode cerrada = json(pedir("GET", "/subastas/" + subasta.getId(), null, null));
        assertEquals("CANCELADA", cerrada.path("estado").asText());
        assertTrue(cerrada.path("pujaMinimaSiguiente").isNull());
        assertEquals(1, cerrada.path("reputacionVendedor").path("cancelaciones").asInt());

        assertEquals(404, pedir("GET", "/subastas/" + UUID.randomUUID(), null, null).statusCode());
    }

    // --- pendientes de recoger (7.7.9) ------------------------------------------------

    @Test
    @DisplayName("ganar al vencer deja el producto pendiente 7 dias; recogerlo lo confirma, una sola vez")
    void ganarYRecoger() throws Exception {
        UUID vendedor = jugadorConSaldo();
        UUID ganador = jugadorConSaldo();
        Subasta subasta = publicada(vendedor);
        pujar(subasta, ganador, "150");

        pujas.cerrarPorVencimiento(subasta.getId());

        JsonNode mios = json(pedir("GET", "/mis-subastas/pendientes", null, token(ganador)));
        assertEquals(1, mios.size());
        JsonNode pendiente = mios.get(0);
        assertEquals(subasta.getId().toString(), pendiente.path("subastaId").asText());
        assertEquals("PENDIENTE", pendiente.path("estado").asText());
        assertMonto("150", pendiente.path("montoPagado"));
        Instant vence = Instant.parse(pendiente.path("venceEn").asText());
        Instant ganada = Instant.parse(pendiente.path("ganadaEn").asText());
        assertEquals(Duration.ofDays(7), Duration.between(ganada, vence));
        assertEquals(1, avisosDe(subasta.getId(), TipoNotificacion.SUBASTA_GANADA).size());
        assertEquals(1, avisosDe(subasta.getId(), TipoNotificacion.SUBASTA_VENDIDA).size());
        // HU-NOT-003: la confirmacion de creditos pasa la restriccion de la base (V11) y va al vendedor.
        assertEquals(List.of(vendedor), avisosDe(subasta.getId(), TipoNotificacion.CREDITOS_RECIBIDOS).stream()
                .map(NotificacionPendiente::getDestinatarioId).toList());

        HttpResponse<String> ajeno = pedir("POST", "/mis-subastas/pendientes/" + subasta.getId() + "/recogida", null,
                token(jugadorConSaldo()));
        assertEquals(404, ajeno.statusCode(), ajeno.body());
        assertEquals("PENDIENTE_NO_ENCONTRADO", motivo(ajeno));

        HttpResponse<String> recogida = pedir("POST", "/mis-subastas/pendientes/" + subasta.getId() + "/recogida",
                null, token(ganador));
        assertEquals(200, recogida.statusCode(), recogida.body());
        assertEquals("RECOGIDO", json(recogida).path("estado").asText());
        assertEquals(200, pedir("POST", "/mis-subastas/pendientes/" + subasta.getId() + "/recogida", null,
                token(ganador)).statusCode(), "recoger dos veces no es un error");
        assertEquals(1, avisosDe(subasta.getId(), TipoNotificacion.PRODUCTO_RECOGIDO).size());
        assertEquals(0, json(pedir("GET", "/mis-subastas/pendientes", null, token(ganador))).size());
    }

    @Test
    @DisplayName("«recoger todo» recoge cada pendiente")
    void recogerTodo() throws Exception {
        UUID ganador = jugadorConSaldo();
        Subasta primera = publicada(jugadorConSaldo());
        Subasta segunda = publicada(jugadorConSaldo());
        pujar(primera, ganador, "100");
        pujar(segunda, ganador, "100");
        pujas.cerrarPorVencimiento(primera.getId());
        pujas.cerrarPorVencimiento(segunda.getId());

        HttpResponse<String> respuesta = pedir("POST", "/mis-subastas/pendientes/recogida", null, token(ganador));

        assertEquals(200, respuesta.statusCode(), respuesta.body());
        assertEquals(2, json(respuesta).path("recogidos").size());
        assertEquals(0, json(respuesta).path("fallidos").size());
    }

    @Test
    @DisplayName("al vencer los 7 dias se aplica la politica provisional ENTREGAR y ya no se recoge a mano")
    void vencerLosSieteDias() throws Exception {
        UUID vendedor = jugadorConSaldo();
        UUID ganador = jugadorConSaldo();
        Subasta subasta = publicada(vendedor);
        subasta.cerrar(EstadoSubasta.ADJUDICADA, Instant.now().minus(Duration.ofDays(8)));
        subastas.saveAndFlush(subasta);
        pendientes.saveAndFlush(new PendienteDeRecoger(subasta.getId(), ganador, subasta.getElementoInventarioId(),
                new BigDecimal("150"), Instant.now().minus(Duration.ofDays(8)),
                Instant.now().minus(Duration.ofDays(1))));

        vencimientos.resolverVencidos();

        assertEquals(EstadoPendiente.ENTREGADO_AL_VENCER, pendientes.findById(subasta.getId()).orElseThrow().getEstado());
        assertEquals(1, avisosDe(subasta.getId(), TipoNotificacion.PENDIENTE_VENCIDO).size());
        HttpResponse<String> tarde = pedir("POST", "/mis-subastas/pendientes/" + subasta.getId() + "/recogida", null,
                token(ganador));
        assertEquals(409, tarde.statusCode(), tarde.body());
        assertEquals("PENDIENTE_YA_RESUELTO", motivo(tarde));
    }

    // --- historial y mis pujas (7.7.9) ------------------------------------------------

    @Test
    @DisplayName("el historial suma compras, ventas y comisiones, y se exporta en CSV")
    void historial() throws Exception {
        UUID vendedor = jugadorConSaldo();
        UUID comprador = jugadorConSaldo();
        Subasta vendida = publicada(vendedor);
        pujar(vendida, comprador, "200");
        pujas.cerrarPorVencimiento(vendida.getId());

        JsonNode delVendedor = json(pedir("GET", "/mis-subastas/historial", null, token(vendedor)));
        assertMonto("200", delVendedor.path("totalGanado"));
        assertMonto("3", delVendedor.path("comisionesPagadas"));
        assertMonto("197", delVendedor.path("balance"));
        JsonNode delComprador = json(pedir("GET", "/mis-subastas/historial", null, token(comprador)));
        assertEquals("COMPRA", delComprador.path("movimientos").get(0).path("tipo").asText());
        assertMonto("200", delComprador.path("totalGastado"));

        HttpResponse<String> csv = pedir("GET", "/mis-subastas/historial?formato=csv", null, token(vendedor));
        assertEquals(200, csv.statusCode());
        assertTrue(csv.headers().firstValue("Content-Type").orElse("").startsWith("text/csv"));
        assertTrue(csv.headers().firstValue("Content-Disposition").orElse("").contains("attachment"));
        assertTrue(csv.body().startsWith("tipo,subastaId,producto,monto,fecha\n"), csv.body());
        assertTrue(csv.body().contains("VENTA," + vendida.getId()), csv.body());
    }

    @Test
    @DisplayName("mis pujas lista tambien las subastas ya cerradas")
    void misPujas() throws Exception {
        UUID yo = jugadorConSaldo();
        Subasta abierta = publicada(jugadorConSaldo());
        Subasta ganada = publicada(jugadorConSaldo());
        pujar(abierta, yo, "100");
        pujar(ganada, yo, "100");
        pujas.cerrarPorVencimiento(ganada.getId());

        JsonNode mias = json(pedir("GET", "/mis-pujas", null, token(yo)));

        assertEquals(2, mias.size());
        assertEquals(1, json(pedir("GET", "/mis-pujas/resumen", null, token(yo))).path("subastasGanando").asInt());
    }

    // --- recordatorio (7.7.8) ---------------------------------------------------------

    @Test
    @DisplayName("1 hora antes del cierre avisa al vendedor, a quien pujo y a quien la sigue, una sola vez (RF-NOT-003)")
    void recordatorioDeCierre() throws Exception {
        UUID vendedor = jugadorConSaldo();
        UUID postor = jugadorConSaldo();
        UUID seguidor = jugadorConSaldo();
        Subasta subasta = publicada(vendedor, Duration.ofMinutes(40));
        pujar(subasta, postor, "100");
        assertEquals(204, pedir("PUT", "/subastas/" + subasta.getId() + "/seguimiento", null, token(seguidor))
                .statusCode());

        recordatorios.recordar();
        recordatorios.recordar();

        List<UUID> avisados = avisosDe(subasta.getId(), TipoNotificacion.RECORDATORIO_CIERRE).stream()
                .map(NotificacionPendiente::getDestinatarioId).sorted().toList();
        assertEquals(List.of(vendedor, postor, seguidor).stream().sorted().toList(), avisados);
        assertNotNull(subastas.findById(subasta.getId()).orElseThrow().getRecordatorioEnviadoEn());
    }
}
