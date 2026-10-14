package com.nexusbattles.ms_cumplimiento.auditoria.catalogo;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.DslPart;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pacto de consumidor con productos — SA-18: la bitácora importa las
 * operaciones administrativas del catálogo
 * ({@code GET /api/v1/productos/auditoria/operaciones}, productos.yaml 1.12.0).
 *
 * <p>Fija lo que el importador lee: el lote con {@code hasta} y
 * {@code completo}, y de cada operación los campos que pasan a la bitácora
 * (quién, cuándo, qué, sobre qué y su nombre, antes y después, motivo,
 * descripción e IP). Los valores se piden por tipo y las fechas por forma
 * ISO-8601: el contenido es de productos.
 *
 * <p>No graba {@code Authorization}, como los demás pactos: el verificador del
 * proveedor firma cada petición con un token de servicio de ms-cumplimiento del
 * emisor de prueba. Lo verifica productos en
 * {@code VerificacionDelPactoDeCumplimientoTest}. (La anotación del proveedor
 * no se escribe aquí literalmente: el guardian de pactos busca ese texto.)
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "productos", pactVersion = PactSpecVersion.V3)
class ProductosPactoTest {

    private static final String CONSUMIDOR = "ms-cumplimiento";
    private static final String RUTA = "/api/v1/productos/auditoria/operaciones";
    private static final String INSTANTE = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z";

    private static ClienteDelCatalogo clienteContra(MockServer servidor) {
        CredencialDeServicio credencial = mock(CredencialDeServicio.class);
        when(credencial.portador()).thenReturn("token-de-prueba");
        return new ClienteDelCatalogo(RestClient.create(), servidor.getUrl(), credencial);
    }

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact operacionesDelCatalogo(PactDslWithProvider constructor) {
        DslPart lote = new PactDslJsonBody()
            .stringMatcher("hasta", INSTANTE, "2026-10-14T15:00:00.250Z")
            .booleanType("completo", true)
            .minArrayLike("operaciones", 1)
                .stringType("id", "7b1f0c9e-2a44-4f7b-9d1e-3c5a8b2e6f10")
                .stringMatcher("ocurridaEn", INSTANTE, "2026-10-14T15:00:00.250Z")
                .stringType("autor", "8f14e45f-ceea-467f-a0e6-9ea7c2b1e7a1")
                .stringType("tipo", "SUSPENSION_PRODUCTO")
                .stringType("objeto", "PRODUCTO")
                .stringType("objetoId", "550e8400-e29b-41d4-a716-446655440000")
                .stringType("nombre", "Espada solar")
                .stringType("valorAnterior", "Estado: Activo")
                .stringType("valorNuevo", "Estado: Suspendido")
                .stringType("motivo", "Rompe el balance de la temporada")
                .stringType("descripcion", "Suspensión del producto")
                .stringType("ipOrigen", "181.52.10.4")
            .closeObject()
            .closeArray();
        return constructor
            .given("hay operaciones administrativas del catalogo")
            .uponReceiving("la lectura de las operaciones del catalogo desde la primera")
            .path(RUTA)
            .method("GET")
            .query("limite=100")
            .willRespondWith()
            .status(200)
            .headers(Map.of("Content-Type", "application/json"))
            .body(lote)
            .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "operacionesDelCatalogo")
    void lote(MockServer servidor) {
        LoteDelCatalogo lote = clienteContra(servidor).consultar(null, 100);

        assertThat(lote.estaCompleto()).isTrue();
        assertThat(lote.operaciones()).singleElement().satisfies(operacion -> {
            assertThat(ImportadorDelCatalogo.aEvento(operacion).tipoAccion()).isEqualTo("SUSPENSION");
            assertThat(ImportadorDelCatalogo.aEvento(operacion).afectado())
                .isEqualTo("producto:550e8400-e29b-41d4-a716-446655440000");
            assertThat(operacion.ipOrigen()).isEqualTo("181.52.10.4");
        });
    }

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact sinOperacionesNuevas(PactDslWithProvider constructor) {
        return constructor
            .given("no hay operaciones administrativas del catalogo")
            .uponReceiving("la lectura de las operaciones del catalogo desde un punto")
            .path(RUTA)
            .method("GET")
            .matchQuery("desde", INSTANTE, "2026-10-14T14:58:00Z")
            .matchQuery("limite", "100", "100")
            .willRespondWith()
            .status(200)
            .headers(Map.of("Content-Type", "application/json"))
            .body(new PactDslJsonBody()
                .stringMatcher("hasta", INSTANTE, "2026-10-14T14:58:00Z")
                .booleanValue("completo", true)
                .array("operaciones").closeArray())
            .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "sinOperacionesNuevas")
    void vacio(MockServer servidor) {
        LoteDelCatalogo lote = clienteContra(servidor).consultar(Instant.parse("2026-10-14T14:58:00Z"), 100);

        assertThat(lote.operaciones()).isEmpty();
        assertThat(lote.hasta()).isEqualTo(Instant.parse("2026-10-14T14:58:00Z"));
    }
}
