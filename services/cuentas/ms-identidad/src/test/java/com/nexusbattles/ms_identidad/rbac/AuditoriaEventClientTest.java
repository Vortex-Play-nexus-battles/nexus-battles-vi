package com.nexusbattles.ms_identidad.rbac;

import com.nexusbattles.ms_identidad.rbac.security.AuditoriaEventClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class AuditoriaEventClientTest {

    @Test
    @DisplayName("AuditoriaEventClient maneja caídas de ms-cumplimiento sin lanzar excepciones (Fail-Safe)")
    void testAuditoriaClientFailsafeWhenServerDown() {
        AuditoriaEventClient client = new AuditoriaEventClient("http://localhost:9999/api/v1/admin/auditoria/eventos");

        assertDoesNotThrow(() -> {
            client.registrarBypassAsync("test_user", "JUGADOR", "BANEAR_DEFINITIVAMENTE", "FORBIDDEN_ROLE", "127.0.0.1");
        });
    }
}
