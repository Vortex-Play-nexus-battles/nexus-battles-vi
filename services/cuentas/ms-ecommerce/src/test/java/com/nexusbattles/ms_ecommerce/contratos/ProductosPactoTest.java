package com.nexusbattles.ms_ecommerce.contratos;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.nexusbattles.ms_ecommerce.catalogo.ReservasDeTiraje;
import com.nexusbattles.ms_ecommerce.integracion.ConfiguracionDeIntegraciones;
import com.nexusbattles.ms_ecommerce.integracion.PropiedadesDeLaTienda;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pacto de consumidor con productos: la reserva de tiraje al cobrar una orden
 * ({@code POST /api/v1/productos/{id}/adquisiciones}, productos.yaml 1.4.0).
 *
 * <p>Fija lo que la compra necesita de verdad: una unidad por llamada, la
 * {@code Idempotency-Key} derivada de la orden, la linea y la unidad (sin ella
 * el proveedor responde 400), y la diferencia entre ACEPTADA (200) y AGOTADO o
 * SUSPENDIDO (409 con el mismo cuerpo), que es lo que decide entre entregar y
 * reembolsar. El cuerpo de la respuesta se pide solo en lo que se lee
 * ({@code estado}); el mensaje, por tipo.
 *
 * <p>No graba {@code Authorization}, igual que los pactos de ms-subastas: el
 * verificador del proveedor firma cada peticion con un token de servicio del
 * emisor de prueba.
 *
 * <p><b>Lo verifica productos</b>: {@code VerificacionDelPactoDeEcommerceTest}
 * en {@code services/contenido/productos}, con el caso de uso real de la
 * reserva (tiraje y claves en memoria) y un estado montado por cada
 * {@code given} de aqui. Un {@code given} nuevo o renombrado exige su estado
 * alli; si falta, el guardian {@code pactos-verificados.py} se pone rojo. (La
 * anotacion del proveedor no se escribe aqui literalmente: el guardian busca
 * ese texto y tomaria esta clase por la verificadora.)
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "productos", pactVersion = PactSpecVersion.V3)
class ProductosPactoTest {

    private static final String CONSUMIDOR = "ms-ecommerce";
    private static final String PRODUCTO = "5b0a3c1e-8d7f-4e2a-9c6b-1f0e2d3c4b5a";
    private static final String RUTA = "/api/v1/productos/" + PRODUCTO + "/adquisiciones";
    private static final String CLAVE = "orden-8f14e45f-ceea-467a-a8a1-6a6d1e1b0c3d-l1-u1";
    private static final String FORMA_DE_LA_CLAVE = "orden-[0-9a-f-]{36}-l[0-9]+-u[0-9]+";

    private static ReservasDeTiraje clienteContra(MockServer servidor) {
        return new ReservasDeTiraje(ConfiguracionDeIntegraciones.constructor(servidor.getUrl(),
                new PropiedadesDeLaTienda.Http(Duration.ofSeconds(2), Duration.ofSeconds(5))).build());
    }

    private static PactDslJsonBody resultado(String estado) {
        return new PactDslJsonBody()
                .stringValue("estado", estado)
                .stringType("mensaje", "Resultado de la reserva");
    }

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact reservaAceptada(PactDslWithProvider constructor) {
        return constructor
                .given("el producto existe y le quedan unidades")
                .uponReceiving("la reserva de una unidad al cobrar una orden")
                .path(RUTA)
                .method("POST")
                .matchHeader("Idempotency-Key", FORMA_DE_LA_CLAVE, CLAVE)
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body(resultado("ACEPTADA"))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "reservaAceptada")
    void aceptada(MockServer servidor) {
        assertThat(clienteContra(servidor).reservarUnaUnidad(PRODUCTO, CLAVE))
                .isEqualTo(ReservasDeTiraje.Resultado.ACEPTADA);
    }

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact reservaAgotada(PactDslWithProvider constructor) {
        return constructor
                .given("el producto existe y esta agotado")
                .uponReceiving("la reserva de una unidad de un producto agotado")
                .path(RUTA)
                .method("POST")
                .matchHeader("Idempotency-Key", FORMA_DE_LA_CLAVE, CLAVE)
                .willRespondWith()
                .status(409)
                .headers(Map.of("Content-Type", "application/json"))
                .body(resultado("AGOTADO"))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "reservaAgotada")
    void agotada(MockServer servidor) {
        assertThat(clienteContra(servidor).reservarUnaUnidad(PRODUCTO, CLAVE))
                .isEqualTo(ReservasDeTiraje.Resultado.AGOTADO);
    }

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact reservaSuspendida(PactDslWithProvider constructor) {
        return constructor
                .given("el producto existe y esta suspendido")
                .uponReceiving("la reserva de una unidad de un producto suspendido")
                .path(RUTA)
                .method("POST")
                .matchHeader("Idempotency-Key", FORMA_DE_LA_CLAVE, CLAVE)
                .willRespondWith()
                .status(409)
                .headers(Map.of("Content-Type", "application/json"))
                .body(resultado("SUSPENDIDO"))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "reservaSuspendida")
    void suspendida(MockServer servidor) {
        assertThat(clienteContra(servidor).reservarUnaUnidad(PRODUCTO, CLAVE))
                .isEqualTo(ReservasDeTiraje.Resultado.SUSPENDIDO);
    }

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact reservaDeUnProductoInexistente(PactDslWithProvider constructor) {
        return constructor
                .given("el producto no existe")
                .uponReceiving("la reserva de una unidad de un producto que no existe")
                .path(RUTA)
                .method("POST")
                .matchHeader("Idempotency-Key", FORMA_DE_LA_CLAVE, CLAVE)
                .willRespondWith()
                .status(404)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "reservaDeUnProductoInexistente")
    void inexistente(MockServer servidor) {
        assertThat(clienteContra(servidor).reservarUnaUnidad(PRODUCTO, CLAVE))
                .isEqualTo(ReservasDeTiraje.Resultado.INEXISTENTE);
    }
}
