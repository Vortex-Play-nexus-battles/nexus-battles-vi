package com.nexusbattles.ms_identidad.auditoria.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Cuando ms-cumplimiento no registra el evento, el respaldo de Resilience4j
 * cancela la operacion con {@link AuditoriaNoDisponibleException} (fail-closed,
 * 7.3.5): la ruta responde 503 y no se aplica nada.
 */
@DisplayName("AuditoriaClient: sin auditoria no hay operacion")
class AuditoriaClientTest {

    @Test
    @DisplayName("el respaldo cancela con «auditoría no disponible», sin aplicar nada")
    void respaldoCancela() {
        AuditoriaClient cliente = new AuditoriaClient(mock(RestClient.class));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(cliente, "registrarConFallback",
                "BANEO", "admin", "uid-ana", "ACTIVO", "BANEADO", "Trampas reiteradas", "10.0.0.1",
                new IllegalStateException("ms-cumplimiento caido")))
                .isInstanceOf(AuditoriaNoDisponibleException.class)
                .hasMessageContaining("no respondió");
    }
}
