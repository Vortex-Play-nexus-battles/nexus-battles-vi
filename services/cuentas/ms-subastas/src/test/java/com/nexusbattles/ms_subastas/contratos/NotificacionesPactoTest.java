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
import com.nexusbattles.comun.seguridad.servicio.TokenDeServicio;
import com.nexusbattles.ms_subastas.notificaciones.NotificacionesClient;
import com.nexusbattles.ms_subastas.notificaciones.NotificacionesClientException;
import com.nexusbattles.ms_subastas.notificaciones.NotificacionesClientHttp;
import com.nexusbattles.ms_subastas.notificaciones.TipoNotificacion;
import com.nexusbattles.ms_subastas.seguridad.PortadorDeServicio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pacto de consumidor con el modulo de notificaciones (B8): lo que ms-subastas
 * necesita de {@code POST /api/v1/internal/notifications}
 * ({@code contracts/openapi/notificaciones.yaml}), ni mas ni menos.
 *
 * <p>Fija tres cosas, y las tres han fallado en produccion o han estado a
 * punto:
 *
 * <ul>
 *   <li><b>La credencial de servicio.</b> Hasta B8 este adaptador salia sin
 *       {@code Authorization} y cada aviso recibia 401: el outbox los
 *       reintentaba para siempre y ningun jugador se enteraba de nada. El pacto
 *       registra la cabecera, y la tercera interaccion deja escrito que sin
 *       ella notificaciones responde 401 —que ms-subastas trata como fallo que
 *       se reintenta, nunca como entregado—.</li>
 *   <li><b>Los nombres exactos del cuerpo</b> ({@code usuarioId}, {@code id},
 *       {@code tipo}, {@code titulo}, {@code cuerpo}, {@code creadaEn} en
 *       ISO-8601).</li>
 *   <li><b>El 409 del duplicado.</b> El {@code id} del aviso es estable por
 *       evento (UUID v3 de la clave del hecho): si la entrega anterior llego y
 *       se perdio la respuesta, el reintento recibe 409 y ms-subastas lo da por
 *       entregado. Si notificaciones dejara de devolver 409, el drenador
 *       reintentaria el mismo aviso para siempre.</li>
 * </ul>
 *
 * <p>ms-subastas no lee el cuerpo de la respuesta, asi que el pacto no lo fija:
 * exigirlo seria atar al proveedor a algo que nadie usa. El pacto queda en
 * {@code contracts/pactos/ms-subastas-notificaciones.json} y lo verifica
 * {@code VerificacionDelPactoDeSubastasTest} en notificaciones.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "notificaciones", pactVersion = PactSpecVersion.V3)
@DisplayName("Pacto: lo que ms-subastas espera de notificaciones")
class NotificacionesPactoTest {

    private static final String CONSUMIDOR = "ms-subastas";
    private static final String RUTA = "/api/v1/internal/notifications";
    private static final String INSTANTE_ISO = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z";

    private static final UUID DESTINATARIO = UUID.fromString("77777777-0000-0000-0000-0000000000cc");
    private static final UUID AVISO_NUEVO = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID AVISO_REPETIDO = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final Instant CREADA_EN = Instant.parse("2026-09-20T12:00:00Z");

    private static final TokenDeServicio CREDENCIAL = () -> "token-de-servicio-de-ms-subastas";

    private static NotificacionesClientHttp cliente(MockServer servidor, PortadorDeServicio portador) {
        return new NotificacionesClientHttp(URI.create(servidor.getUrl() + "/api/v1"), HttpClient.newHttpClient(),
                new ObjectMapper(), Duration.ofSeconds(5), portador);
    }

    private static NotificacionesClient.Aviso aviso(UUID id) {
        return new NotificacionesClient.Aviso(id, DESTINATARIO, TipoNotificacion.PUJA_SUPERADA,
                "Te superaron en una subasta · Espada del Alba",
                "Otra puja superó tu oferta de 110 créditos por Espada del Alba.", CREADA_EN);
    }

    private static PactDslJsonBody cuerpoDelAviso(UUID id) {
        return new PactDslJsonBody()
                .uuid("usuarioId", DESTINATARIO)
                .uuid("id", id)
                .stringType("tipo", TipoNotificacion.PUJA_SUPERADA.name())
                .stringType("titulo", "Te superaron en una subasta · Espada del Alba")
                .stringType("cuerpo", "Otra puja superó tu oferta de 110 créditos por Espada del Alba.")
                .stringMatcher("creadaEn", INSTANTE_ISO, "2026-09-20T12:00:00Z");
    }

    // --- el aviso nuevo -------------------------------------------------------------

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact avisoNuevo(PactDslWithProvider constructor) {
        return constructor
                .given("el destinatario todavia no tiene ese aviso")
                .uponReceiving("un aviso de subasta con la credencial de servicio de ms-subastas")
                .path(RUTA)
                .method("POST")
                .matchHeader("Authorization", "Bearer .+", "Bearer token-de-servicio-de-ms-subastas")
                .headers(Map.of("Content-Type", "application/json"))
                .body(cuerpoDelAviso(AVISO_NUEVO))
                .willRespondWith()
                .status(201)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "avisoNuevo")
    @DisplayName("con credencial, un aviso nuevo se entrega (201)")
    void entregaUnAvisoNuevo(MockServer servidor) {
        assertDoesNotThrow(() -> cliente(servidor, PortadorDeServicio.de(CREDENCIAL)).entregar(aviso(AVISO_NUEVO)));
    }

    // --- el reintento de un aviso que ya llego ---------------------------------------

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact avisoRepetido(PactDslWithProvider constructor) {
        return constructor
                .given("el destinatario ya tiene un aviso con ese identificador")
                .uponReceiving("el reintento de un aviso que ya estaba en la bandeja")
                .path(RUTA)
                .method("POST")
                .matchHeader("Authorization", "Bearer .+", "Bearer token-de-servicio-de-ms-subastas")
                .headers(Map.of("Content-Type", "application/json"))
                .body(cuerpoDelAviso(AVISO_REPETIDO))
                .willRespondWith()
                .status(409)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "avisoRepetido")
    @DisplayName("un 409 es «ya entregado»: el reintento no es un fallo")
    void unDuplicadoSeDaPorEntregado(MockServer servidor) {
        assertDoesNotThrow(() -> cliente(servidor, PortadorDeServicio.de(CREDENCIAL)).entregar(aviso(AVISO_REPETIDO)));
    }

    // --- sin credencial --------------------------------------------------------------

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact avisoSinCredencial(PactDslWithProvider constructor) {
        return constructor
                .uponReceiving("un aviso sin credencial de servicio")
                .path(RUTA)
                .method("POST")
                .headers(Map.of("Content-Type", "application/json"))
                .body(cuerpoDelAviso(AVISO_NUEVO))
                .willRespondWith()
                .status(401)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "avisoSinCredencial")
    @DisplayName("sin credencial es 401, y eso es un fallo que se reintenta, nunca «entregado»")
    void sinCredencialNoSeDaPorEntregado(MockServer servidor) {
        NotificacionesClientException error = assertThrows(NotificacionesClientException.class,
                () -> cliente(servidor, PortadorDeServicio.ninguno()).entregar(aviso(AVISO_NUEVO)));

        assertFalse(error.esDefinitivo(), "la credencial se arregla sin tocar el aviso: se reintenta");
    }
}
