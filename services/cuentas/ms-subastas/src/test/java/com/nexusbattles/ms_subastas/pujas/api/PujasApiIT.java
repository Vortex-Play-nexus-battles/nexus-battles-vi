package com.nexusbattles.ms_subastas.pujas.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClient;
import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClientFake;
import com.nexusbattles.ms_subastas.pujas.model.PujaAutomatica;
import com.nexusbattles.ms_subastas.pujas.repository.PujaAutomaticaRepository;
import com.nexusbattles.ms_subastas.pujas.service.PujaApplicationService;
import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClient;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClientFake;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HU-SUB-004 de extremo a extremo: peticion HTTP real con JWT real contra
 * Postgres real. Es la unica prueba que ejerce la cadena entera —
 * token -> IdentidadDesdeToken -> controlador -> servicio -> lock -> BD —
 * y por tanto la unica que habria detectado que el puerto IdentidadClient no
 * tenia implementacion, cosa que ninguna prueba con dobles puede ver.
 *
 * <p>Sin @WebMvcTest/MockMvc, por el mismo motivo que documenta
 * ManejadorDeErroresSubastasIT: no esta confirmado que ese starter este en el
 * classpath del modulo. Se usa java.net.http del JDK, sin dependencias nuevas.
 *
 * <p>Los dos jobs programados se desactivan subiendo su intervalo: si el de
 * emision automatica corriera en medio, pujaria por su cuenta y las
 * aserciones sobre la oferta vigente serian una loteria.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.finanzas.modo=prueba",
                "app.jwt.clave-secreta=" + PujasApiIT.CLAVE_DE_FIRMA,
                "app.pujas.emision-automatica-intervalo-ms=3600000",
                "app.subastas.cierre-intervalo-ms=3600000",
                // El drenador intentaria entregar los avisos a un modulo de
                // notificaciones que aqui no existe: llenaria el log de avisos
                // de conexion rechazada sin aportar nada a estas pruebas.
                "app.notificaciones.drenaje-intervalo-ms=3600000"
        })
@Testcontainers(disabledWithoutDocker = true)
class PujasApiIT {

    static final String CLAVE_DE_FIRMA = "clave-de-prueba-para-el-jwt-de-ms-subastas-de-mas-de-32-bytes";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    /**
     * Reemplaza el doble de creditos por uno accesible desde la prueba: el bean
     * de produccion lo envuelve en CreditoClientResiliente y no deja llegar a
     * acreditar(). Con app.finanzas.modo=prueba, aquel no se crea.
     */
    @TestConfiguration
    static class CreditosDePrueba {
        @Bean
        CreditoClientFake creditoClientFake() {
            return new CreditoClientFake();
        }
    }

    @LocalServerPort
    private int puerto;

    @Autowired
    private SubastaRepository subastas;

    @Autowired
    private PujaAutomaticaRepository pujasAutomaticas;

    @Autowired
    private CreditoClient creditos;

    @Autowired
    private InventarioClient inventario;

    @Autowired
    private PujaApplicationService pujaApplicationService;

    private final HttpClient cliente = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    // --- utilidades -------------------------------------------------------

    private UUID jugadorConSaldo(String creditos) {
        UUID jugador = UUID.randomUUID();
        ((CreditoClientFake) this.creditos).acreditar(jugador, new BigDecimal(creditos));
        return jugador;
    }

    private Subasta subastaActiva(UUID vendedor, String ofertaVigente, String compraInmediata) {
        Subasta subasta = new Subasta();
        subasta.setProductoId(UUID.randomUUID());
        subasta.setElementoInventarioId("elem-" + UUID.randomUUID());
        subasta.setVendedorId(vendedor);
        subasta.setOfertaVigente(new BigDecimal(ofertaVigente));
        subasta.setIncrementoMinimo(new BigDecimal("10"));
        subasta.setPrecioCompraInmediata(compraInmediata == null ? null : new BigDecimal(compraInmediata));
        subasta.setEstado(EstadoSubasta.ACTIVA);
        subasta.setFechaFin(Instant.now().plus(Duration.ofDays(1)));
        return subastas.saveAndFlush(subasta);
    }

