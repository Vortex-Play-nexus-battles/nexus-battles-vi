package com.nexusbattles.plataforma.comentarios.moderacion;

import com.nexusbattles.plataforma.comentarios.Comentario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * SA-5 (§7.3.5): cada decision de moderacion llega a la bitacora central desde
 * su asiento, con la forma de ms-cumplimiento-auditoria.yaml 1.3.0.
 */
class EnvioDeAuditoriaTest {

    private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");
    private static final String URL = "http://srv-ms-cumplimiento:8091/api/v1/admin/auditoria/eventos";

    private static AsientoDeModeracion asiento(AccionDeModeracion accion, Comentario.Estado de, Comentario.Estado a,
                                               String textoAnterior, String textoNuevo, String ip) {
        return new AsientoDeModeracion("asiento-1", "c-42", "5b2e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55", "moderadora_qa",
                accion, "Lenguaje ofensivo", de, a, AHORA.minusSeconds(30), textoAnterior, textoNuevo, ip);
    }

    @Nested
    @DisplayName("la forma del evento")
    class Forma {

        @Test
        @DisplayName("ocultar: ACTUALIZACION sobre comentario:<id>, de Publicado a Oculto, con su momento y referencia")
        void ocultar() {
            EnvioDeAuditoria.Evento evento = EnvioDeAuditoria.eventoDe(asiento(AccionDeModeracion.OCULTAR,
                    Comentario.Estado.PUBLICADO, Comentario.Estado.OCULTO, null, null, "203.0.113.9"));

            assertThat(evento.tipoAccion()).isEqualTo("ACTUALIZACION");
            assertThat(evento.administradorId()).isEqualTo("5b2e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55");
            assertThat(evento.afectado()).isEqualTo("comentario:c-42");
            assertThat(evento.valorAnterior()).isEqualTo("Publicado");
            assertThat(evento.valorNuevo()).isEqualTo("Oculto");
            assertThat(evento.motivo()).isEqualTo("Lenguaje ofensivo");
            assertThat(evento.ipOrigen()).isEqualTo("203.0.113.9");
            assertThat(evento.ocurridoEn()).isEqualTo("2026-10-14T14:59:30Z");
            assertThat(evento.referencia()).isEqualTo("comentarios:asiento-1");
        }

        @Test
        @DisplayName("editar: el texto anterior y el nuevo, citados y recortados")
        void editar() {
            EnvioDeAuditoria.Evento evento = EnvioDeAuditoria.eventoDe(asiento(AccionDeModeracion.EDITAR,
                    Comentario.Estado.PUBLICADO, Comentario.Estado.PUBLICADO, "Eres un idiota", "x".repeat(400),
                    "203.0.113.9"));

            assertThat(evento.valorAnterior()).isEqualTo("Publicado · «Eres un idiota»");
            assertThat(evento.valorNuevo()).startsWith("Publicado · «xxx").endsWith("…»")
                    .hasSize("Publicado · «".length() + 300 + 1);
        }

        @Test
        @DisplayName("cada accion con su tipo; un asiento sin IP (anterior a V5) dice «desconocida»")
        void tipos() {
            assertThat(EnvioDeAuditoria.tipoDe(AccionDeModeracion.APROBAR)).isEqualTo("APROBACION");
            assertThat(EnvioDeAuditoria.tipoDe(AccionDeModeracion.ELIMINAR)).isEqualTo("ELIMINACION_LOGICA");
            assertThat(EnvioDeAuditoria.tipoDe(AccionDeModeracion.RESTAURAR)).isEqualTo("ACTUALIZACION");
            assertThat(EnvioDeAuditoria.tipoDe(AccionDeModeracion.MARCAR)).isEqualTo("OTRO");
            assertThat(EnvioDeAuditoria.nombreDe(Comentario.Estado.EN_REVISION)).isEqualTo("En revisión");
            assertThat(EnvioDeAuditoria.eventoDe(asiento(AccionDeModeracion.APROBAR, Comentario.Estado.EN_REVISION,
                    Comentario.Estado.PUBLICADO, null, null, null)).ipOrigen()).isEqualTo("desconocida");
        }
    }

    @Nested
    @DisplayName("la entrega")
    class Entrega {

        private final AsientoRepository asientos = mock(AsientoRepository.class);
        private final RestClient.Builder constructor = RestClient.builder();
        private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        private final EnvioDeAuditoria envio = new EnvioDeAuditoria(asientos, constructor.build(), URL,
                Clock.fixed(AHORA, ZoneOffset.UTC), Duration.ofSeconds(10), Duration.ofMinutes(15));

        @Test
        @DisplayName("201 la deja auditada; 400 se queda con su motivo y una espera, sin descartarla")
        void entregaYRechazo() {
            AsientoDeModeracion bueno = asiento(AccionDeModeracion.OCULTAR, Comentario.Estado.PUBLICADO,
                    Comentario.Estado.OCULTO, null, null, "203.0.113.9");
            AsientoDeModeracion malo = asiento(AccionDeModeracion.APROBAR, Comentario.Estado.EN_REVISION,
                    Comentario.Estado.PUBLICADO, null, null, "203.0.113.9");
            when(asientos.porAuditar(any(), any(Pageable.class))).thenReturn(List.of(bueno, malo));
            servidor.expect(once(), requestTo(URL)).andExpect(method(HttpMethod.POST))
                    .andExpect(jsonPath("$.tipoAccion").value("ACTUALIZACION"))
                    .andRespond(withStatus(HttpStatus.CREATED));
            servidor.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

            assertThat(envio.ejecutar()).isEqualTo(1);

            assertThat(bueno.auditadoEn()).isEqualTo(AHORA);
            assertThat(malo.auditadoEn()).isNull();
            assertThat(malo.errorDeAuditoria()).contains("400");
            assertThat(malo.proximoIntentoDeAuditoria()).isEqualTo(AHORA.plusSeconds(10));
            servidor.verify();
        }

        @Test
        @DisplayName("si la bitacora central falla se para la vuelta: las demas esperan sin gastar intentos")
        void caida() {
            AsientoDeModeracion primero = asiento(AccionDeModeracion.OCULTAR, Comentario.Estado.PUBLICADO,
                    Comentario.Estado.OCULTO, null, null, "203.0.113.9");
            AsientoDeModeracion segundo = asiento(AccionDeModeracion.OCULTAR, Comentario.Estado.PUBLICADO,
                    Comentario.Estado.OCULTO, null, null, "203.0.113.9");
            when(asientos.porAuditar(any(), any(Pageable.class))).thenReturn(List.of(primero, segundo));
            servidor.expect(once(), requestTo(URL)).andRespond(withServerError());

            assertThat(envio.ejecutar()).isZero();

            assertThat(primero.intentosDeAuditoria()).isEqualTo(1);
            assertThat(segundo.intentosDeAuditoria()).isZero();
            verify(asientos, never()).save(segundo);
        }
    }
}
