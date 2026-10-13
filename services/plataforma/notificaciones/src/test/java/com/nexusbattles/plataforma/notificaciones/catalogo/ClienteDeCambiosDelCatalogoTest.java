package com.nexusbattles.plataforma.notificaciones.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import com.nexusbattles.comun.observabilidad.FiltroDeTraza;
import com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio;
import com.nexusbattles.plataforma.observabilidad.InterceptorDeTraza;
import com.sun.net.httpserver.HttpServer;

/**
 * HU-NOT-001 — el cliente de {@code GET /api/v1/productos/alertas/cambios}
 * (productos.yaml 1.6.0) tal como lo arma {@link ConfiguracionDelCatalogo},
 * contra un servidor HTTP de verdad (el del JDK) que hace de productos.
 *
 * <p>Se comprueba lo que un doble en memoria no puede: la ruta y los
 * parametros que viajan, la credencial de servicio y la traza en las
 * cabeceras, que el tiempo de lectura corta a un productos colgado SIN
 * reintentar (la llamada ocurre dentro de una peticion interactiva) y que
 * cualquier respuesta que no sea un lote valido es «no disponible».
 */
@DisplayName("Notificaciones · cliente de los cambios del catalogo")
class ClienteDeCambiosDelCatalogoTest {

    private HttpServer servidor;
    private final List<String> rutas = new CopyOnWriteArrayList<>();
    private final List<String> consultas = new CopyOnWriteArrayList<>();
    private final List<String> autorizaciones = new CopyOnWriteArrayList<>();
    private final List<String> trazas = new CopyOnWriteArrayList<>();
    private volatile int estado = 200;
    private volatile String cuerpo = "{}";
    private volatile long esperaMs;