    private String tokenDe(UUID jugadorId) {
        var constructor = Jwts.builder()
                .subject("jugador_" + (jugadorId == null ? "sin_uid" : jugadorId.toString().substring(0, 8)))
                .claim("rol", "JUGADOR")
                .claim("ver", 1)
                .expiration(Date.from(Instant.now().plus(Duration.ofHours(1))));
        if (jugadorId != null) {
            constructor.claim("uid", jugadorId.toString());
        }
        return constructor.signWith(clave()).compact();
    }

    private static SecretKey clave() {
        return Keys.hmacShaKeyFor(CLAVE_DE_FIRMA.getBytes(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> enviar(String metodo, String ruta, String cuerpo, String token, String idempotencyKey)
            throws Exception {
        HttpRequest.Builder constructor = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + puerto + "/api/v1" + ruta))
                .header("Content-Type", "application/json");
        if (token != null) {
            constructor.header("Authorization", "Bearer " + token);
        }
        if (idempotencyKey != null) {
            constructor.header("Idempotency-Key", idempotencyKey);
        }
        constructor.method(metodo, cuerpo == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(cuerpo));
        return cliente.send(constructor.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String claveNueva() {
        return "idem-" + UUID.randomUUID();
    }

    private String motivoDe(HttpResponse<String> respuesta) throws Exception {
        assertTrue(respuesta.headers().firstValue("Content-Type").orElse("").contains("application/problem+json"),
                "regla 4 de plataforma: el error va en problem+json");
        return mapper.readTree(respuesta.body()).path("motivo").asText();
    }

    // --- autenticacion ----------------------------------------------------

    /**
     * Sin CORS el navegador bloquea la peticion antes de enviarla y la pantalla
     * de subastas no carga nada, ni siquiera el listado publico. Se comprueba
     * sobre el preflight de pujar porque es el caso que mas facil se rompe:
     * Idempotency-Key no esta entre las cabeceras que CORS admite por defecto.
     */
    @Test
    void elPreflightDePujarPermiteAlFrontendDeDesarrollo() throws Exception {
        HttpRequest preflight = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + puerto + "/api/v1/subastas/" + UUID.randomUUID() + "/pujas"))
                .header("Origin", "http://localhost:8080")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type,idempotency-key")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> respuesta = cliente.send(preflight, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, respuesta.statusCode(), respuesta.body());
        assertEquals("http://localhost:8080",
                respuesta.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
        assertTrue(respuesta.headers().firstValue("Access-Control-Allow-Headers").orElse("")
                        .toLowerCase().contains("idempotency-key"),
                "sin esta cabecera declarada, pujar y comprar fallan en el preflight");
    }

    @Test
    void unOrigenNoAutorizadoNoRecibePermiso() throws Exception {
        HttpRequest preflight = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + puerto + "/api/v1/subastas/" + UUID.randomUUID() + "/pujas"))
                .header("Origin", "http://sitio-que-no-es-nuestro.example")
                .header("Access-Control-Request-Method", "POST")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> respuesta = cliente.send(preflight, HttpResponse.BodyHandlers.ofString());

        assertTrue(respuesta.headers().firstValue("Access-Control-Allow-Origin").isEmpty(),
                "por aqui pasan operaciones que mueven creditos: la lista de origenes es explicita");
    }

    @Test
    void pujarSinTokenDevuelve401() throws Exception {
        Subasta subasta = subastaActiva(UUID.randomUUID(), "100", "500");

        HttpResponse<String> respuesta = enviar("POST", "/subastas/" + subasta.getId() + "/pujas",
                "{\"monto\":\"110\"}", null, claveNueva());

        assertEquals(401, respuesta.statusCode());
    }

