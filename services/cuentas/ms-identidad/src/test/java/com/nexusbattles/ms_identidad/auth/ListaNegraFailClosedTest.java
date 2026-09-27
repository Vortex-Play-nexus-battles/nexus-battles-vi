package com.nexusbattles.ms_identidad.auth;

import com.nexusbattles.ms_identidad.auth.validation.ApodoBlacklistValidator;
import com.nexusbattles.ms_identidad.auth.validation.ListaNegraClient;
import com.nexusbattles.ms_identidad.auth.validation.ModeracionNoDisponibleException;
import com.nexusbattles.ms_identidad.auth.validation.dto.ListaNegraResponse;
import com.nexusbattles.ms_identidad.config.ModeracionNoDisponibleAdvice;
import com.nexusbattles.ms_identidad.onboarding.ServidorFalso;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * B2 — «spiderman fue aceptado como apodo». Una de las tres causas era que,
 * con moderacion caida, el respaldo aprobaba cualquier apodo. Aqui se fija lo
 * contrario: sin respuesta no hay apodo, y el rechazo se decide por
 * {@code aprobado} o por la {@code accion} que publica moderacion.
 */
@DisplayName("Lista negra del apodo, fail-closed (B2)")
class ListaNegraFailClosedTest {

    private static ListaNegraResponse respuesta(boolean aprobado, String accion, String motivo) {
        ListaNegraResponse r = new ListaNegraResponse();
        r.setAprobado(aprobado);
        r.setAccion(accion);
        r.setMotivo(motivo);
        return r;
    }

    @Test
    @DisplayName("aprobado y PERMITIR: pasa; aprobado=false o accion=RECHAZAR: 400 con el motivo (o uno generico)")
    void decide() {
        ListaNegraClient cliente = mock(ListaNegraClient.class);
        ApodoBlacklistValidator validador = new ApodoBlacklistValidator(cliente);

        when(cliente.verificar("valkiria", "APODO")).thenReturn(respuesta(true, "PERMITIR", null));
        assertThatCode(() -> validador.validar("valkiria")).doesNotThrowAnyException();

        when(cliente.verificar("spiderman", "APODO")).thenReturn(respuesta(false, "RECHAZAR", "El apodo no está permitido."));
        assertThatThrownBy(() -> validador.validar("spiderman")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El apodo no está permitido.");

        when(cliente.verificar("raro", "APODO")).thenReturn(respuesta(true, "RECHAZAR", " "));
        assertThatThrownBy(() -> validador.validar("raro")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("términos prohibidos");

        assertThatCode(() -> validador.validar(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sin respuesta (cuerpo vacio o respaldo de Resilience4j): 503, nunca aprobado")
    void sinRespuesta() {
        ListaNegraClient cliente = mock(ListaNegraClient.class);
        when(cliente.verificar("x", "APODO")).thenReturn(null);
        assertThatThrownBy(() -> new ApodoBlacklistValidator(cliente).validar("x"))
                .isInstanceOf(ModeracionNoDisponibleException.class);

        ListaNegraClient real = new ListaNegraClient(RestClient.create());
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(real, "verificarConFallback", "spiderman", "APODO",
                new IllegalStateException("circuito abierto")))
                .isInstanceOf(ModeracionNoDisponibleException.class);
    }

    @Test
    @DisplayName("envia el contexto APODO y lee la accion aunque la respuesta traiga mas campos")
    void contrato() {
        try (ServidorFalso moderacion = new ServidorFalso()) {
            moderacion.responder("POST", "/api/v1/lista-negra/verificar", 200,
                    "{\"aprobado\":false,\"accion\":\"RECHAZAR\",\"motivo\":\"No permitido.\","
                            + "\"categoria\":\"CELEBRIDAD\",\"coincidencias\":[\"spiderman\"]}");
            ListaNegraClient cliente = new ListaNegraClient(RestClient.create());
            ReflectionTestUtils.setField(cliente, "urlListaNegra", moderacion.url() + "/api/v1/lista-negra/verificar");

            ListaNegraResponse leida = cliente.verificar("Spider-Man", "APODO");

            assertThat(leida.isAprobado()).isFalse();
            assertThat(leida.getAccion()).isEqualTo("RECHAZAR");
            assertThat(moderacion.recibidas().get(0).cuerpo())
                    .contains("\"texto\":\"Spider-Man\"").contains("\"contexto\":\"APODO\"");
        }
    }

    @Test
    @DisplayName("el 503 es problem details con Retry-After: 30, el mismo para todas las rutas")
    void respuesta503() {
        MockHttpServletRequest peticion = new MockHttpServletRequest("PUT", "/api/v1/perfiles/abc");
        ResponseEntity<ProblemDetail> r = new ModeracionNoDisponibleAdvice()
                .noDisponible(new ModeracionNoDisponibleException("No pudimos comprobar el apodo."), peticion);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(r.getHeaders().getFirst("Retry-After")).isEqualTo("30");
        assertThat(r.getBody().getType()).isEqualTo(ModeracionNoDisponibleAdvice.TIPO);
        assertThat(r.getBody().getDetail()).isEqualTo("No pudimos comprobar el apodo.");
        assertThat(r.getBody().getInstance().toString()).isEqualTo("/api/v1/perfiles/abc");
        assertThat(ModeracionNoDisponibleAdvice.respuesta("x", null).getBody().getInstance()).isNull();
    }
}
