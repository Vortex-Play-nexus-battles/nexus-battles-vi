package com.nexusbattles.ms_cumplimiento.auditoria.service;

import com.nexusbattles.ms_cumplimiento.auditoria.dto.RegistrarAuditoriaRequest;
import com.nexusbattles.ms_cumplimiento.auditoria.model.AuditActionType;
import com.nexusbattles.ms_cumplimiento.auditoria.model.AuditLog;
import com.nexusbattles.ms_cumplimiento.auditoria.repository.AuditLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SA-5 (contrato 1.3.0): los eventos de los servicios con cola propia y la
 * exportacion en CSV. El repositorio se simula: se prueba la decision del
 * servicio, no la base (la IT de filtros corre contra PostgreSQL).
 */
class AuditLogEventosTest {

    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final AuditLogService service = new AuditLogService(repository);

    private static RegistrarAuditoriaRequest evento(String referencia, Instant ocurridoEn) {
        return new RegistrarAuditoriaRequest("SANCION", "5b2e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55",
            "usuario:7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55", null, "Advertencia · Spam o publicidad no autorizada",
            "Mensajes repetidos en el chat general", "203.0.113.9", ocurridoEn, referencia);
    }

    private static AuditLog guardada(String referencia) {
        return AuditLog.builder()
            .tipoAccion(AuditActionType.SANCION)
            .administradorId("5b2e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55")
            .afectado("usuario:7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55")
            .motivo("Mensajes repetidos en el chat general")
            .ipOrigen("203.0.113.9")
            .referencia(referencia)
            .build();
    }

    @Nested
    @DisplayName("un evento con momento y referencia")
    class Registrar {

