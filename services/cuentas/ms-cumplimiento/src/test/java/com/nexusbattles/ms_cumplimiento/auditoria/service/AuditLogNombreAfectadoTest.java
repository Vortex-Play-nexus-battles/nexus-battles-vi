package com.nexusbattles.ms_cumplimiento.auditoria.service;

import com.nexusbattles.ms_cumplimiento.auditoria.dto.AuditLogResponse;
import com.nexusbattles.ms_cumplimiento.auditoria.dto.RegistrarAuditoriaRequest;
import com.nexusbattles.ms_cumplimiento.auditoria.model.AuditActionType;
import com.nexusbattles.ms_cumplimiento.auditoria.model.AuditLog;
import com.nexusbattles.ms_cumplimiento.auditoria.repository.AuditLogRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SA-18 (contrato 1.4.0): el nombre de lo afectado en el momento de la acción,
 * los dos tipos nuevos y una exportación en PDF que no se cae por un carácter
 * que su fuente no dibuja.
 */
@DisplayName("Bitácora: nombre de lo afectado y tipos del catálogo (SA-18)")
class AuditLogNombreAfectadoTest {

    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final AuditLogService service = new AuditLogService(repository);

    private static AuditLog delCatalogo(String nombre, String motivo) {
        return AuditLog.builder()
            .tipoAccion(AuditActionType.REACTIVACION)
            .administradorId("8f14e45f-ceea-467f-a0e6-9ea7c2b1e7a1")
            .afectado("producto:550e8400-e29b-41d4-a716-446655440000")
            .nombreAfectado(nombre)
            .valorAnterior("Estado: Suspendido")
            .valorNuevo("Estado: Activo")
            .motivo(motivo)
            .ipOrigen("181.52.10.4")
            .referencia("productos:op-1")
            .build();
    }

    @Test
    @DisplayName("un evento con nombre lo guarda y la respuesta lo devuelve")
    void guardaElNombre() {
        when(repository.findByReferencia("productos:op-1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditLog entrada = service.registrarEvento(new RegistrarAuditoriaRequest("REVERSION",
            "8f14e45f-ceea-467f-a0e6-9ea7c2b1e7a1", "producto:550e8400-e29b-41d4-a716-446655440000",
            "Precio en créditos: 950", "Precio en créditos: 900", "Precio mal puesto", "181.52.10.4",
            Instant.parse("2026-10-14T15:00:00Z"), "productos:op-1", "  Espada solar  ")).entrada();

        assertThat(entrada.getTipoAccion()).isEqualTo(AuditActionType.REVERSION);
        assertThat(entrada.getNombreAfectado()).isEqualTo("Espada solar");
        assertThat(AuditLogResponse.from(entrada).nombreAfectado()).isEqualTo("Espada solar");
    }

    @Test
    @DisplayName("un nombre de más de 255 caracteres es un 400, no un 503")
    void nombreDemasiadoLargo() {
        RegistrarAuditoriaRequest evento = new RegistrarAuditoriaRequest("CREACION", "admin", "banner:b-1", null,
            "x", "Publicación de un banner", "1.1.1.1", null, "productos:op-2", "n".repeat(256));

        assertThatThrownBy(() -> service.registrarEvento(evento))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("nombreAfectado");
        verify(repository, never()).saveAndFlush(any(AuditLog.class));
    }

    @Test
    @DisplayName("sin nombre (la forma de 1.3.0) sigue entrando, con el nombre vacío")
    void sinNombre() {
        when(repository.saveAndFlush(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditLog entrada = service.registrarEvento(new RegistrarAuditoriaRequest("SANCION", "admin",
            "usuario:u-1", null, "Advertencia", "Spam", "1.1.1.1")).entrada();

        assertThat(entrada.getNombreAfectado()).isNull();
    }

    @Test
    @DisplayName("el CSV agrega nombre_afectado al final, sin mover las columnas de antes")
    void csvConNombre() {
        String csv = new String(AuditLogService.generarCsv(List.of(delCatalogo("Espada solar", "Vuelve a la tienda"))),
            StandardCharsets.UTF_8);

        String[] lineas = csv.split("\r\n");
        assertThat(lineas[0]).isEqualTo("﻿id,fecha_hora,ocurrido_en,administrador,tipo_accion,afectado,"
            + "valor_anterior,valor_nuevo,motivo,ip_origen,nombre_afectado");
        assertThat(lineas[1]).contains("\"REACTIVACION\"").endsWith(",\"181.52.10.4\",\"Espada solar\"");
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("el PDF sale aunque un nombre o un motivo traiga un emoji o una flecha (se dibuja ?)")
    void pdfConCaracteresSinGlifo() throws Exception {
        ReflectionTestUtils.setField(service, "maximoRegistrosPorExportacion", 10);
        when(repository.count(any(Specification.class))).thenReturn(1L);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(delCatalogo("Corona 👑 del Nexo", "Sube → baja"))));
        when(repository.saveAndFlush(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        byte[] pdf = service.exportar(new AuditLogService.FiltroDeAuditoria(List.of(), List.of(), null, null, null),
            AuditLogService.Formato.PDF, "superadmin", "10.0.0.1").contenido();

        try (PDDocument documento = Loader.loadPDF(pdf)) {
            String texto = new PDFTextStripper().getText(documento);
            assertThat(texto).contains("Corona ? del Nexo").contains("REACTIVACION");
        }
    }

    @Test
    @DisplayName("lo que la fuente sí dibuja (tildes, «», ·, …) se conserva")
    void conservaElLatino() {
        PDType1Font courier = new PDType1Font(Standard14Fonts.FontName.COURIER);

        assertThat(AuditLogService.paraLaFuente(courier, "«Espada» · Ñandú…")).isEqualTo("«Espada» · Ñandú…");
        assertThat(AuditLogService.paraLaFuente(courier, "a→b")).isEqualTo("a?b");
    }
}