    /**
     * El caso que preguntaba Edwin: token bien firmado y sin expirar, pero sin
     * el claim uid. No se puede dejar pasar hacia una operacion que retiene
     * creditos, porque no se sabe a quien cobrarle.
     */
    @Test
    void pujarConUnTokenSinIdentificadorDeJugadorDevuelve401() throws Exception {
        Subasta subasta = subastaActiva(UUID.randomUUID(), "100", "500");

        HttpResponse<String> respuesta = enviar("POST", "/subastas/" + subasta.getId() + "/pujas",
                "{\"monto\":\"110\"}", tokenDe(null), claveNueva());

        assertEquals(401, respuesta.statusCode());
    }

    @Test
    void pujarConUnTokenFirmadoConOtraClaveDevuelve401() throws Exception {
        Subasta subasta = subastaActiva(UUID.randomUUID(), "100", "500");
        String ajeno = Jwts.builder()
                .subject("intruso").claim("rol", "JUGADOR").claim("ver", 1)
                .claim("uid", UUID.randomUUID().toString())
                .expiration(Date.from(Instant.now().plus(Duration.ofHours(1))))
                .signWith(Keys.hmacShaKeyFor("otra-clave-igual-de-larga-pero-que-no-es-la-del-servicio".getBytes(StandardCharsets.UTF_8)))
                .compact();

        HttpResponse<String> respuesta = enviar("POST", "/subastas/" + subasta.getId() + "/pujas",
                "{\"monto\":\"110\"}", ajeno, claveNueva());

        assertEquals(401, respuesta.statusCode());
    }

    // --- pujar ------------------------------------------------------------

    @Test
    void unaPujaValidaDevuelve201YSeConvierteEnLaOfertaVigente() throws Exception {
        UUID jugador = jugadorConSaldo("1000");
        Subasta subasta = subastaActiva(UUID.randomUUID(), "100", "500");

        HttpResponse<String> respuesta = enviar("POST", "/subastas/" + subasta.getId() + "/pujas",
                "{\"monto\":\"110\"}", tokenDe(jugador), claveNueva());

        assertEquals(201, respuesta.statusCode(), respuesta.body());
        JsonNode json = mapper.readTree(respuesta.body());
        assertEquals(jugador.toString(), json.get("jugadorId").asText());
        assertEquals("ACTIVA", json.get("estado").asText());

        // Por contrato el monto viaja como cadena: un decimal en JSON pasa por
        // el double de JavaScript y ahi 0.1 + 0.2 deja de ser 0.3.
        assertTrue(json.get("monto").isTextual(), "monto debe ser cadena, no numero: " + respuesta.body());
        assertTrue(json.get("creadaEn").isTextual(), "creadaEn debe ser ISO-8601, no epoch: " + respuesta.body());

        Subasta recargada = subastas.findById(subasta.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("110.00").compareTo(recargada.getOfertaVigente()));
        assertEquals(jugador, recargada.getMejorPostorId());
        assertEquals(1, recargada.getCantidadPujas(), "el contador que muestra el listado tiene que subir");
    }