    @BeforeEach
    void levantar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            rutas.add(intercambio.getRequestURI().getPath());
            consultas.add(String.valueOf(intercambio.getRequestURI().getQuery()));
            autorizaciones.add(String.valueOf(intercambio.getRequestHeaders().getFirst("Authorization")));
            trazas.add(String.valueOf(intercambio.getRequestHeaders().getFirst(FiltroDeTraza.CABECERA)));
            try {
                Thread.sleep(esperaMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] respuesta = cuerpo.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(estado, respuesta.length);
            intercambio.getResponseBody().write(respuesta);
            intercambio.close();
        });
        servidor.start();
    }

    @AfterEach
    void apagar() {
        servidor.stop(0);
        MDC.clear();
    }

    private ClienteDeCambiosDelCatalogo cliente(int lecturaMs) {
        return ConfiguracionDelCatalogo.cliente(
                "http://127.0.0.1:" + servidor.getAddress().getPort(),
                500, lecturaMs, 50,
                new InterceptorDeTraza(),
                new InterceptorDePortadorDeServicio(() -> "token-de-notificaciones"));
    }

    @Test
    @DisplayName("sin desde pide la linea base a la ruta del contrato, con la credencial de servicio")
    void lineaBase() {
        cuerpo = """
                {"hasta":"2026-10-05T15:00:00.123Z","completo":true,"alertas":[]}
                """;

        LoteDeCambios lote = cliente(1_000).consultar(null);

        assertEquals(Instant.parse("2026-10-05T15:00:00.123Z"), lote.hasta());
        assertTrue(lote.completo());
        assertEquals(List.of(), lote.alertas());
        assertEquals(List.of("/api/v1/productos/alertas/cambios"), rutas);
        assertEquals(List.of("limite=50"), consultas);
        assertEquals(List.of("Bearer token-de-notificaciones"), autorizaciones);
    }

    @Test
    @DisplayName("con desde lo envia tal cual y lee cada cambio con su descripcion y su fecha")
    void conDesde() {
        cuerpo = """
                {"hasta":"2026-10-05T14:30:00Z","completo":false,"alertas":[
                  {"id":"alerta-1","productoId":"producto-1","productoNombre":"Espada solar",
                   "tipo":"CAMBIO_BALANCE","descripcion":"Se actualizo el balance de Espada solar.",
                   "implementadaEn":"2026-10-05T14:30:00Z","campoNuevo":"se ignora"}]}
                """;

        LoteDeCambios lote = cliente(1_000).consultar(Instant.parse("2026-10-01T12:00:00Z"));

        assertEquals(List.of("desde=2026-10-01T12:00:00Z&limite=50"), consultas);
        assertFalse(lote.completo());
        assertEquals(Instant.parse("2026-10-05T14:30:00Z"), lote.hasta());
        CambioDelCatalogo cambio = lote.alertas().getFirst();
        assertEquals("alerta-1", cambio.id());
        assertEquals("CAMBIO_BALANCE", cambio.tipo());
        assertEquals("Se actualizo el balance de Espada solar.", cambio.descripcion());
        assertEquals(Instant.parse("2026-10-05T14:30:00Z"), cambio.implementadaEn());
    }

    @Test
    @DisplayName("la traza de la peticion en curso viaja en traceparent (regla 5)")
    void propagaLaTraza() {
        cuerpo = "{\"hasta\":\"2026-10-05T15:00:00Z\",\"completo\":true,\"alertas\":[]}";
        MDC.put(FiltroDeTraza.CLAVE_MDC, "4bf92f3577b34da6a3ce929d0e0e4736");

        cliente(1_000).consultar(null);

        assertTrue(trazas.getFirst().startsWith("00-4bf92f3577b34da6a3ce929d0e0e4736-"), trazas.getFirst());
    }

    @Test
    @DisplayName("un 503 o un 403 de productos es «no disponible», nunca un lote vacio inventado")
    void erroresDeProductos() {
        estado = 503;
        cuerpo = "{\"type\":\"urn:nexus:problema:error-interno\"}";
        assertThrows(CatalogoNoDisponible.class, () -> cliente(1_000).consultar(null));

        estado = 403;
        assertThrows(CatalogoNoDisponible.class, () -> cliente(1_000).consultar(null));
    }

    @Test
    @DisplayName("una respuesta sin hasta o que no es JSON es «no disponible»")
    void respuestaQueNoSeEntiende() {
        cuerpo = "{\"completo\":true,\"alertas\":[]}";
        assertThrows(CatalogoNoDisponible.class, () -> cliente(1_000).consultar(null));

        cuerpo = "{\"hasta\":\"2026-10-05T15:00:00Z\",\"completo\":true}";
        assertThrows(CatalogoNoDisponible.class, () -> cliente(1_000).consultar(null));

        cuerpo = "<html>borde</html>";
        assertThrows(CatalogoNoDisponible.class, () -> cliente(1_000).consultar(null));
    }

    @Test
    @DisplayName("un productos colgado se corta en el tiempo de lectura y no se reintenta")
    void tiempoDeEsperaSinReintentos() {
        esperaMs = 2_000;
        cuerpo = "{\"hasta\":\"2026-10-05T15:00:00Z\",\"completo\":true,\"alertas\":[]}";

        Instant inicio = Instant.now();
        assertThrows(CatalogoNoDisponible.class, () -> cliente(300).consultar(null));

        assertTrue(Duration.between(inicio, Instant.now()).compareTo(Duration.ofMillis(1_500)) < 0,
                "el tiempo de lectura tiene que cortar antes de que responda el servidor");
        assertEquals(1, rutas.size(), "sin reintentos: una sola peticion");
    }

    @Test
    @DisplayName("sin credencial configurada la peticion sale sin Authorization (y productos la rechazara)")
    void sinCredencial() {
        cuerpo = "{\"hasta\":\"2026-10-05T15:00:00Z\",\"completo\":true,\"alertas\":[]}";

        ConfiguracionDelCatalogo.cliente(
                "http://127.0.0.1:" + servidor.getAddress().getPort() + "/",
                500, 1_000, 10, null, null).consultar(null);

        assertEquals(List.of("null"), autorizaciones);
        assertEquals(List.of("/api/v1/productos/alertas/cambios"), rutas);
        assertEquals(List.of("limite=10"), consultas);
        assertNull(MDC.get(FiltroDeTraza.CLAVE_MDC));
    }
}
