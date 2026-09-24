package com.nexusbattles.ms_subastas.subastas.port;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Contra un servidor HTTP real del JDK, no un mock del cliente: lo que hay que
 * verificar es la peticion que sale por el cable —verbo, ruta, cabeceras y
 * cuerpo— frente al contrato de inventario, y eso un mock no lo comprueba.
 */
class InventarioClientHttpTest {

    private static final String ELEMENTO = "elem-hacha-01";

    private HttpServer servidor;
    private InventarioClientHttp cliente;
    private final AtomicReference<String> metodo = new AtomicReference<>();
    private final AtomicReference<String> ruta = new AtomicReference<>();
    private final AtomicReference<String> identidad = new AtomicReference<>();
    private final AtomicReference<String> claveIdempotencia = new AtomicReference<>();
    private final AtomicReference<String> cuerpo = new AtomicReference<>();
    private final AtomicInteger codigo = new AtomicInteger(200);
    private final AtomicReference<String> cuerpoDeRespuesta = new AtomicReference<>("{}");

    @BeforeEach
    void levantar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/api/v1/inventario", intercambio -> {
            metodo.set(intercambio.getRequestMethod());
            ruta.set(intercambio.getRequestURI().getPath());
            identidad.set(intercambio.getRequestHeaders().getFirst("X-User-Name"));
            claveIdempotencia.set(intercambio.getRequestHeaders().getFirst("Idempotency-Key"));
            cuerpo.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] respuesta = cuerpoDeRespuesta.get().getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(codigo.get(), respuesta.length);
            intercambio.getResponseBody().write(respuesta);
            intercambio.close();
        });
        servidor.start();
        cliente = new InventarioClientHttp(
                URI.create("http://localhost:" + servidor.getAddress().getPort()),
                HttpClient.newHttpClient(), new ObjectMapper(), Duration.ofSeconds(2));
    }

    @AfterEach
    void bajar() {
        servidor.stop(0);
    }

    // --- la operacion de bloqueo/reserva acordada --------------------------

    @Test
    void bloquearUsaElVerboLaRutaYElCuerpoQueDeclaraElContrato() throws Exception {
        UUID propietario = UUID.fromString("77777777-0000-0000-0000-0000000000cc");
        UUID subasta = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

        cliente.reservar(ELEMENTO, propietario, subasta, "clave-de-prueba");

        assertEquals("PUT", metodo.get());
        assertEquals("/api/v1/inventario/elementos/" + ELEMENTO + "/bloqueo-subasta", ruta.get());
        assertEquals("clave-de-prueba", claveIdempotencia.get());

        JsonNode json = new ObjectMapper().readTree(cuerpo.get());
        assertTrue(json.has("propietarioUid"), "El JSON de reserva debe contener propietarioUid");
        assertTrue(json.has("subastaId"), "El JSON de reserva debe contener subastaId");
        assertFalse(json.has("propietarioId"), "No debe usar propietarioId");
        assertEquals(subasta.toString(), json.get("subastaId").asText());
        // El propietario viaja en el CUERPO como propietarioUid, que es lo que
        // publico inventario el 15/09. Antes se mandaba el UUID en X-User-Name,
        // una cabecera que compara contra el apodo: no coincidia nunca.
        assertEquals(propietario.toString(), json.get("propietarioUid").asText());
        assertEquals(null, identidad.get(), "ya no se manda cabecera de apodo");
    }

    /**
     * Inventario exige la clave: sin ella responde 400. Cortarlo aqui ahorra el
     * viaje y da un mensaje que se entiende.
     */
    @Test
    void bloquearSinClaveDeIdempotenciaNiSaleDeCasa() {
        assertThrows(InventarioClientException.class,
                () -> cliente.reservar(ELEMENTO, UUID.randomUUID(), UUID.randomUUID(), "  "));
        assertEquals(null, metodo.get());
    }

    /**
     * Ya no es el sintoma del desacuerdo de identificadores —eso se cerro al
     * publicarse propietarioUid—: ahora un 403 significa lo que dice, que quien
     * intenta bloquear no es el dueno del elemento.
     */
    @Test
    void unRechazoPorPropietarioAjenoSeExplicaComoTal() {
        codigo.set(403);

        InventarioClientException error = assertThrows(InventarioClientException.class,
                () -> cliente.reservar(ELEMENTO, UUID.randomUUID(), UUID.randomUUID(), "clave"));

        assertTrue(error.getMessage().contains("propietarioUid"), error.getMessage());
    }

    @Test
    void unElementoYaBloqueadoOEquipadoSeDistingueDeUnFalloCualquiera() {
        codigo.set(409);

        InventarioClientException error = assertThrows(InventarioClientException.class,
                () -> cliente.reservar(ELEMENTO, UUID.randomUUID(), UUID.randomUUID(), "clave"));

        assertTrue(error.getMessage().contains("equipado") || error.getMessage().contains("bloqueado"),
                error.getMessage());
    }

    @Test
    void siInventarioNoRespondeSeReportaComoFallo() {
        InventarioClientHttp haciaLaNada = new InventarioClientHttp(
                URI.create("http://localhost:1"), HttpClient.newHttpClient(),
                new ObjectMapper(), Duration.ofMillis(300));

        assertThrows(InventarioNoDisponibleException.class,
                () -> haciaLaNada.reservar(ELEMENTO, UUID.randomUUID(), UUID.randomUUID(), "clave"));
    }

    // --- consultar un elemento, que Nicolay publico el 15/09 ---------------

    @Test
    void buscarUsaLaRutaDelElementoYLeeSusCampos() {
        UUID producto = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
        UUID propietario = UUID.fromString("77777777-0000-0000-0000-0000000000cc");
        cuerpoDeRespuesta.set("""
                {"elementoId":"%s","productoId":"%s","propietarioUid":"%s",
                 "enUso":false,"disponible":true,"subastaId":null}"""
                .formatted(ELEMENTO, producto, propietario));

        InventarioClient.ElementoInventario elemento = cliente.buscar(ELEMENTO).orElseThrow();

        assertEquals("GET", metodo.get());
        assertEquals("/api/v1/inventario/elementos/" + ELEMENTO, ruta.get());
        assertEquals(ELEMENTO, elemento.id());
        assertEquals(producto, elemento.productoId());
        assertEquals(propietario, elemento.propietarioId());
        assertFalse(elemento.enUso());
    }

    /**
     * La consulta no pide identidad, y eso es lo que la hace utilizable desde
     * el job de cierre, donde no hay peticion de nadie.
     */
    @Test
    void buscarNoMandaIdentidadDeNadie() {
        cuerpoDeRespuesta.set("""
                {"elementoId":"%s","productoId":"bbbbbbbb-0000-0000-0000-000000000002",
                 "propietarioUid":"77777777-0000-0000-0000-0000000000cc","enUso":false,
                 "disponible":true,"subastaId":null}""".formatted(ELEMENTO));

        cliente.buscar(ELEMENTO);

        assertEquals(null, identidad.get());
    }

    /**
     * Que el elemento ya no exista es un estado normal —lo pudieron borrar
     * entre publicar la subasta y cerrarla—, no un fallo. Devolver vacio deja
     * decidir a quien llama; una excepcion lo obligaria a cazarla para nada.
     */
    @Test
    void unElementoQueYaNoExisteDevuelveVacioYNoExplota() {
        codigo.set(404);

        assertTrue(cliente.buscar(ELEMENTO).isEmpty());
    }

    @Test
    void unaRespuestaIlegibleAlConsultarNoPasaPorBuena() {
        cuerpoDeRespuesta.set("esto no es json");

        assertThrows(InventarioClientException.class, () -> cliente.buscar(ELEMENTO));
    }

    @Test
    void siInventarioNoRespondeAlConsultarSeReportaComoAveria() {
        InventarioClientHttp haciaLaNada = new InventarioClientHttp(
                URI.create("http://localhost:1"), HttpClient.newHttpClient(),
                new ObjectMapper(), Duration.ofMillis(300));

        assertThrows(InventarioNoDisponibleException.class, () -> haciaLaNada.buscar(ELEMENTO));
    }

    // --- liberar el bloqueo, que Nicolay publico despues -------------------

    @Test
    void liberarUsaElVerboYLaRutaConSubastaIdQueDeclaraElContrato() {
        UUID subasta = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

        cliente.liberarReserva(ELEMENTO, subasta, "clave-de-cierre");

        assertEquals("DELETE", metodo.get());
        assertEquals("/api/v1/inventario/elementos/" + ELEMENTO + "/bloqueo-subasta/" + subasta, ruta.get());
        assertEquals("clave-de-cierre", claveIdempotencia.get());
    }

    /**
     * El cierre por vencimiento lo dispara un job programado: no hay peticion
     * HTTP ni token de nadie, porque lo inicio el reloj. Que inventario no pida
     * identidad en esta operacion es justamente lo que hace viable ese camino.
     */
    @Test
    void liberarNoNecesitaIdentidadPorqueLoLlamaUnJobSinToken() {
        cliente.liberarReserva(ELEMENTO, UUID.randomUUID(), "clave-de-cierre");

        assertEquals(null, identidad.get());
    }

    /**
     * 409 aqui significa que el elemento esta bloqueado por OTRA subasta.
     * Inventario lo conserva bloqueado a proposito, y hace bien: soltarlo seria
     * quitarle el producto a una subasta viva.
     */
    @Test
    void liberarUnBloqueoDeOtraSubastaSeRechazaSinReintentar() {
        codigo.set(409);
        UUID subasta = UUID.randomUUID();

        InventarioClientException error = assertThrows(InventarioClientException.class,
                () -> cliente.liberarReserva(ELEMENTO, subasta, "clave"));

        assertFalse(error instanceof InventarioNoDisponibleException,
                "es un rechazo de negocio: reintentarlo da lo mismo y no debe abrir el cortacircuitos");
        assertTrue(error.getMessage().contains("otra subasta"), error.getMessage());
    }

    /**
     * Si el elemento ya no existe, no queda nada que desbloquear: el efecto
     * buscado ya se cumple. Y tratarlo como error seria peor que inutil — el
     * cierre corre dentro de una transaccion, asi que al fallar revierte, la
     * subasta se queda ACTIVA y el job la reintenta cada 30 s para siempre sin
     * que ninguna vuelta pueda salir bien.
     */
    @Test
    void liberarUnElementoQueYaNoExisteNoImpideCerrarLaSubasta() {
        codigo.set(404);

        assertDoesNotThrow(() -> cliente.liberarReserva(ELEMENTO, UUID.randomUUID(), "clave"));
    }

    // --- disponibilidad frente a negocio -----------------------------------

    /**
     * La distincion que sostiene el cortacircuitos. Un 503 es una averia y debe
     * contar como fallo; un 409 es una respuesta correcta y no.
     */
    @Test
    void un503SeDistingueDeUnRechazoDeNegocio() {
        codigo.set(503);

        assertThrows(InventarioNoDisponibleException.class,
                () -> cliente.liberarReserva(ELEMENTO, UUID.randomUUID(), "clave"));
    }

    @Test
    void unRechazoDeNegocioNoCuentaComoAveria() {
        codigo.set(409);

        InventarioClientException error = assertThrows(InventarioClientException.class,
                () -> cliente.reservar(ELEMENTO, UUID.randomUUID(), UUID.randomUUID(), "clave"));

        assertFalse(error instanceof InventarioNoDisponibleException, error.getMessage());
    }

    // --- transferir un elemento al ganador (HU-SUB-004) --------------------

    @Test
    void transferirUsaElVerboLaRutaYElCuerpoQueDeclaraElContrato() throws Exception {
        UUID nuevoPropietario = UUID.fromString("88888888-0000-0000-0000-0000000000dd");
        UUID subasta = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

        cliente.transferirProducto(ELEMENTO, nuevoPropietario, subasta, "clave-transferencia");

        assertEquals("POST", metodo.get());
        assertEquals("/api/v1/inventario/elementos/" + ELEMENTO + "/transferencias", ruta.get());
        assertEquals("clave-transferencia", claveIdempotencia.get());

        JsonNode json = new ObjectMapper().readTree(cuerpo.get());
        assertTrue(json.has("nuevoPropietarioUid"), "El JSON de transferencia debe contener nuevoPropietarioUid");
        assertTrue(json.has("subastaId"), "El JSON de transferencia debe contener subastaId");
        assertFalse(json.has("nuevoPropietarioId"), "No debe usar nuevoPropietarioId");
        assertFalse(json.has("propietarioUid"), "No debe usar propietarioUid en transferencias");
        assertEquals(subasta.toString(), json.get("subastaId").asText());
        assertEquals(nuevoPropietario.toString(), json.get("nuevoPropietarioUid").asText());
        assertEquals(null, identidad.get(), "no depende de X-User-Name");
    }

    @Test
    void transferirExigeElementoNuevoPropietarioYSubastaAntesDeSalir() {
        assertThrows(InventarioClientException.class,
                () -> cliente.transferirProducto(null, UUID.randomUUID(), UUID.randomUUID(), "clave"));
        assertThrows(InventarioClientException.class,
                () -> cliente.transferirProducto("  ", UUID.randomUUID(), UUID.randomUUID(), "clave"));
        assertThrows(InventarioClientException.class,
                () -> cliente.transferirProducto(ELEMENTO, null, UUID.randomUUID(), "clave"));
        assertThrows(InventarioClientException.class,
                () -> cliente.transferirProducto(ELEMENTO, UUID.randomUUID(), null, "clave"));

        assertEquals(null, metodo.get(), "ninguna puede haber salido a la red");
    }

    /**
     * Este caso comprobaba que sin clave la cabecera no viajaba. Cambia en
     * FI-TRANSFER-1: el contrato declara {@code Idempotency-Key} obligatoria y
     * ahora el controlador la exige de verdad, asi que una llamada sin cabecera
     * se llevaria un 400 en vez de transferir. La clave no se inventa — se
     * deriva del identificador de la subasta, que es la misma que usa el cierre
     * por vencimiento, y por tanto sigue siendo estable entre reintentos.
     */
    @Test
    void transferirSinClaveLaDerivaDeLaSubasta() {
        UUID subasta = UUID.randomUUID();

        cliente.transferirProducto(ELEMENTO, UUID.randomUUID(), subasta, null);

        assertEquals("POST", metodo.get());
        assertEquals("cierre-" + subasta, claveIdempotencia.get());
    }

    @Test
    void transferirConClavePropiaLaRespeta() {
        cliente.transferirProducto(ELEMENTO, UUID.randomUUID(), UUID.randomUUID(), "compensar-7");

        assertEquals("compensar-7", claveIdempotencia.get(),
                "la clave de la llamada manda: es la que comparte con la reserva de credito");
    }

    @Test
    void un503AlTransferirEsAveriaYNoRechazoDeNegocio() {
        codigo.set(503);

        assertThrows(InventarioNoDisponibleException.class,
                () -> cliente.transferirProducto(ELEMENTO, UUID.randomUUID(), UUID.randomUUID(), "clave"));
    }

    @Test
    void unaRespuestaInesperadaAlTransferirNoPasaPorBuena() {
        codigo.set(500);

        InventarioClientException error = assertThrows(InventarioClientException.class,
                () -> cliente.transferirProducto(ELEMENTO, UUID.randomUUID(), UUID.randomUUID(), "clave"));

        assertFalse(error instanceof InventarioNoDisponibleException);
        assertTrue(error.getMessage().contains("500"), error.getMessage());
    }

    @Test
    void siInventarioNoRespondeAlTransferirSeReportaComoFallo() {
        InventarioClientHttp haciaLaNada = new InventarioClientHttp(
                URI.create("http://localhost:1"), HttpClient.newHttpClient(),
                new ObjectMapper(), Duration.ofMillis(300));

        assertThrows(InventarioNoDisponibleException.class,
                () -> haciaLaNada.transferirProducto(ELEMENTO, UUID.randomUUID(), UUID.randomUUID(), "clave"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void capturarPeticionesVerificaCuerpoYCabecerasConArgumentCaptor() throws Exception {
        HttpClient mockHttpClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        InventarioClientHttp clienteConMock = new InventarioClientHttp(
                URI.create("http://localhost:8080"),
                mockHttpClient, new ObjectMapper(), Duration.ofSeconds(2));

        UUID nuevoPropietario = UUID.fromString("88888888-0000-0000-0000-0000000000dd");
        UUID subasta = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
        clienteConMock.transferirProducto(ELEMENTO, nuevoPropietario, subasta, "clave-captor");

        UUID propietario = UUID.fromString("77777777-0000-0000-0000-0000000000cc");
        clienteConMock.reservar(ELEMENTO, propietario, subasta, "clave-bloqueo");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient, times(2)).send(captor.capture(), any());

        List<HttpRequest> peticiones = captor.getAllValues();

        HttpRequest peticionTransferencia = peticiones.get(0);
        assertEquals("POST", peticionTransferencia.method());
        assertEquals("http://localhost:8080/api/v1/inventario/elementos/" + ELEMENTO + "/transferencias",
                peticionTransferencia.uri().toString());
        assertEquals("clave-captor", peticionTransferencia.headers().firstValue("Idempotency-Key").orElse(null));
        assertEquals("application/json", peticionTransferencia.headers().firstValue("Content-Type").orElse(null));

        JsonNode jsonTransferencia = new ObjectMapper().readTree(extraerCuerpo(peticionTransferencia));
        assertTrue(jsonTransferencia.has("nuevoPropietarioUid"), "El JSON capturado de transferencia debe contener nuevoPropietarioUid");
        assertTrue(jsonTransferencia.has("subastaId"), "El JSON capturado de transferencia debe contener subastaId");
        assertFalse(jsonTransferencia.has("nuevoPropietarioId"), "No debe contener nuevoPropietarioId");
        assertFalse(jsonTransferencia.has("propietarioUid"), "No debe contener propietarioUid en transferencias");
        assertEquals(nuevoPropietario.toString(), jsonTransferencia.get("nuevoPropietarioUid").asText());
        assertEquals(subasta.toString(), jsonTransferencia.get("subastaId").asText());

        HttpRequest peticionReserva = peticiones.get(1);
        assertEquals("PUT", peticionReserva.method());
        assertEquals("http://localhost:8080/api/v1/inventario/elementos/" + ELEMENTO + "/bloqueo-subasta",
                peticionReserva.uri().toString());
        assertEquals("clave-bloqueo", peticionReserva.headers().firstValue("Idempotency-Key").orElse(null));
        assertEquals("application/json", peticionReserva.headers().firstValue("Content-Type").orElse(null));

        JsonNode jsonReserva = new ObjectMapper().readTree(extraerCuerpo(peticionReserva));
        assertTrue(jsonReserva.has("propietarioUid"), "El JSON capturado de reserva debe contener propietarioUid");
        assertTrue(jsonReserva.has("subastaId"), "El JSON capturado de reserva debe contener subastaId");
        assertFalse(jsonReserva.has("propietarioId"), "No debe contener propietarioId");
        assertFalse(jsonReserva.has("nuevoPropietarioUid"), "No debe contener nuevoPropietarioUid en reservas");
        assertEquals(propietario.toString(), jsonReserva.get("propietarioUid").asText());
        assertEquals(subasta.toString(), jsonReserva.get("subastaId").asText());
    }

    private static String extraerCuerpo(HttpRequest peticion) {
        assertTrue(peticion.bodyPublisher().isPresent(), "La peticion debe tener un BodyPublisher");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        peticion.bodyPublisher().get().subscribe(new Flow.Subscriber<ByteBuffer>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] buf = new byte[item.remaining()];
                item.get(buf);
                baos.write(buf, 0, buf.length);
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        });
        return baos.toString(StandardCharsets.UTF_8);
    }

    // --- lo que se corta antes de salir a la red ---------------------------
    //
    // Estas llamadas deciden de quien es un producto. Una peticion mal armada
    // que llegue a inventario o la rechaza con un 400 que hay que interpretar,
    // o acaba bloqueando/soltando el elemento equivocado.

    @Test
    void bloquearExigeElementoPropietarioYSubastaAntesDeSalir() {
        assertThrows(InventarioClientException.class,
                () -> cliente.reservar(null, UUID.randomUUID(), UUID.randomUUID(), "clave"));
        assertThrows(InventarioClientException.class,
                () -> cliente.reservar("  ", UUID.randomUUID(), UUID.randomUUID(), "clave"));
        assertThrows(InventarioClientException.class,
                () -> cliente.reservar(ELEMENTO, null, UUID.randomUUID(), "clave"));
        assertThrows(InventarioClientException.class,
                () -> cliente.reservar(ELEMENTO, UUID.randomUUID(), null, "clave"));

        assertEquals(null, metodo.get(), "ninguna puede haber salido a la red");
    }

    @Test
    void liberarExigeElementoYSubasta() {
        assertThrows(InventarioClientException.class,
                () -> cliente.liberarReserva(null, UUID.randomUUID(), "clave"));
        assertThrows(InventarioClientException.class,
                () -> cliente.liberarReserva(ELEMENTO, null, "clave"));
        assertEquals(null, metodo.get());
    }

    @Test
    void buscarExigeElIdentificadorDelElemento() {
        assertThrows(InventarioClientException.class, () -> cliente.buscar(null));
        assertThrows(InventarioClientException.class, () -> cliente.buscar("  "));
        assertEquals(null, metodo.get());
    }

    /**
     * A diferencia del bloqueo, liberar admite ir sin clave: lo llama el job de
     * cierre, e inventario garantiza la idempotencia por su lado (repetir el
     * aviso sobre un producto ya disponible responde 200).
     */
    @Test
    void liberarSinClaveDeIdempotenciaSiSale() {
        cliente.liberarReserva(ELEMENTO, UUID.randomUUID(), null);

        assertEquals("DELETE", metodo.get());
        assertEquals(null, claveIdempotencia.get());
    }

    // --- averia contra rechazo de negocio ----------------------------------

    /**
     * El 503 es lo unico que merece reintento y lo unico que debe empujar el
     * cortacircuitos. Un 409 es una respuesta correcta: reintentarlo da igual.
     */
    @Test
    void un503AlBloquearEsAveriaYNoRechazoDeNegocio() {
        codigo.set(503);

        assertThrows(InventarioNoDisponibleException.class,
                () -> cliente.reservar(ELEMENTO, UUID.randomUUID(), UUID.randomUUID(), "clave"));
    }

    @Test
    void un503AlConsultarEsAveria() {
        codigo.set(503);

        assertThrows(InventarioNoDisponibleException.class, () -> cliente.buscar(ELEMENTO));
    }

    @Test
    void unaRespuestaInesperadaAlBloquearNoPasaPorBuena() {
        codigo.set(418);

        InventarioClientException error = assertThrows(InventarioClientException.class,
                () -> cliente.reservar(ELEMENTO, UUID.randomUUID(), UUID.randomUUID(), "clave"));

        assertFalse(error instanceof InventarioNoDisponibleException);
        assertTrue(error.getMessage().contains("418"), error.getMessage());
    }

    @Test
    void unaRespuestaInesperadaAlConsultarNoPasaPorBuena() {
        codigo.set(500);

        assertThrows(InventarioClientException.class, () -> cliente.buscar(ELEMENTO));
    }

    /**
     * Si inventario no devuelve el elementoId, vale el que se pidio: es el que
     * identifica al elemento que se acaba de consultar. Dejarlo nulo dejaria la
     * subasta apuntando a un producto sin identificador.
     */
    @Test
    void siNoVieneElElementoIdValeElQueSePidio() {
        cuerpoDeRespuesta.set("""
                {"productoId":"bbbbbbbb-0000-0000-0000-000000000002",
                 "propietarioUid":"77777777-0000-0000-0000-0000000000cc",
                 "enUso":false,"disponible":true,"subastaId":null}""");

        assertEquals(ELEMENTO, cliente.buscar(ELEMENTO).orElseThrow().id());
    }

    @Test
    void serializacionDirectaSolicitudesContratosExactos() throws Exception {
        UUID u1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID u2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

        ObjectMapper mapper = new ObjectMapper();

        String jsonTransferencia = mapper.writeValueAsString(
                new InventarioClientHttp.SolicitudTransferencia(u1, u2));
        JsonNode nodeTransferencia = mapper.readTree(jsonTransferencia);
        assertTrue(nodeTransferencia.has("nuevoPropietarioUid"), "Debe contener nuevoPropietarioUid");
        assertTrue(nodeTransferencia.has("subastaId"), "Debe contener subastaId");
        assertFalse(nodeTransferencia.has("nuevoPropietarioId"), "No debe contener nuevoPropietarioId");
        assertFalse(nodeTransferencia.has("propietarioUid"), "No debe contener propietarioUid");
        assertEquals(u1.toString(), nodeTransferencia.get("nuevoPropietarioUid").asText());
        assertEquals(u2.toString(), nodeTransferencia.get("subastaId").asText());

        String jsonReserva = mapper.writeValueAsString(
                new InventarioClientHttp.SolicitudReserva(u1, u2));
        JsonNode nodeReserva = mapper.readTree(jsonReserva);
        assertTrue(nodeReserva.has("propietarioUid"), "Debe contener propietarioUid");
        assertTrue(nodeReserva.has("subastaId"), "Debe contener subastaId");
        assertFalse(nodeReserva.has("propietarioId"), "No debe contener propietarioId");
        assertFalse(nodeReserva.has("nuevoPropietarioUid"), "No debe contener nuevoPropietarioUid");
        assertEquals(u1.toString(), nodeReserva.get("propietarioUid").asText());
        assertEquals(u2.toString(), nodeReserva.get("subastaId").asText());
    }

    @Test
    void un404AlTransferirNoEsUnaAveria() {
        codigo.set(404);

        InventarioClientException error = assertThrows(InventarioClientException.class,
                () -> cliente.transferirProducto(ELEMENTO, UUID.randomUUID(), UUID.randomUUID(), "clave"));

        assertFalse(error instanceof InventarioNoDisponibleException,
                "un 404 no se reintenta ni debe abrir el cortacircuitos");
    }

    @Test
    void un409AlTransferirEsRechazoPorConflicto() {
        codigo.set(409);

        InventarioClientException error = assertThrows(InventarioClientException.class,
                () -> cliente.transferirProducto(ELEMENTO, UUID.randomUUID(), UUID.randomUUID(), "clave"));

        assertFalse(error instanceof InventarioNoDisponibleException);
        assertTrue(error.getMessage().contains("conflicto"), error.getMessage());
    }

    @Test
    void errorDeSerializacionAlTransferirOAlBloquearLanzaInventarioClientException() throws Exception {
        ObjectMapper mockMapper = mock(ObjectMapper.class);
        when(mockMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("fallo") {});

        InventarioClientHttp clienteFalloSerializacion = new InventarioClientHttp(
                URI.create("http://localhost:8080"),
                HttpClient.newHttpClient(), mockMapper, Duration.ofSeconds(2));

        assertThrows(InventarioClientException.class,
                () -> clienteFalloSerializacion.transferirProducto(ELEMENTO, UUID.randomUUID(), UUID.randomUUID(), "k"));
        assertThrows(InventarioClientException.class,
                () -> clienteFalloSerializacion.reservar(ELEMENTO, UUID.randomUUID(), UUID.randomUUID(), "k"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void siElEnvioEsInterrumpidoSeLanzaInventarioNoDisponible() throws Exception {
        HttpClient mockHttpClient = mock(HttpClient.class);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("interrumpido"));

        InventarioClientHttp clienteInterrumpido = new InventarioClientHttp(
                URI.create("http://localhost:8080"),
                mockHttpClient, new ObjectMapper(), Duration.ofSeconds(2));

        assertThrows(InventarioNoDisponibleException.class,
                () -> clienteInterrumpido.transferirProducto(ELEMENTO, UUID.randomUUID(), UUID.randomUUID(), "k"));
        assertTrue(Thread.interrupted(), "debe restaurar el flag de interrupcion");
    }

    @Test
    void liberarRespuestaInesperadaLanzaInventarioClientException() {
        codigo.set(500);

        InventarioClientException error = assertThrows(InventarioClientException.class,
                () -> cliente.liberarReserva(ELEMENTO, UUID.randomUUID(), "clave"));

        assertFalse(error instanceof InventarioNoDisponibleException);
        assertTrue(error.getMessage().contains("500"), error.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void baseUriConRutasOBarraFinalSeNormalizaCorrectamente() throws Exception {
        HttpClient mockHttpClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        InventarioClientHttp clienteConContexto = new InventarioClientHttp(
                URI.create("http://localhost:8080/api-base/"),
                mockHttpClient, new ObjectMapper(), Duration.ofSeconds(2));

        clienteConContexto.transferirProducto(ELEMENTO, UUID.randomUUID(), UUID.randomUUID(), "k");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any());
        assertEquals("http://localhost:8080/api-base/api/v1/inventario/elementos/" + ELEMENTO + "/transferencias",
                captor.getValue().uri().toString());
    }

    /**
     * Este caso exigia que el mensaje nombrase dos lecturas posibles del 404,
     * porque la segunda — «ms-inventario todavia no publica esa ruta» — era
     * entonces la mas probable. Deja de serlo en FI-TRANSFER-1: la ruta existe
     * (#670) y esta declarada en {@code contracts/openapi/inventario.yaml}
     * 1.2.0, asi que un 404 aqui solo puede querer decir una cosa, y decir la
     * otra mandaria a quien depure a mirar el contrato en vez del inventario.
     *
     * <p>Lo que se sigue exigiendo es lo util: que el mensaje nombre el
     * elemento concreto y diga que no esta en ningun inventario.
     */
    @Test
    void un404AlTransferirSenalaAlElemento() {
        codigo.set(404);

        InventarioClientException error = assertThrows(InventarioClientException.class,
                () -> cliente.transferirProducto(ELEMENTO, UUID.randomUUID(), UUID.randomUUID(), "clave"));

        assertTrue(error.getMessage().contains(ELEMENTO), error.getMessage());
        assertTrue(error.getMessage().contains("no existe"), error.getMessage());
        assertFalse(error.getMessage().contains("aun no expone"),
                "la ruta ya existe: culpar al contrato manda a depurar al sitio equivocado");
    }
}
