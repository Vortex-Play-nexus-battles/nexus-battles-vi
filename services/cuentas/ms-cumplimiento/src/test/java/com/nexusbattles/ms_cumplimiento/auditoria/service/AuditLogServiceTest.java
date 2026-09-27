package com.nexusbattles.ms_cumplimiento.auditoria.service;

import com.nexusbattles.ms_cumplimiento.auditoria.exception.ExportacionExcedeMaximoException;
import com.nexusbattles.ms_cumplimiento.auditoria.model.AuditActionType;
import com.nexusbattles.ms_cumplimiento.auditoria.model.AuditLog;
import com.nexusbattles.ms_cumplimiento.auditoria.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prueba la frontera exacta del maximo de exportacion (HU-AUD-004, pedida
 * por Simon en la revision de #498: "total == maximo se exporta"). Esta
 * logica vive en AuditLogService, no en el controller -- por eso no la
 * cubre AuditLogControllerTest, que mockea el servicio entero.
 *
 * <p>El repositorio se mockea aqui (no hay base de datos real): se prueba
 * solo la decision de AuditLogService, con AuditLogRepository como unico
 * colaborador.
 */
class AuditLogServiceTest {

    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final AuditLogService service = new AuditLogService(repository);

    @BeforeEach
    void ponerMaximoDePrueba() {
        ReflectionTestUtils.setField(service, "maximoRegistrosPorExportacion", 2);
    }

    private static AuditLog registro() {
        return AuditLog.builder()
            .tipoAccion(AuditActionType.CAMBIO_ROL)
            .administradorId("admin-1")
            .afectado("usuario-1")
            .motivo("motivo de prueba")
            .ipOrigen("10.0.0.1")
            .build();
    }

    @SuppressWarnings("unchecked")
    private void simularConteo(long total) {
        when(repository.count(any(Specification.class))).thenReturn(total);
    }

    @SuppressWarnings("unchecked")
    private void simularPagina(List<AuditLog> contenido) {
        Page<AuditLog> pagina = new PageImpl<>(contenido);
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pagina);
    }

    @Test
    @DisplayName("total == maximo: exporta, no rechaza")
    void totalIgualAlMaximo_exporta() {
        simularConteo(2);
        simularPagina(List.of(registro(), registro()));
        when(repository.saveAndFlush(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        byte[] pdf = service.exportarPdf(null, null, null, null, "root", "10.0.0.1");

        assertTrue(pdf.length > 0, "el PDF generado no deberia estar vacio");
        verify(repository, times(1)).saveAndFlush(any(AuditLog.class));
    }

    @Test
    @DisplayName("total > maximo: rechaza con las cifras exactas, sin generar el PDF")
    void totalSuperaElMaximo_rechaza() {
        simularConteo(3);
        when(repository.saveAndFlush(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        ExportacionExcedeMaximoException error = assertThrows(ExportacionExcedeMaximoException.class,
            () -> service.exportarPdf(null, null, null, null, "root", "10.0.0.1"));

        assertEquals(3L, error.getTotalEncontrado());
        assertEquals(2, error.getMaximoPermitido());
        verify(repository, times(1)).saveAndFlush(any(AuditLog.class));
        verify(repository, org.mockito.Mockito.never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("total > maximo por poco (maximo + 1): tambien rechaza")
    void totalUnoMasQueElMaximo_rechazaIgual() {
        simularConteo(3);

        assertThrows(ExportacionExcedeMaximoException.class,
            () -> service.exportarPdf(null, null, null, null, "root", "10.0.0.1"));
    }
}