        @Test
        @DisplayName("se guarda con la hora de la accion y su referencia; fechaHora la pone este servicio")
        void guardaMomentoYReferencia() {
            Instant hace = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
            when(repository.findByReferencia("sancion-1-emision")).thenReturn(Optional.empty());
            when(repository.saveAndFlush(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

            AuditLogService.ResultadoDeRegistro resultado = service.registrarEvento(evento("sancion-1-emision", hace));

            assertThat(resultado.nueva()).isTrue();
            assertThat(resultado.entrada().getOcurridoEn()).isEqualTo(hace);
            assertThat(resultado.entrada().getReferencia()).isEqualTo("sancion-1-emision");
            assertThat(resultado.entrada().getFechaHora()).isAfter(hace);
            assertThat(resultado.entrada().getTipoAccion()).isEqualTo(AuditActionType.SANCION);
        }

        @Test
        @DisplayName("una referencia ya registrada devuelve la entrada que habia, sin guardar otra")
        void referenciaRepetida() {
            AuditLog previa = guardada("sancion-1-emision");
            when(repository.findByReferencia("sancion-1-emision")).thenReturn(Optional.of(previa));

            AuditLogService.ResultadoDeRegistro resultado = service.registrarEvento(evento("sancion-1-emision", null));

            assertThat(resultado.nueva()).isFalse();
            assertThat(resultado.entrada()).isSameAs(previa);
            verify(repository, never()).saveAndFlush(any(AuditLog.class));
        }

        @Test
        @DisplayName("dos entregas a la vez: la segunda choca con el indice unico y devuelve la de la primera")
        void carreraDeEntregas() {
            AuditLog ganadora = guardada("sancion-1-emision");
            when(repository.findByReferencia("sancion-1-emision"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(ganadora));
            when(repository.saveAndFlush(any(AuditLog.class)))
                .thenThrow(new DataIntegrityViolationException("uq_audit_log_referencia"));

            AuditLogService.ResultadoDeRegistro resultado = service.registrarEvento(evento("sancion-1-emision", null));

            assertThat(resultado.nueva()).isFalse();
            assertThat(resultado.entrada()).isSameAs(ganadora);
        }

        @Test
        @DisplayName("sin referencia, siempre una entrada nueva (la forma anterior a 1.3.0)")
        void sinReferencia() {
            when(repository.saveAndFlush(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

            AuditLogService.ResultadoDeRegistro resultado = service.registrarEvento(evento("  ", null));

            assertThat(resultado.nueva()).isTrue();
            assertThat(resultado.entrada().getReferencia()).isNull();
            verify(repository, never()).findByReferencia(any());
        }
    }

    @Nested
    @DisplayName("lo que no se puede registrar es 400, no 503")
    class Validacion {

        @Test
        @DisplayName("faltan campos: el mensaje los nombra y no se toca la base")
        void faltanCampos() {
            RegistrarAuditoriaRequest incompleto = new RegistrarAuditoriaRequest("SANCION", " ", "usuario:1",
                null, null, null, "10.0.0.1");

            assertThatThrownBy(() -> service.registrarEvento(incompleto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("administradorId")
                .hasMessageContaining("motivo")
                .hasMessageNotContaining("afectado");
            verify(repository, never()).saveAndFlush(any(AuditLog.class));
        }

        @Test
        @DisplayName("un motivo mas largo que su columna se rechaza, no se recorta en silencio")
        void motivoDemasiadoLargo() {
            RegistrarAuditoriaRequest largo = new RegistrarAuditoriaRequest("SANCION", "admin", "usuario:1",
                null, null, "x".repeat(501), "10.0.0.1");

            assertThatThrownBy(() -> service.registrarEvento(largo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motivo supera los 500 caracteres");
        }

        @Test
        @DisplayName("una referencia de mas de 120 caracteres se rechaza")
        void referenciaDemasiadoLarga() {
            assertThatThrownBy(() -> service.registrarEvento(evento("r".repeat(121), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("referencia");
        }

        @Test
        @DisplayName("una accion no ocurre en el futuro; dentro del desfase de reloj, si")
        void momentoEnElFuturo() {
            assertThatThrownBy(() -> service.registrarEvento(evento(null, Instant.now().plus(1, ChronoUnit.HOURS))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ocurridoEn");

            when(repository.saveAndFlush(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
            assertThat(service.registrarEvento(evento(null, Instant.now().plus(1, ChronoUnit.MINUTES))).nueva())
                .isTrue();
        }

        @Test
        @DisplayName("un tipo nulo es 400 con la lista de valores")
        void tipoNulo() {
            RegistrarAuditoriaRequest sinTipo = new RegistrarAuditoriaRequest(null, "admin", "usuario:1",
                null, null, "motivo", "10.0.0.1");

            assertThatThrownBy(() -> service.registrarEvento(sinTipo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Valores permitidos");
        }
    }

    @Nested
    @DisplayName("exportar en CSV")
    class Csv {

        @Test
        @DisplayName("BOM, cabecera fija, todas las celdas entre comillas y las comillas internas dobladas")
        void forma() {
            AuditLog entrada = AuditLog.builder()
                .tipoAccion(AuditActionType.ACTUALIZACION)
                .administradorId("superadmin_qa")
                .afectado("parametro:metricas.umbral-sanciones-por-dia")
                .valorAnterior("3")
                .valorNuevo("4")
                .motivo("Ajuste \"temporal\", acordado con el PO")
                .ipOrigen("203.0.113.9")
                .build();

            String csv = new String(AuditLogService.generarCsv(List.of(entrada)), StandardCharsets.UTF_8);

            assertThat(csv).startsWith("﻿id,fecha_hora,ocurrido_en,administrador,tipo_accion,afectado,"
                + "valor_anterior,valor_nuevo,motivo,ip_origen\r\n");
            assertThat(csv).contains("\"superadmin_qa\",\"ACTUALIZACION\",\"parametro:metricas.umbral-sanciones-por-dia\","
                + "\"3\",\"4\",\"Ajuste \"\"temporal\"\", acordado con el PO\",\"203.0.113.9\"\r\n");
        }

        @Test
        @DisplayName("lo que empieza como formula se neutraliza con un apostrofo")
        void inyeccionDeFormulas() {
            assertThat(AuditLogService.celda("=HYPERLINK(\"http://x\")")).isEqualTo("\"'=HYPERLINK(\"\"http://x\"\")\"");
            assertThat(AuditLogService.celda("+57 300")).isEqualTo("\"'+57 300\"");
            assertThat(AuditLogService.celda("-1")).isEqualTo("\"'-1\"");
            assertThat(AuditLogService.celda("@SUMA(A1)")).isEqualTo("\"'@SUMA(A1)\"");
            assertThat(AuditLogService.celda("Spam")).isEqualTo("\"Spam\"");
            assertThat(AuditLogService.celda(null)).isEmpty();
        }

        @Test
        @DisplayName("exportar en CSV registra la exportacion con su formato, como el PDF")
        @SuppressWarnings("unchecked")
        void registraLaExportacion() {
            ReflectionTestUtils.setField(service, "maximoRegistrosPorExportacion", 10);
            when(repository.count(any(Specification.class))).thenReturn(1L);
            Page<AuditLog> pagina = new PageImpl<>(List.of(guardada(null)));
            when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pagina);
            when(repository.saveAndFlush(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

            AuditLogService.Exportacion exportacion = service.exportar(
                new AuditLogService.FiltroDeAuditoria(List.of("superadmin_qa"), List.of(), null, null, null),
                AuditLogService.Formato.CSV, "superadmin_qa", "203.0.113.9");

            assertThat(exportacion.formato()).isEqualTo(AuditLogService.Formato.CSV);
            assertThat(new String(exportacion.contenido(), StandardCharsets.UTF_8)).contains("\"203.0.113.9\"");
            verify(repository).saveAndFlush(argThat(e -> e.getTipoAccion() == AuditActionType.EXPORTACION
                && e.getMotivo().equals("Exportación del registro de auditoría en CSV")
                && "Exportado 1 registro".equals(e.getValorNuevo())));
        }

        @Test
        @DisplayName("formato: ausente es PDF, sin distinguir mayusculas; otro valor es 400")
        void formato() {
            assertThat(AuditLogService.Formato.de(null)).isEqualTo(AuditLogService.Formato.PDF);
            assertThat(AuditLogService.Formato.de(" CSV ")).isEqualTo(AuditLogService.Formato.CSV);
            assertThatThrownBy(() -> AuditLogService.Formato.de("xlsx"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pdf, csv");
        }
    }
    @Nested
    @DisplayName("una exportacion")
    class ExportacionComoValor {

        @Test
        @DisplayName("se compara por su contenido y su formato, y su texto no vuelca los bytes")
        void comparaPorContenido() {
            byte[] bytes = "a,b".getBytes(StandardCharsets.UTF_8);
            AuditLogService.Exportacion una = new AuditLogService.Exportacion(bytes, AuditLogService.Formato.CSV);
            AuditLogService.Exportacion igual = new AuditLogService.Exportacion(
                    "a,b".getBytes(StandardCharsets.UTF_8), AuditLogService.Formato.CSV);
            AuditLogService.Exportacion otroFormato = new AuditLogService.Exportacion(bytes, AuditLogService.Formato.PDF);
            AuditLogService.Exportacion otroContenido = new AuditLogService.Exportacion(
                    "a,c".getBytes(StandardCharsets.UTF_8), AuditLogService.Formato.CSV);

            assertThat(una).isEqualTo(igual).hasSameHashCodeAs(igual);
            assertThat(una).isNotEqualTo(otroFormato).isNotEqualTo(otroContenido).isNotEqualTo(null);
            assertThat(una.toString()).isEqualTo("Exportacion[formato=CSV, bytes=3]");
            assertThat(new AuditLogService.Exportacion(null, null).toString())
                    .isEqualTo("Exportacion[formato=null, bytes=0]");
        }
    }
}
