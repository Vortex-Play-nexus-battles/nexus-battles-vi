package com.nexusbattles.ms_cumplimiento.auditoria.controller;

import com.nexusbattles.ms_cumplimiento.auditoria.dto.AuditLogResponse;
import com.nexusbattles.ms_cumplimiento.auditoria.dto.RegistrarAuditoriaRequest;
import com.nexusbattles.ms_cumplimiento.auditoria.model.AuditActionType;
import com.nexusbattles.ms_cumplimiento.auditoria.model.AuditLog;
import com.nexusbattles.ms_cumplimiento.auditoria.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pruebas unitarias puras: no levantan Spring ni ninguna base de datos.
// El AuditLogService se reemplaza por un mock, así que estas pruebas
// corren en milisegundos, a diferencia de las de AuditLogServiceTest
// (que sí necesitan Postgres real vía Testcontainers).
@ExtendWith(MockitoExtension.class)
class AuditLogControllerTest {

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AuditLogController controller;

    @Test
    void consultarDeberiaMapearCadaAuditLogASuResponse() {
        // Arrange: un AuditLog "de mentira", nunca toca la base de datos real
        AuditLog entrada = AuditLog.builder()
            .tipoAccion(AuditActionType.CAMBIO_ROL)
            .administradorId("admin-1")
            .afectado("usuario-1")
            .motivo("Prueba")
            .ipOrigen("127.0.0.1")
            .build();

        Pageable pageable = PageRequest.of(0, 10);
        Page<AuditLog> paginaSimulada = new PageImpl<>(List.of(entrada), pageable, 1);

        when(auditLogService.consultar(
            eq("admin-1"), eq(AuditActionType.CAMBIO_ROL), any(), any(), eq(pageable)
        )).thenReturn(paginaSimulada);

        // Act
        Page<AuditLogResponse> resultado = controller.consultar(
            "admin-1", AuditActionType.CAMBIO_ROL, null, null, pageable
        );

        // Assert: el controller debe convertir cada AuditLog a su DTO
        // AuditLogResponse, sin perder datos ni cambiar la cantidad total.
        assertThat(resultado.getTotalElements()).isEqualTo(1);
        assertThat(resultado.getContent().get(0).administrador()).isEqualTo("admin-1");
        assertThat(resultado.getContent().get(0).tipoAccion()).isEqualTo("CAMBIO_ROL");
    }

    @Test
    void registrarEventoDeberiaRetornarElRegistroCreado() {
        // Arrange
        RegistrarAuditoriaRequest solicitud = new RegistrarAuditoriaRequest(
            "SUSPENSION", "admin-2", "usuario-2", "ACTIVO", "SUSPENDIDO",
            "Motivo de prueba", "10.0.0.1"
        );

        AuditLog registrado = AuditLog.builder()
            .tipoAccion(AuditActionType.SUSPENSION)
            .administradorId("admin-2")
            .afectado("usuario-2")
            .valorAnterior("ACTIVO")
            .valorNuevo("SUSPENDIDO")
            .motivo("Motivo de prueba")
            .ipOrigen("10.0.0.1")
            .build();

        when(auditLogService.registrarDesdeSolicitud(solicitud)).thenReturn(registrado);

        // Act
        AuditLogResponse respuesta = controller.registrarEvento(solicitud);

        // Assert: la respuesta debe reflejar exactamente lo que el
        // servicio devolvió, y el servicio debe haber sido llamado
        // con la solicitud original (sin transformarla antes de tiempo).
        assertThat(respuesta.administrador()).isEqualTo("admin-2");
        assertThat(respuesta.tipoAccion()).isEqualTo("SUSPENSION");
        verify(auditLogService).registrarDesdeSolicitud(solicitud);
    }
}