    /**
     * El caso que motiva toda la cabecera Idempotency-Key: la puja entra, la
     * respuesta se pierde de vuelta y el navegador reintenta. Antes el
     * reintento llegaba con el precio ya subido por su propia puja y se
     * rechazaba con OFERTA_INSUFICIENTE, de modo que el jugador no podia saber
     * si habia pujado. Ahora recibe su misma puja.
     */
    @Test
    void reintentarLaMismaPujaConLaMismaClaveDevuelveLaPujaOriginal() throws Exception {
        UUID jugador = jugadorConSaldo("1000");
        Subasta subasta = subastaActiva(UUID.randomUUID(), "100", "500");
        String clave = claveNueva();

        HttpResponse<String> primera = enviar("POST", "/subastas/" + subasta.getId() + "/pujas",
                "{\"monto\":\"110\"}", tokenDe(jugador), clave);
        assertEquals(201, primera.statusCode(), primera.body());

        HttpResponse<String> reintento = enviar("POST", "/subastas/" + subasta.getId() + "/pujas",
                "{\"monto\":\"110\"}", tokenDe(jugador), clave);

        assertEquals(201, reintento.statusCode(), reintento.body());
        assertEquals(mapper.readTree(primera.body()).get("id").asText(),
                mapper.readTree(reintento.body()).get("id").asText(),
                "el reintento debe devolver la misma puja, no una nueva");

        // Y sobre todo: la subasta no se movio dos veces.
        Subasta recargada = subastas.findById(subasta.getId()).orElseThrow();
        assertEquals(1, recargada.getCantidadPujas(),
                "un reintento no puede contar como una segunda puja");
        assertEquals(0, new BigDecimal("110.00").compareTo(recargada.getOfertaVigente()));
    }

    @Test
    void reintentarLaCompraInmediataConLaMismaClaveDevuelveLaCompraOriginal() throws Exception {
        UUID comprador = jugadorConSaldo("1000");
        Subasta subasta = subastaActiva(UUID.randomUUID(), "100", "500");
        String clave = claveNueva();

        HttpResponse<String> primera = enviar("POST", "/subastas/" + subasta.getId() + "/compra-inmediata",
                "{\"confirmado\":true}", tokenDe(comprador), clave);
        assertEquals(201, primera.statusCode(), primera.body());

        // Sin reproduccion esto devolveria 409 SUBASTA_NO_ACTIVA: la subasta ya
        // esta ADJUDICADA por la primera compra, y el comprador no podria
        // distinguir "ya es tuyo" de "llegaste tarde".
        HttpResponse<String> reintento = enviar("POST", "/subastas/" + subasta.getId() + "/compra-inmediata",
                "{\"confirmado\":true}", tokenDe(comprador), clave);

        assertEquals(201, reintento.statusCode(), reintento.body());
        assertEquals(mapper.readTree(primera.body()).get("id").asText(),
                mapper.readTree(reintento.body()).get("id").asText());
    }

    @Test
    void reutilizarLaClaveEnOtraSubastaSeRechazaConSuMotivo() throws Exception {
        UUID jugador = jugadorConSaldo("1000");
        Subasta primera = subastaActiva(UUID.randomUUID(), "100", "500");
        Subasta otra = subastaActiva(UUID.randomUUID(), "100", "500");
        String clave = claveNueva();

        assertEquals(201, enviar("POST", "/subastas/" + primera.getId() + "/pujas",
                "{\"monto\":\"110\"}", tokenDe(jugador), clave).statusCode());

        HttpResponse<String> enOtra = enviar("POST", "/subastas/" + otra.getId() + "/pujas",
                "{\"monto\":\"110\"}", tokenDe(jugador), clave);

        assertEquals(422, enOtra.statusCode(), enOtra.body());
        assertEquals("CLAVE_REUTILIZADA", motivoDe(enOtra));
    }

    /**
     * 409 y no 422: cuando la interfaz pinto la pantalla la puja era valida, y
     * dejo de serlo porque otro se adelanto. Releer y reintentar tiene sentido.
     */
    @Test
    void pujarPorDebajoDelIncrementoMinimoDevuelve409ConSuMotivo() throws Exception {
        UUID jugador = jugadorConSaldo("1000");
        Subasta subasta = subastaActiva(UUID.randomUUID(), "100", "500");

        HttpResponse<String> respuesta = enviar("POST", "/subastas/" + subasta.getId() + "/pujas",
                "{\"monto\":\"105\"}", tokenDe(jugador), claveNueva());

        assertEquals(409, respuesta.statusCode(), respuesta.body());
        assertEquals("OFERTA_INSUFICIENTE", motivoDe(respuesta));
    }

