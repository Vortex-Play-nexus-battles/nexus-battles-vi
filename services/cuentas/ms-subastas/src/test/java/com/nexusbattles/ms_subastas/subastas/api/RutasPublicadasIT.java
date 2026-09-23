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
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /** Con la cadena de seguridad real, una ruta protegida sin token es 401 exista o no: se pide con un jugador. */
    @org.springframework.test.context.DynamicPropertySource
    static void jwks(org.springframework.test.context.DynamicPropertyRegistry registro) {
        com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba.registrarJwks(registro);
    }

    private static final String TOKEN = com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba.emisor()
            .tokenDeJugador("rutas", java.util.UUID.randomUUID());

    private HttpResponse<String> pedir(String metodo, String ruta, String cuerpo) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + TOKEN)
                .method(metodo, cuerpo == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(cuerpo))
                .build();
        return cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
    }

    private int estadoDe(String metodo, String ruta, String cuerpo) throws Exception {
        return pedir(metodo, ruta, cuerpo).statusCode();
    }

    private void resuelve(String metodo, String ruta, String cuerpo) throws Exception {
        HttpResponse<String> respuesta = pedir(metodo, ruta, cuerpo);
        assertNotEquals(404, respuesta.statusCode(), metodo + " " + ruta + " no existe (404)");
        assertNotEquals(405, respuesta.statusCode(), metodo + " " + ruta + " existe con otro verbo (405): "
                + "casi siempre es un prefijo /api/v1 repetido sobre el context-path");
    }

    /**
     * Para las consultas que devuelven 404 con todo derecho cuando la subasta
     * no existe. Aqui el 404 no distingue por si solo una ruta ausente de un
     * recurso ausente, asi que se mira quien contesto: si el cuerpo trae el
     * problem+json de "subasta no encontrada", la peticion llego a su
     * controlador y la ruta existe. Un 404 de Spring por ruta desconocida no
     * lleva ese tipo.
     */
    private void resuelveAunqueElRecursoNoExista(String metodo, String ruta) throws Exception {
        HttpResponse<String> respuesta = pedir(metodo, ruta, null);
        assertNotEquals(405, respuesta.statusCode(), metodo + " " + ruta + " existe con otro verbo (405)");
        if (respuesta.statusCode() == 404) {
            assertTrue(respuesta.body().contains("subasta-no-encontrada"),
                    metodo + " " + ruta + " devolvio un 404 que no viene de esta API: la ruta no existe. "
                            + "Cuerpo: " + respuesta.body());
        }
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
        resuelveAunqueElRecursoNoExista("GET", subasta + "/pujas");
        resuelveAunqueElRecursoNoExista("GET", subasta + "/mi-participacion");
        resuelve("POST", subasta + "/pujas", "{}");
        resuelve("POST", subasta + "/compra-inmediata", "{}");
        resuelve("PUT", subasta + "/puja-automatica", "{}");
        // Con el token real la peticion llega al negocio: sin subasta, 404 de esta API.
        resuelveAunqueElRecursoNoExista("DELETE", subasta + "/puja-automatica");
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
