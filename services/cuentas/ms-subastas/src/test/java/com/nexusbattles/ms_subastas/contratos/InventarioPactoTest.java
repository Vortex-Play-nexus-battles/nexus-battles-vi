package com.nexusbattles.ms_subastas.contratos;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClient;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClientHttp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pacto de consumidor con ms-inventario (regla 1 de plataforma).
 *
 * <p>Fija sobre todo <b>el identificador que cruza esta frontera</b>: el
 * propietario viaja como {@code propietarioUid} en el cuerpo, no como apodo en
 * una cabecera. Eso se acordo con Edwin el 14/09/2026 y Nicolay lo publico el
 * 15/09, y es justo la clase de acuerdo que se pierde si no queda escrito en
 * algo ejecutable: durante dias este cliente mando el UUID en
 * {@code X-User-Name}, que inventario compara contra el apodo, y todo bloqueo
 * real habria salido 403 sin que ninguna prueba lo notara.
 *
 * <p>La razon del acuerdo no es estetica: en tres de las cinco llamadas a
 * inventario no existe ningun apodo que propagar —el cierre por vencimiento y
 * la liberacion los dispara un {@code @Scheduled} sin peticion ni token, y la
 * compensacion transfiere al vendedor, que no es quien pidio nada.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "ms-inventario", pactVersion = PactSpecVersion.V3)
class InventarioPactoTest {

    private static final String CONSUMIDOR = "ms-subastas";
    private static final String ELEMENTO = "elem-hacha-01";
    private static final UUID PROPIETARIO = UUID.fromString("77777777-0000-0000-0000-0000000000cc");
    private static final UUID PRODUCTO = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID SUBASTA = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    private InventarioClientHttp clienteContra(MockServer servidor) {
        return new InventarioClientHttp(URI.create(servidor.getUrl()),
                HttpClient.newHttpClient(), new ObjectMapper(), Duration.ofSeconds(5));
    }

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact bloqueoDeUnElementoPropio(PactDslWithProvider constructor) {
        return constructor
                .given("el elemento existe, esta disponible y es del propietario indicado")
                .uponReceiving("el bloqueo del elemento al publicar la subasta")
                .path("/api/v1/inventario/elementos/" + ELEMENTO + "/bloqueo-subasta")
                .method("PUT")
                .matchHeader("Idempotency-Key", ".+")
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        // Lo que este pacto existe para proteger.
                        .uuid("propietarioUid", PROPIETARIO)
                        .uuid("subastaId", SUBASTA))
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody().stringType("elementoId", ELEMENTO))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "bloqueoDeUnElementoPropio")
    void elPropietarioViajaEnElCuerpoComoUuid(MockServer servidor) {
        assertDoesNotThrow(() -> clienteContra(servidor)
                .reservar(ELEMENTO, PROPIETARIO, SUBASTA, "clave-de-publicacion"));
    }

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact consultaDeUnElemento(PactDslWithProvider constructor) {
        return constructor
                .given("el elemento existe")
                .uponReceiving("la consulta de un elemento por su identificador")
                .path("/api/v1/inventario/elementos/" + ELEMENTO)
                .method("GET")
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .stringType("elementoId", ELEMENTO)
                        .uuid("productoId", PRODUCTO)
                        .uuid("propietarioUid", PROPIETARIO)
                        .booleanType("enUso", false)
                        .booleanType("disponible", true))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "consultaDeUnElemento")
    void laConsultaResuelveDuenoYProductoSinPedirIdentidad(MockServer servidor) {
        InventarioClient.ElementoInventario elemento =
                clienteContra(servidor).buscar(ELEMENTO).orElseThrow();

        assertEquals(PROPIETARIO, elemento.propietarioId());
        assertEquals(PRODUCTO, elemento.productoId());
        assertFalse(elemento.enUso());
    }

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact liberacionDelBloqueo(PactDslWithProvider constructor) {
        return constructor
                .given("el elemento esta bloqueado por esa subasta")
                .uponReceiving("la liberacion del bloqueo al cerrar la subasta")
                .path("/api/v1/inventario/elementos/" + ELEMENTO + "/bloqueo-subasta/" + SUBASTA)
                .method("DELETE")
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody().stringType("elementoId", ELEMENTO))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "liberacionDelBloqueo")
    void liberarNoNecesitaIdentidadPorqueLoLlamaUnJobSinToken(MockServer servidor) {
        // Que esta operacion no pida identidad es lo que hace viable el cierre
        // por vencimiento: lo dispara el reloj, no una peticion de nadie.
        assertDoesNotThrow(() -> clienteContra(servidor)
                .liberarReserva(ELEMENTO, SUBASTA, "clave-de-cierre"));
    }

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact consultaDeUnElementoBorrado(PactDslWithProvider constructor) {
        return constructor
                .given("el elemento no existe")
                .uponReceiving("la consulta de un elemento que ya no esta")
                .path("/api/v1/inventario/elementos/" + ELEMENTO)
                .method("GET")
                .willRespondWith()
                .status(404)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "consultaDeUnElementoBorrado")
    void unElementoQueYaNoExisteDevuelveVacio(MockServer servidor) {
        // Estado normal, no fallo: lo pudieron borrar entre publicar la subasta
        // y cerrarla. Quien llama decide, en vez de cazar una excepcion.
        assertTrue(clienteContra(servidor).buscar(ELEMENTO).isEmpty());
    }
}
