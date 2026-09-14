package com.nexusbattles.ms_subastas.subastas.port;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @BeforeEach
    void levantar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/api/v1/inventario", intercambio -> {
            metodo.set(intercambio.getRequestMethod());
            ruta.set(intercambio.getRequestURI().getPath());
            identidad.set(intercambio.getRequestHeaders().getFirst("X-User-Name"));
            claveIdempotencia.set(intercambio.getRequestHeaders().getFirst("Idempotency-Key"));
            cuerpo.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] respuesta = "{}".getBytes(StandardCharsets.UTF_8);
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

    // --- la unica operacion que existe al otro lado ------------------------

    @Test
    void bloquearUsaElVerboLaRutaYElCuerpoQueDeclaraElContrato() throws Exception {
        UUID propietario = UUID.fromString("77777777-0000-0000-0000-0000000000cc");
        UUID subasta = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

        cliente.reservar(ELEMENTO, propietario, subasta, "clave-de-prueba");

        assertEquals("PUT", metodo.get());
        assertEquals("/api/v1/inventario/elementos/" + ELEMENTO + "/bloqueo-subasta", ruta.get());
        assertEquals(propietario.toString(), identidad.get());
        assertEquals("clave-de-prueba", claveIdempotencia.get());

        JsonNode json = new ObjectMapper().readTree(cuerpo.get());
        assertEquals(subasta.toString(), json.get("subastaId").asText());
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
     * El 403 es el sintoma del desacuerdo de identificadores entre los dos
     * servicios, y el mensaje tiene que decirlo: si solo dijera "403", el
     * siguiente que lo vea perdera una tarde averiguando por que.
     */
    @Test
    void unRechazoPorIdentidadExplicaElDesacuerdoDeIdentificadores() {
        codigo.set(403);

        InventarioClientException error = assertThrows(InventarioClientException.class,
                () -> cliente.reservar(ELEMENTO, UUID.randomUUID(), UUID.randomUUID(), "clave"));

        assertTrue(error.getMessage().contains("X-User-Name"), error.getMessage());
        assertTrue(error.getMessage().contains("apodo"), error.getMessage());
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

    // --- las tres que no existen -------------------------------------------

    /**
     * Antes llamaban a rutas inventadas y habrian muerto con un 404 en mitad de
     * una subasta. Ahora fallan aqui, y el mensaje nombra el endpoint que hay
     * que pedir. Estas tres pruebas se borran el dia que los endpoints existan.
     */
    @Test
    void buscarPorIdDiceQueEsEndpointNoExisteYNoInventaUnaRuta() {
        InventarioClientException error = assertThrows(InventarioClientException.class,
                () -> cliente.buscar(ELEMENTO));

        assertTrue(error.getMessage().contains("GET /api/v1/inventario/elementos/{elementoId}"),
                error.getMessage());
        assertEquals(null, metodo.get(), "no puede salir ninguna peticion hacia una ruta que no existe");
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

    @Test
    void transferirDiceQueEsEndpointNoExiste() {
        InventarioClientException error = assertThrows(InventarioClientException.class,
                () -> cliente.transferirProducto(ELEMENTO, UUID.randomUUID(), UUID.randomUUID(), "clave"));

        assertTrue(error.getMessage().contains("transferencia"), error.getMessage());
        assertEquals(null, metodo.get());
    }
}
