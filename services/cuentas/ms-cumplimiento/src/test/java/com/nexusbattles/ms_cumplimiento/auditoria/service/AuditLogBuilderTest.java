package com.nexusbattles.ms_cumplimiento.auditoria.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Prueba unitaria pura: no necesita Mockito ni Spring, solo instancia
// el Builder directamente. Confirma que HU-AUD-001 se cumple a nivel
// de código Java (no solo a nivel de base de datos): es imposible
// construir un AuditLog al que le falte cualquiera de los 7 campos
// obligatorios, sin importar qué mecanismo lo intente crear.
class AuditLogBuilderTest {

    @Test
    void deberiaRechazarConstruccionSiFaltaAdministradorId() {
        assertThatThrownBy(() -> AuditLog.builder()
            .tipoAccion(AuditActionType.CREACION)
            // administradorId() nunca se llama
            .afectado("usuario-1")
            .motivo("Prueba")
            .ipOrigen("127.0.0.1")
            .build()
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deberiaRechazarConstruccionSiFaltaTipoAccion() {
        assertThatThrownBy(() -> AuditLog.builder()
            .administradorId("admin-1")
            .afectado("usuario-1")
            .motivo("Prueba")
            .ipOrigen("127.0.0.1")
            .build()
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deberiaRechazarConstruccionSiFaltaAfectado() {
        assertThatThrownBy(() -> AuditLog.builder()
            .tipoAccion(AuditActionType.CREACION)
            .administradorId("admin-1")
            .motivo("Prueba")
            .ipOrigen("127.0.0.1")
            .build()
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deberiaRechazarConstruccionSiFaltaMotivo() {
        assertThatThrownBy(() -> AuditLog.builder()
            .tipoAccion(AuditActionType.CREACION)
            .administradorId("admin-1")
            .afectado("usuario-1")
            .ipOrigen("127.0.0.1")
            .build()
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deberiaRechazarConstruccionSiFaltaIpOrigen() {
        assertThatThrownBy(() -> AuditLog.builder()
            .tipoAccion(AuditActionType.CREACION)
            .administradorId("admin-1")
            .afectado("usuario-1")
            .motivo("Prueba")
            .build()
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deberiaConstruirCorrectamenteConTodosLosCamposObligatorios() {
        AuditLog resultado = AuditLog.builder()
            .tipoAccion(AuditActionType.CREACION)
            .administradorId("admin-1")
            .afectado("usuario-1")
            .motivo("Prueba")
            .ipOrigen("127.0.0.1")
            .build();

        // Si llegamos aquí sin excepción, el Builder aceptó los datos
        // completos correctamente — confirmamos que el objeto quedó
        // bien armado.
        org.assertj.core.api.Assertions.assertThat(resultado.getAdministradorId())
            .isEqualTo("admin-1");
    }
}