    @Test
    void elVendedorNoPuedePujarEnSuPropiaSubasta() throws Exception {
        UUID vendedor = jugadorConSaldo("1000");
        Subasta subasta = subastaActiva(vendedor, "100", "500");

        HttpResponse<String> respuesta = enviar("POST", "/subastas/" + subasta.getId() + "/pujas",
                "{\"monto\":\"110\"}", tokenDe(vendedor), claveNueva());

        assertEquals(422, respuesta.statusCode(), respuesta.body());
        assertEquals("PUJA_PROPIA", motivoDe(respuesta));
    }

    @Test
    void pujarSinSaldoSuficienteDevuelve422() throws Exception {
        UUID jugador = jugadorConSaldo("50");
        Subasta subasta = subastaActiva(UUID.randomUUID(), "100", "500");

        HttpResponse<String> respuesta = enviar("POST", "/subastas/" + subasta.getId() + "/pujas",
                "{\"monto\":\"110\"}", tokenDe(jugador), claveNueva());

        assertEquals(422, respuesta.statusCode(), respuesta.body());
        assertEquals("SALDO_INSUFICIENTE", motivoDe(respuesta));
    }

    @Test
    void pujarEnUnaSubastaInexistenteDevuelve404() throws Exception {
        UUID jugador = jugadorConSaldo("1000");

        HttpResponse<String> respuesta = enviar("POST", "/subastas/" + UUID.randomUUID() + "/pujas",
                "{\"monto\":\"110\"}", tokenDe(jugador), claveNueva());

        assertEquals(404, respuesta.statusCode(), respuesta.body());
    }

    /**
     * La cabecera es obligatoria por contrato justamente para que el servidor no
     * tenga que inventarla: derivada del reloj, un reintento del cliente por
     * timeout traeria otra clave y ms-finanzas reservaria dos veces.
     */
    @Test
    void pujarSinCabeceraDeIdempotenciaDevuelve400() throws Exception {
        UUID jugador = jugadorConSaldo("1000");
        Subasta subasta = subastaActiva(UUID.randomUUID(), "100", "500");

        HttpResponse<String> respuesta = enviar("POST", "/subastas/" + subasta.getId() + "/pujas",
                "{\"monto\":\"110\"}", tokenDe(jugador), null);

        assertEquals(400, respuesta.statusCode(), respuesta.body());
    }

    /**
     * El contrato fija la clave entre 8 y 128 caracteres. Sin esta prueba, la
     * restriccion estaria escrita en el contrato y en la anotacion pero nadie
     * habria comprobado que Spring la aplica de verdad sobre una cabecera: el
     * 400 de la prueba anterior lo produce la cabecera ausente, que es otro
     * mecanismo distinto.
     */
    @Test
    void pujarConUnaClaveDeIdempotenciaDemasiadoCortaDevuelve400() throws Exception {
        UUID jugador = jugadorConSaldo("1000");
        Subasta subasta = subastaActiva(UUID.randomUUID(), "100", "500");

        HttpResponse<String> respuesta = enviar("POST", "/subastas/" + subasta.getId() + "/pujas",
                "{\"monto\":\"110\"}", tokenDe(jugador), "corta");

        assertEquals(400, respuesta.statusCode(), respuesta.body());
    }

    @Test
    void pujarConUnMontoNoPositivoDevuelve400() throws Exception {
        UUID jugador = jugadorConSaldo("1000");
        Subasta subasta = subastaActiva(UUID.randomUUID(), "100", "500");

        HttpResponse<String> respuesta = enviar("POST", "/subastas/" + subasta.getId() + "/pujas",
                "{\"monto\":\"0\"}", tokenDe(jugador), claveNueva());

        assertEquals(400, respuesta.statusCode(), respuesta.body());
    }

    // --- compra inmediata -------------------------------------------------

