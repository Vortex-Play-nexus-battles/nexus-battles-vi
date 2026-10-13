package nexus.misiones.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.report.ValidationReport;
import com.nexusbattles.plataforma.resiliencia.DependenciaDegradada;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import nexus.misiones.DependenciasFalsas;
import nexus.misiones.aplicacion.AvisosDeMisiones;
import nexus.misiones.aplicacion.RechazoDelServicio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El aviso de misiones contra la bandeja ({@code POST /internal/notifications},
 * notificaciones.yaml 1.2.0) y lo que cada respuesta significa para la
 * liquidacion: 201 entregado, 409 ya estaba (entregado), otro 4xx definitivo,
 * 5xx y 401/408/429 reintentables. La peticion se valida contra el contrato
 * del proveedor: misiones no publica un pacto con notificaciones (su
 * verificacion es del dueno de ese servicio) pero no manda nada que el
 * contrato no admita.
 */
class ClienteNotificacionesTest {

    private static final Instant TERMINO = Instant.parse("2026-10-04T15:00:00Z");
    private static final String CONTRATO = System.getProperty("notificaciones.contrato",
            "../../../contracts/openapi/notificaciones.yaml");

    private DependenciasFalsas falsas;
    private ClienteNotificaciones cliente;

    @BeforeEach
    void arrancar() throws IOException {
        falsas = new DependenciasFalsas();
        cliente = new ClienteNotificaciones(ClientesDePrueba.rest(), falsas.base() + "/notificaciones/api/v1/",
                ClientesDePrueba.corta("notificaciones"));
    }

    @AfterEach
    void parar() {
        falsas.close();
    }

    @Test
    @DisplayName("el aviso viaja con los seis campos del contrato, tipo MISION y la hora del hecho")
    void avisoSegunElContrato() throws IOException {
        String uid = UUID.randomUUID().toString();

        cliente.avisar(uid, "mision-1-aviso", "Tu misión «El Templo Olvidado» terminó con éxito",
                "Vorn volvió con la misión cumplida.", TERMINO);

        DependenciasFalsas.Peticion enviada = falsas.a("POST", "/notificaciones/").getFirst();
        assertThat(enviada.ruta()).isEqualTo("/notificaciones/api/v1/internal/notifications");
        Map<String, Object> cuerpo = enviada.json();
        assertThat(cuerpo).containsExactlyInAnyOrderEntriesOf(Map.of(
                "usuarioId", uid,
                "id", "mision-1-aviso",
                "tipo", AvisosDeMisiones.TIPO,
                "titulo", "Tu misión «El Templo Olvidado» terminó con éxito",
                "cuerpo", "Vorn volvió con la misión cumplida.",
                "creadaEn", "2026-10-04T15:00:00Z"));
        assertThat(AvisosDeMisiones.TIPO).isEqualTo("MISION");

        ValidationReport informe = validador().validateRequest(SimpleRequest.Builder
                .post("/api/v1/internal/notifications")
                .withContentType("application/json")
                .withAuthorization("Bearer credencial-de-servicio")
                .withBody(enviada.cuerpo())
                .build());
        assertThat(informe.getMessages()).as("la peticion cumple EmitirNotificacionRequest").isEmpty();
    }

    @Test
    @DisplayName("409 es el reintento de un aviso que ya llego: cuenta como entregado, sin error")
    void repetidoEsEntregado() {
        cliente.avisar("uid-1", "mision-2-aviso", "titulo", "cuerpo", TERMINO);

        assertThatCode(() -> cliente.avisar("uid-1", "mision-2-aviso", "titulo", "cuerpo", TERMINO))
                .doesNotThrowAnyException();
        assertThat(falsas.avisos).hasSize(1);
    }

    @Test
    @DisplayName("un 400 es definitivo: rechazo con su motivo, que la liquidacion no reintenta")
    void rechazoDefinitivo() {
        falsas.forzados.put("/internal/notifications", 400);

        assertThatThrownBy(() -> cliente.avisar("uid-1", "mision-3-aviso", "titulo", "cuerpo", TERMINO))
                .isInstanceOf(RechazoDelServicio.class)
                .hasMessageContaining("400");
    }

    @Test
    @DisplayName("caida, credencial que no vale o cupo agotado: falta de respuesta, que se reintenta")
    void pasajerosSeReintentan() {
        falsas.caidas.add("notificaciones");
        assertThatThrownBy(() -> cliente.avisar("uid-1", "mision-4-aviso", "t", "c", TERMINO))
                .isInstanceOf(DependenciaDegradada.class);
        falsas.caidas.clear();

        for (int estado : new int[] {401, 408, 429}) {
            falsas.forzados.put("/internal/notifications", estado);
            assertThatThrownBy(() -> cliente.avisar("uid-1", "mision-4-aviso", "t", "c", TERMINO))
                    .as("HTTP %d", estado)
                    .isInstanceOf(DependenciaDegradada.class);
        }
    }

    private static OpenApiInteractionValidator validador() throws IOException {
        String contrato = Files.readString(Path.of(CONTRATO));
        return OpenApiInteractionValidator.createForInlineApiSpecification(contrato)
                .withBasePathOverride("/api/v1")
                .build();
    }
}
