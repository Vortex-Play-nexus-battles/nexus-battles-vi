package com.nexusbattles.ms_cumplimiento.auditoria.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Los filtros que no filtran nada no se anaden a la consulta (contrato 1.2.0). */
class AuditLogSpecificationsTest {

    @Test
    @DisplayName("sin afectados utiles no hay filtro; con alguno, si")
    void conAfectado() {
        assertThat(AuditLogSpecifications.conAfectado(null)).isNull();
        assertThat(AuditLogSpecifications.conAfectado(List.of())).isNull();
        assertThat(AuditLogSpecifications.conAfectado(Arrays.asList(" ", null, ""))).isNull();
        assertThat(AuditLogSpecifications.conAfectado(List.of("uid-de-lyra", " 77 "))).isNotNull();
    }

    @Test
    @DisplayName("1.3.0: varios administradores; sin ninguno util, no hay filtro")
    void conAdministradores() {
        assertThat(AuditLogSpecifications.conAdministradores(null)).isNull();
        assertThat(AuditLogSpecifications.conAdministradores(Arrays.asList(" ", null))).isNull();
        assertThat(AuditLogSpecifications.conAdministradores(List.of("moderadora_qa", "5b2e1c4e"))).isNotNull();
    }

    @Test
    @DisplayName("los demas filtros vacios tampoco filtran")
    void filtrosVacios() {
        assertThat(AuditLogSpecifications.conAdministrador(" ")).isNull();
        assertThat(AuditLogSpecifications.conTipoAccion(null)).isNull();
        assertThat(AuditLogSpecifications.entreFechas(null, null)).isNull();
    }
}