    @Test
    void laCompraInmediataConfirmadaCierraLaSubasta() throws Exception {
        UUID comprador = jugadorConSaldo("1000");
        Subasta subasta = subastaActiva(UUID.randomUUID(), "100", "500");

        HttpResponse<String> respuesta = enviar("POST", "/subastas/" + subasta.getId() + "/compra-inmediata",
                "{\"confirmado\":true}", tokenDe(comprador), claveNueva());

        assertEquals(201, respuesta.statusCode(), respuesta.body());
        JsonNode json = mapper.readTree(respuesta.body());
        assertEquals("GANADORA", json.get("estado").asText());
        assertEquals(comprador.toString(), json.get("jugadorId").asText());

        Subasta recargada = subastas.findById(subasta.getId()).orElseThrow();
        assertEquals(EstadoSubasta.ADJUDICADA, recargada.getEstado());

        if (inventario instanceof InventarioClientFake fake) {
            var elemento = fake.buscar(subasta.getElementoInventarioId());
            assertTrue(elemento.isPresent());
            assertEquals(comprador, elemento.get().propietarioId(),
                    "El item debio transferirse formalmente al nuevo dueno");
            assertFalse(elemento.get().enUso());
        }
    }

    @Test
    void laCompraInmediataConFalloEnInventarioDevuelve500YNoDebitaCreditos() throws Exception {
        UUID comprador = jugadorConSaldo("1000");
        Subasta subasta = subastaActiva(UUID.randomUUID(), "100", "500");

        if (inventario instanceof InventarioClientFake fake) {
            fake.simularFallo(true, "Inventario no disponible");
        }

        try {
            HttpResponse<String> respuesta = enviar("POST", "/subastas/" + subasta.getId() + "/compra-inmediata",
                    "{\"confirmado\":true}", tokenDe(comprador), claveNueva());

            assertEquals(500, respuesta.statusCode(), respuesta.body());
            Subasta recargada = subastas.findById(subasta.getId()).orElseThrow();
            assertEquals(EstadoSubasta.ACTIVA, recargada.getEstado());
            assertEquals(new BigDecimal("1000"), creditos.saldoDisponible(comprador));
        } finally {
            if (inventario instanceof InventarioClientFake fake) {
                fake.simularFallo(false);
            }
        }
    }

    @Test
    void laCompraInmediataConPujaVigenteLiberaCreditosDePostorAnteriorYTransfiereItemAlComprador() throws Exception {
        UUID postor = jugadorConSaldo("1000");
        UUID comprador = jugadorConSaldo("1000");
        Subasta subasta = subastaActiva(UUID.randomUUID(), "100", "500");

        // Postor oferta 150
        HttpResponse<String> pujaResp = enviar("POST", "/subastas/" + subasta.getId() + "/pujas",
                "{\"monto\":\"150\"}", tokenDe(postor), claveNueva());
        assertEquals(201, pujaResp.statusCode());
        assertEquals(0, new BigDecimal("850.00").compareTo(creditos.saldoDisponible(postor)));

        // Comprador ejecuta compra inmediata por 500
        HttpResponse<String> compraResp = enviar("POST", "/subastas/" + subasta.getId() + "/compra-inmediata",
                "{\"confirmado\":true}", tokenDe(comprador), claveNueva());
        assertEquals(201, compraResp.statusCode(), compraResp.body());

        // El postor anterior recuperó sus 1000 créditos
        assertEquals(0, new BigDecimal("1000.00").compareTo(creditos.saldoDisponible(postor)));
        // El comprador pagó 500
        assertEquals(0, new BigDecimal("500.00").compareTo(creditos.saldoDisponible(comprador)));

        // Subasta quedó adjudicada al comprador
        Subasta recargada = subastas.findById(subasta.getId()).orElseThrow();
        assertEquals(EstadoSubasta.ADJUDICADA, recargada.getEstado());
        assertEquals(comprador, recargada.getMejorPostorId());

        // El inventario quedó transferido formalmente al comprador
        if (inventario instanceof InventarioClientFake fake) {
            var elemento = fake.buscar(subasta.getElementoInventarioId());
            assertTrue(elemento.isPresent());
            assertEquals(comprador, elemento.get().propietarioId());
            assertFalse(elemento.get().enUso());
        }
    }

