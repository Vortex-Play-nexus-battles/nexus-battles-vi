package com.nexusbattles.plataforma.comentarios.catalogo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.client.RestClient;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;

/**
 * Pacto de consumidor con el catalogo de productos — B3 (regla 1 de plataforma,
 * riesgo #3 del Project Charter).
 *
 * <p>Comentarios pregunta al catalogo si un producto existe antes de dejar
 * comentarlo o calificarlo, y lo que necesita de la respuesta es muy poco:
 * <ul>
 *   <li>que {@code GET /api/v1/productos/{id}} sea publico (no se manda
 *       token: la ruta es abierta en productos.yaml);</li>
 *   <li>que un producto que no existe responda <b>404</b>, que es lo unico
 *       que este servicio cree como «no existe»;</li>
 *   <li>que uno que existe responda 200 con su {@code id}: el cliente lo
 *       compara con el pedido para no tomar por producto un 200 de otra cosa.</li>
 * </ul>
 *
 * <p>Nada mas: ni nombre, ni tipo, ni precio. Un pacto que exige campos que el
 * consumidor no lee ata las manos del proveedor sin proteger a nadie (el mismo
 * criterio que R11.4 aplico a los pactos de ms-subastas).
 *
 * <p>El producto que existe es «Espada de una mano», del catalogo inicial
 * (services/contenido/productos/docs/catalogo-inicial-identificadores.md).
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "productos", pactVersion = PactSpecVersion.V3)
class CatalogoPactoTest {

    private static final String CONSUMIDOR = "comentarios";
    private static final String EXISTE = "1647b2ea-096d-37e7-b580-0172e4c62313";
    private static final String NO_EXISTE = "00000000-0000-4000-8000-00000000dead";

    private static ClienteCatalogo clienteContra(MockServer servidor) {
        return new ClienteCatalogo(RestClient.builder().baseUrl(servidor.getUrl()).build(),
                Clock.systemUTC(), Duration.ofMinutes(5), Duration.ofSeconds(30));
    }

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact productoQueExiste(PactDslWithProvider constructor) {
        return constructor
                .given("el producto existe en el catalogo")
                .uponReceiving("la consulta de un producto antes de comentarlo o calificarlo")
                .path(ClienteCatalogo.RUTA.replace("{id}", EXISTE))
                .method("GET")
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json"))
                // Solo el id, y con su valor: el cliente lo compara con el pedido.
                .body(new PactDslJsonBody().stringValue("id", EXISTE))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "productoQueExiste")
    void unProductoDelCatalogoExiste(MockServer servidor) {
        ClienteCatalogo cliente = clienteContra(servidor);

        assertEquals(CatalogoDeProductos.Existencia.EXISTE, cliente.existencia(EXISTE));
        assertDoesNotThrow(() -> cliente.exigirExistente(EXISTE));
    }

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact productoQueNoExiste(PactDslWithProvider constructor) {
        return constructor
                .given("el producto no existe en el catalogo")
                .uponReceiving("la consulta de un producto que no esta en el catalogo")
                .path(ClienteCatalogo.RUTA.replace("{id}", NO_EXISTE))
                .method("GET")
                .willRespondWith()
                // El 404 es lo que se pacta: es la unica respuesta que este
                // servicio cree como «no existe». El cuerpo (problem details)
                // no se lee, asi que no se exige.
                .status(404)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "productoQueNoExiste")
    void unProductoQueNoEstaEsInexistente(MockServer servidor) {
        ClienteCatalogo cliente = clienteContra(servidor);

        assertThrows(ProductoInexistente.class, () -> cliente.exigirExistente(NO_EXISTE));
    }
}
