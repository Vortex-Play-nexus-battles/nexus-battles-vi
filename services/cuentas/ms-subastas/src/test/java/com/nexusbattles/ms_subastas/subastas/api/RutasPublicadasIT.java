package com.nexusbattles.ms_subastas.subastas.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Comprueba que cada ruta de los contratos existe donde ellos dicen.
 *
 * <p><b>Por que hace falta una prueba aparte para algo tan tonto.</b> El
 * endpoint de publicacion estuvo mapeado a {@code /api/v1/subastas} mientras
 * {@code server.servlet.context-path} ya valia {@code /api/v1}, asi que vivia
 * en {@code /api/v1/api/v1/subastas} y la ruta del contrato respondia 405. Su
 * prueba no lo detecto porque usa {@code MockMvcBuilders.standaloneSetup}, que
 * <b>nunca aplica el context-path</b>: pasaba en verde contra una ruta que en
 * el servicio real no existia.
 *
 * <p>Por eso esto arranca el servidor de verdad y pide por HTTP. No comprueba
 * el comportamiento —para eso estan las pruebas de cada endpoint— sino algo
 * mas basico: que la ruta resuelve. Un 400 o un 401 valen, porque significan
 * que la peticion llego a su controlador; un 404 o un 405 no.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.pujas.emision-automatica-intervalo-ms=3600000",
                "app.subastas.cierre-intervalo-ms=3600000",
                "app.notificaciones.drenaje-intervalo-ms=3600000"
        })
@Testcontainers(disabledWithoutDocker = true)
class RutasPublicadasIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @LocalServerPort
    private int puerto;

    private final HttpClient cliente = HttpClient.newHttpClient();

    private int estadoDe(String metodo, String ruta, String cuerpo) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Content-Type", "application/json")
                .method(metodo, cuerpo == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(cuerpo))
                .build();
        return cliente.send(peticion, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private void resuelve(String metodo, String ruta, String cuerpo) throws Exception {
        int estado = estadoDe(metodo, ruta, cuerpo);
        assertNotEquals(404, estado, metodo + " " + ruta + " no existe (404)");
        assertNotEquals(405, estado, metodo + " " + ruta + " existe con otro verbo (405): "
                + "casi siempre es un prefijo /api/v1 repetido sobre el context-path");
    }

    /** ms-subastas-publicar.yaml: server .../api/v1 + ruta /subastas. */
    @Test
    void publicarRespondeDondeDiceSuContrato() throws Exception {
        resuelve("POST", "/api/v1/subastas", "{}");
    }

    /** ms-subastas-listado.yaml */
    @Test
    void elListadoRespondeDondeDiceSuContrato() throws Exception {
        resuelve("GET", "/api/v1/subastas", null);
        resuelve("GET", "/api/v1/subastas/sugerencias?q=hacha", null);
    }

    /** ms-subastas-pujas.yaml */
    @Test
    void lasRutasDePujasRespondenDondeDiceSuContrato() throws Exception {
        String subasta = "/api/v1/subastas/" + UUID.randomUUID();
        resuelve("POST", subasta + "/pujas", "{}");
        resuelve("POST", subasta + "/compra-inmediata", "{}");
        resuelve("PUT", subasta + "/puja-automatica", "{}");
        resuelve("DELETE", subasta + "/puja-automatica", null);
    }

    /**
     * La otra mitad: la ruta con el prefijo repetido NO debe existir. Sin esto,
     * alguien podria "arreglar" el 405 anadiendo el mapeo duplicado en vez de
     * quitarlo.
     */
    @Test
    void laRutaConElPrefijoRepetidoNoExiste() throws Exception {
        assertNotEquals(400, estadoDe("POST", "/api/v1/api/v1/subastas", "{}"),
                "/api/v1/api/v1/subastas no deberia resolver: el context-path ya aporta /api/v1");
    }
}