    @Test
    void elCierrePorVencimientoTransfiereElProductoAlMejorPostor() throws Exception {
        UUID postor = jugadorConSaldo("1000");
        Subasta subasta = subastaActiva(UUID.randomUUID(), "100", "500");

        HttpResponse<String> respuesta = enviar("POST", "/subastas/" + subasta.getId() + "/pujas",
                "{\"monto\":\"150\"}", tokenDe(postor), claveNueva());
        assertEquals(201, respuesta.statusCode());

        pujaApplicationService.cerrarPorVencimiento(subasta.getId());

        Subasta recargada = subastas.findById(subasta.getId()).orElseThrow();
        assertEquals(EstadoSubasta.ADJUDICADA, recargada.getEstado());

        if (inventario instanceof InventarioClientFake fake) {
            var elemento = fake.buscar(subasta.getElementoInventarioId());
            assertTrue(elemento.isPresent());
            assertEquals(postor, elemento.get().propietarioId(),
                    "El item debio transferirse formalmente al ganador de la subasta vencida");
            assertFalse(elemento.get().enUso());
        }
    }

    /**
     * Lo que la historia pide como "confirmacion explicita" se comprueba en el
     * servidor: una interfaz con un bug no puede cerrar una compra por accidente.
     */
    @Test
    void laCompraInmediataSinConfirmarSeRechaza() throws Exception {
        UUID comprador = jugadorConSaldo("1000");
        Subasta subasta = subastaActiva(UUID.randomUUID(), "100", "500");

        HttpResponse<String> respuesta = enviar("POST", "/subastas/" + subasta.getId() + "/compra-inmediata",
                "{\"confirmado\":false}", tokenDe(comprador), claveNueva());

        assertEquals(422, respuesta.statusCode(), respuesta.body());
        assertEquals("CONFIRMACION_REQUERIDA", motivoDe(respuesta));
        assertEquals(EstadoSubasta.ACTIVA, subastas.findById(subasta.getId()).orElseThrow().getEstado());
    }

    /**
     * Antes era un IllegalStateException, que por HTTP habria salido como un 500
     * para algo que el jugador puede entender y corregir.
     */
    @Test
    void comprarUnaSubastaSinPrecioDeCompraInmediataDevuelve422() throws Exception {
        UUID comprador = jugadorConSaldo("1000");
        Subasta subasta = subastaActiva(UUID.randomUUID(), "100", null);

        HttpResponse<String> respuesta = enviar("POST", "/subastas/" + subasta.getId() + "/compra-inmediata",
                "{\"confirmado\":true}", tokenDe(comprador), claveNueva());

        assertEquals(422, respuesta.statusCode(), respuesta.body());
        assertEquals("SIN_COMPRA_INMEDIATA", motivoDe(respuesta));
    }

    // --- puja automatica --------------------------------------------------

    @Test
    void configurarLaPujaAutomaticaDevuelve200YLaDejaGuardada() throws Exception {
        UUID jugador = jugadorConSaldo("1000");
        Subasta subasta = subastaActiva(UUID.randomUUID(), "100", "500");

        HttpResponse<String> respuesta = enviar("PUT", "/subastas/" + subasta.getId() + "/puja-automatica",
                "{\"limite\":\"400\"}", tokenDe(jugador), null);

        assertEquals(200, respuesta.statusCode(), respuesta.body());
        JsonNode json = mapper.readTree(respuesta.body());
        assertTrue(json.get("activa").asBoolean());
        assertTrue(json.get("limite").isTextual(), "limite debe ser cadena: " + respuesta.body());
        assertNotNull(json.get("id").asText());

        Optional<PujaAutomatica> guardada =
                pujasAutomaticas.findBySubastaIdAndJugadorId(subasta.getId(), jugador);
        assertTrue(guardada.isPresent());
        assertEquals(0, new BigDecimal("400.00").compareTo(guardada.orElseThrow().getLimite()));
    }

