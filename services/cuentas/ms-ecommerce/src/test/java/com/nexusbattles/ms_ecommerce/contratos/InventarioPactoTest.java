package com.nexusbattles.ms_ecommerce.contratos;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.nexusbattles.ms_ecommerce.integracion.ConfiguracionDeIntegraciones;
import com.nexusbattles.ms_ecommerce.integracion.PropiedadesDeLaTienda;
import com.nexusbattles.ms_ecommerce.integracion.inventario.ClienteDeInventario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pacto de consumidor con inventario: la entrega de lo comprado
 * ({@code POST /api/v1/inventario/entregas}, inventario.yaml 1.4.0/1.5.0).
 *
 * <p>Fija lo que la compra manda y de lo que depende: el jugador que recibe en
 * el cuerpo ({@code uid}, un UUID: la entrega la dispara tambien la tarea
 * programada, sin peticion del jugador), {@code origen} COMPRA, la orden como
 * {@code referencia}, cada producto con su cantidad y la
 * {@code Idempotency-Key} {@code orden-{id}}. De la respuesta solo importa el
 * codigo: 201 nueva, 200 la misma clave otra vez (reintento seguro), 409 un
 * producto suspendido y 422 uno inexistente (las dos compensan la orden). El
 * cuerpo no se pide: la tienda no lo lee, y exigirlo ataria al proveedor sin
 * proteger a nadie (la leccion del pacto de ms-subastas, R11.4).
 *
 * <p>No graba {@code Authorization}: la firma el verificador del proveedor con
 * un token de servicio.
 *
 * <p><b>Proveedor «inventario» sin verificador todavia:</b> las entregas las
 * implementa B4 en paralelo. Es un nombre de proveedor distinto de
 * «ms-inventario» (el del pacto de ms-subastas) a proposito: la verificacion
 * que ya existe carga todos los pactos de su proveedor y fallaria con estados
 * que no sabe montar. El integrador anade en inventario la clase de
 * verificacion del proveedor «inventario» (con un {@code @State} por cada uno
 * de estos cuatro estados) cuando B4 este fusionado; hasta entonces
 * {@code pactos-verificados.py} lo reporta como brecha conocida. Ojo: este
 * comentario no escribe la anotacion literal a proposito, porque el guardian
 * busca ese texto y tomaria esta clase por la verificadora.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "inventario", pactVersion = PactSpecVersion.V3)
class InventarioPactoTest {

    private static final String CONSUMIDOR = "ms-ecommerce";
    private static final UUID JUGADOR = UUID.fromString("7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55");
    private static final String ORDEN = "8f14e45f-ceea-467a-a8a1-6a6d1e1b0c3d";
    private static final String PRODUCTO = "5b0a3c1e-8d7f-4e2a-9c6b-1f0e2d3c4b5a";
    private static final String RUTA = "/api/v1/inventario/entregas";
    private static final String FORMA_DE_LA_CLAVE = "orden-[0-9a-f-]{36}";

    private static ClienteDeInventario clienteContra(MockServer servidor) {
        return new ClienteDeInventario(ConfiguracionDeIntegraciones.constructor(servidor.getUrl(),
                new PropiedadesDeLaTienda.Http(Duration.ofSeconds(2), Duration.ofSeconds(5))).build());
    }

    private static PactDslJsonBody solicitud() {
        PactDslJsonBody cuerpo = new PactDslJsonBody()
                .uuid("uid", JUGADOR)
                .stringValue("origen", "COMPRA")
                .stringMatcher("referencia", "[0-9a-f-]{36}", ORDEN);
        cuerpo.minArrayLike("productos", 1)
                .stringType("productoId", PRODUCTO)
                .integerType("cantidad", 2)
                .closeObject()
                .closeArray();
        return cuerpo;
    }

    private static ClienteDeInventario.ResultadoDeEntrega entregar(MockServer servidor) {
        return clienteContra(servidor).entregar(JUGADOR.toString(), ORDEN,
                List.of(new ClienteDeInventario.Unidades(PRODUCTO, 2)), "orden-" + ORDEN);
    }

    private RequestResponsePact entrega(PactDslWithProvider constructor, String estado, String descripcion,
                                        int codigo) {
        return constructor
                .given(estado)
                .uponReceiving(descripcion)
                .path(RUTA)
                .method("POST")
                .matchHeader("Idempotency-Key", FORMA_DE_LA_CLAVE, "orden-" + ORDEN)
                .headers(Map.of("Content-Type", "application/json"))
                .body(solicitud())
                .willRespondWith()
                .status(codigo)
                .toPact();
    }

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact entregaNueva(PactDslWithProvider constructor) {
        return entrega(constructor, "los productos de la entrega existen y no estan suspendidos",
                "la entrega de una compra cobrada", 201);
    }

    @Test
    @PactTestFor(pactMethod = "entregaNueva")
    void nueva(MockServer servidor) {
        assertThat(entregar(servidor)).isEqualTo(ClienteDeInventario.ResultadoDeEntrega.ENTREGADA);
    }

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact entregaRepetida(PactDslWithProvider constructor) {
        return entrega(constructor, "la entrega con esa clave ya se hizo con el mismo cuerpo",
                "el reintento de una entrega ya hecha", 200);
    }

    @Test
    @PactTestFor(pactMethod = "entregaRepetida")
    void repetida(MockServer servidor) {
        assertThat(entregar(servidor)).isEqualTo(ClienteDeInventario.ResultadoDeEntrega.ENTREGADA);
    }

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact entregaDeUnSuspendido(PactDslWithProvider constructor) {
        return entrega(constructor, "un producto de la entrega esta suspendido",
                "la entrega de una compra con un producto suspendido", 409);
    }

    @Test
    @PactTestFor(pactMethod = "entregaDeUnSuspendido")
    void suspendido(MockServer servidor) {
        assertThat(entregar(servidor)).isEqualTo(ClienteDeInventario.ResultadoDeEntrega.RECHAZADA);
    }

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact entregaDeUnInexistente(PactDslWithProvider constructor) {
        return entrega(constructor, "un producto de la entrega no existe en el catalogo",
                "la entrega de una compra con un producto que no existe", 422);
    }

    @Test
    @PactTestFor(pactMethod = "entregaDeUnInexistente")
    void inexistente(MockServer servidor) {
        assertThat(entregar(servidor)).isEqualTo(ClienteDeInventario.ResultadoDeEntrega.RECHAZADA);
    }
}
