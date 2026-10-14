package com.nexusbattles.ms_cumplimiento.auditoria.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogControllerConValoresTest {

    @Test
    @DisplayName("afectado: sin el parametro ninguno; sin vacios, recortados y sin repetir")
    void conValores() {
        assertThat(AuditLogController.conValores(null)).isEmpty();
        assertThat(AuditLogController.conValores(Arrays.asList(" 42 ", "", null, "42", "uid")))
            .containsExactly("42", "uid");
    }
}
