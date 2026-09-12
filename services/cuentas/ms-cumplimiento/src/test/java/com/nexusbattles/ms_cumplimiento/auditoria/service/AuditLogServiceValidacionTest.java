package com.nexusbattles.ms_cumplimiento.auditoria.service;

import com.nexusbattles.ms_cumplimiento.auditoria.dto.RegistrarAuditoriaRequest;
import com.nexusbattles.ms_cumplimiento.auditoria.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

// Prueba unitaria pura con Mockito: el repositorio se mockea porque
// esta prueba no necesita tocar ninguna base de datos — solo valida
// que un tipoAccion inválido (texto que no existe en el enum
// AuditActionType) se rechace ANTES de intentar guardar nada.
@ExtendWith(MockitoExtension.class)
class AuditLogServiceValidacionTest {

    @Mock
    private AuditLogRepository repository;

    @InjectMocks
    private AuditLogService service;

    @Test
    void registrarDesdeSolicitudDeberiaRechazarTipoAccionInvalido() {
        RegistrarAuditoriaRequest solicitudConTipoInvalido = new RegistrarAuditoriaRequest(
            "TIPO_QUE_NO_EXISTE",
            "admin-1",
            "usuario-1",
            null,
            null,
            "Motivo de prueba",
            "127.0.0.1"
        );

        assertThatThrownBy(() -> service.registrarDesdeSolicitud(solicitudConTipoInvalido))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("TIPO_QUE_NO_EXISTE")
            .hasMessageContaining("Valores permitidos");

        // Confirmamos que, al fallar la validación, ni siquiera se
        // intentó tocar el repositorio (no debe haber ningún guardado
        // a medias).
        verifyNoInteractions(repository);
    }
}
