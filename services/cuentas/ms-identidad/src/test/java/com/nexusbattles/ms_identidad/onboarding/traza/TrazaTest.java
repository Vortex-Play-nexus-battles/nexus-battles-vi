package com.nexusbattles.ms_identidad.onboarding.traza;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Traza W3C del alta (regla 5)")
class TrazaTest {

    private static final String TRAZA = "4bf92f3577b34da6a3ce929d0e0e4736";

    @AfterEach
    void limpiar() {
        Traza.cerrar();
    }

    @Test
    @DisplayName("lee el trace-id de un traceparent valido y rechaza los que no lo son")
    void traceIdDe() {
        assertThat(Traza.traceIdDe("00-" + TRAZA + "-00f067aa0ba902b7-01")).contains(TRAZA);
        assertThat(Traza.traceIdDe(" 00-" + TRAZA.toUpperCase() + "-00f067aa0ba902b7-00 ")).contains(TRAZA);
        assertThat(Traza.traceIdDe("00-00000000000000000000000000000000-00f067aa0ba902b7-01")).isEmpty();
        assertThat(Traza.traceIdDe("01-" + TRAZA + "-00f067aa0ba902b7-01")).isEmpty();
        assertThat(Traza.traceIdDe("basura")).isEmpty();
        assertThat(Traza.traceIdDe(null)).isEmpty();
    }

    @Test
    @DisplayName("abrir fija la traza del hilo y del MDC; cada llamada saliente lleva un span nuevo")
    void abrirYPropagar() {
        assertThat(Traza.abrir(TRAZA)).isEqualTo(TRAZA);
        assertThat(MDC.get(Traza.CLAVE_MDC)).isEqualTo(TRAZA);

        String primera = Traza.traceparentHijo().orElseThrow();
        String segunda = Traza.traceparentHijo().orElseThrow();
        assertThat(primera).matches("00-" + TRAZA + "-[0-9a-f]{16}-01");
        assertThat(primera).isNotEqualTo(segunda);

        Traza.cerrar();
        assertThat(Traza.actual()).isEmpty();
        assertThat(Traza.traceparentHijo()).isEmpty();
        assertThat(MDC.get(Traza.CLAVE_MDC)).isNull();
    }

    @Test
    @DisplayName("una traza guardada invalida (o ninguna) se sustituye por una nueva")
    void trazaInvalida() {
        assertThat(Traza.abrir(null)).matches("[0-9a-f]{32}");
        assertThat(Traza.abrir("corta")).matches("[0-9a-f]{32}");
        assertThat(Traza.abrir("00000000000000000000000000000000")).isNotEqualTo("00000000000000000000000000000000");
        assertThat(Traza.nuevoTraceId()).isNotEqualTo(Traza.nuevoTraceId());
    }
}