    /**
     * Es un PUT: repetirlo tiene que dejar el mismo estado, no reventar contra
     * el unico (subasta_id, jugador_id) de la tabla.
     */
    @Test
    void reconfigurarLaPujaAutomaticaNoCreaUnaSegundaFila() throws Exception {
        UUID jugador = jugadorConSaldo("1000");
        Subasta subasta = subastaActiva(UUID.randomUUID(), "100", "500");
        String ruta = "/subastas/" + subasta.getId() + "/puja-automatica";

        HttpResponse<String> primera = enviar("PUT", ruta, "{\"limite\":\"300\"}", tokenDe(jugador), null);
        HttpResponse<String> segunda = enviar("PUT", ruta, "{\"limite\":\"450\"}", tokenDe(jugador), null);

        assertEquals(200, primera.statusCode(), primera.body());
        assertEquals(200, segunda.statusCode(), segunda.body());
        assertEquals(mapper.readTree(primera.body()).get("id").asText(),
                mapper.readTree(segunda.body()).get("id").asText(),
                "la segunda tiene que actualizar la misma fila");
        assertEquals(0, new BigDecimal("450.00").compareTo(
                pujasAutomaticas.findBySubastaIdAndJugadorId(subasta.getId(), jugador).orElseThrow().getLimite()));
    }

    @Test
    void unLimiteQueNuncaLlegariaAPujarDevuelve422() throws Exception {
        UUID jugador = jugadorConSaldo("1000");
        Subasta subasta = subastaActiva(UUID.randomUUID(), "100", "500");

        // La siguiente oferta valida es 110: un limite de 105 no alcanza nunca.
        HttpResponse<String> respuesta = enviar("PUT", "/subastas/" + subasta.getId() + "/puja-automatica",
                "{\"limite\":\"105\"}", tokenDe(jugador), null);

        assertEquals(422, respuesta.statusCode(), respuesta.body());
        assertEquals("LIMITE_AUTOMATICO_INALCANZABLE", motivoDe(respuesta));
    }

    @Test
    void desactivarLaPujaAutomaticaDevuelve204YLaApaga() throws Exception {
        UUID jugador = jugadorConSaldo("1000");
        Subasta subasta = subastaActiva(UUID.randomUUID(), "100", "500");
        String ruta = "/subastas/" + subasta.getId() + "/puja-automatica";
        enviar("PUT", ruta, "{\"limite\":\"400\"}", tokenDe(jugador), null);

        HttpResponse<String> respuesta = enviar("DELETE", ruta, null, tokenDe(jugador), null);

        assertEquals(204, respuesta.statusCode(), respuesta.body());
        assertEquals(false,
                pujasAutomaticas.findBySubastaIdAndJugadorId(subasta.getId(), jugador).orElseThrow().isActiva());
    }

    /** Reintentar un DELETE tras un timeout no puede convertirse en un 404. */
    @Test
    void desactivarSinHaberConfiguradoNingunaTambienDevuelve204() throws Exception {
        UUID jugador = jugadorConSaldo("1000");
        Subasta subasta = subastaActiva(UUID.randomUUID(), "100", "500");

        HttpResponse<String> respuesta = enviar("DELETE",
                "/subastas/" + subasta.getId() + "/puja-automatica", null, tokenDe(jugador), null);

        assertEquals(204, respuesta.statusCode(), respuesta.body());
    }

    @Test
    void desactivarSobreUnaSubastaInexistenteDevuelve404() throws Exception {
        UUID jugador = jugadorConSaldo("1000");

        HttpResponse<String> respuesta = enviar("DELETE",
                "/subastas/" + UUID.randomUUID() + "/puja-automatica", null, tokenDe(jugador), null);

        assertEquals(404, respuesta.statusCode(), respuesta.body());
    }
}
