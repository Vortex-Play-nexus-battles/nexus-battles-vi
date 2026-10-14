package com.nexusbattles.ms_cumplimiento.auditoria.catalogo;

import com.nexusbattles.ms_cumplimiento.auditoria.dto.RegistrarAuditoriaRequest;
import com.nexusbattles.ms_cumplimiento.auditoria.exception.AuditWriteException;
import com.nexusbattles.ms_cumplimiento.auditoria.model.AuditLog;
import com.nexusbattles.ms_cumplimiento.auditoria.repository.AuditLogRepository;
import com.nexusbattles.ms_cumplimiento.auditoria.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Bitácora: importación de las operaciones del catálogo (SA-18)")
class ImportadorDelCatalogoTest {

    private static final String ADMIN = "8f14e45f-ceea-467f-a0e6-9ea7c2b1e7a1";
    private static final Duration SOLAPE = Duration.ofMinutes(2);

    private final ClienteDelCatalogo cliente = mock(ClienteDelCatalogo.class);
    private final AuditLogService bitacora = mock(AuditLogService.class);
    private final AuditLogRepository repositorio = mock(AuditLogRepository.class);
    private final ImportadorDelCatalogo importador =
        new ImportadorDelCatalogo(cliente, bitacora, repositorio, SOLAPE, 100, 3);

    @BeforeEach
    void cadaEventoEsNuevo() {
        when(bitacora.registrarEvento(any(RegistrarAuditoriaRequest.class)))
            .thenReturn(new AuditLogService.ResultadoDeRegistro(mock(AuditLog.class), true));
    }

    private static OperacionDelCatalogo operacion(String id, String instante, String tipo, String objeto,
                                                  String motivo, String ip) {
        return new OperacionDelCatalogo(id, Instant.parse(instante), ADMIN, tipo, objeto, "obj-" + id,
            "Espada solar", "Estado: Activo", "Estado: Suspendido", motivo, "Suspensión del producto", ip);
    }

    private static LoteDelCatalogo lote(String hasta, boolean completo, OperacionDelCatalogo... operaciones) {
        return new LoteDelCatalogo(Instant.parse(hasta), completo, List.of(operaciones));
    }

    @Nested
    @DisplayName("cómo entra cada operación")
    class Traduccion {

        @Test
        @DisplayName("con su tipo, lo afectado y su nombre, su momento, su IP, su motivo y su referencia")
        void completa() {
            RegistrarAuditoriaRequest evento = ImportadorDelCatalogo.aEvento(operacion("op-1",
                "2026-10-14T15:00:00.250Z", "SUSPENSION_PRODUCTO", "PRODUCTO", "Balance roto", "181.52.10.4"));

            assertThat(evento.tipoAccion()).isEqualTo("SUSPENSION");
            assertThat(evento.administradorId()).isEqualTo(ADMIN);
            assertThat(evento.afectado()).isEqualTo("producto:obj-op-1");
            assertThat(evento.nombreAfectado()).isEqualTo("Espada solar");
            assertThat(evento.valorAnterior()).isEqualTo("Estado: Activo");
            assertThat(evento.valorNuevo()).isEqualTo("Estado: Suspendido");
            assertThat(evento.motivo()).isEqualTo("Balance roto");
            assertThat(evento.ipOrigen()).isEqualTo("181.52.10.4");
            assertThat(evento.ocurridoEn()).isEqualTo(Instant.parse("2026-10-14T15:00:00.250Z"));
            assertThat(evento.referencia()).isEqualTo("productos:op-1");
        }

        @Test
        @DisplayName("sin motivo escrito, el motivo dice qué se hizo; sin IP, «no registrada»")
        void sinMotivoNiIp() {
            RegistrarAuditoriaRequest evento = ImportadorDelCatalogo.aEvento(operacion("op-2",
                "2026-10-14T15:00:00Z", "ALTA_BANNER", "BANNER", "  ", null));

            assertThat(evento.tipoAccion()).isEqualTo("CREACION");
            assertThat(evento.afectado()).isEqualTo("banner:obj-op-2");
            assertThat(evento.motivo()).isEqualTo("Suspensión del producto");
            assertThat(evento.ipOrigen()).isEqualTo("no registrada");
        }

        @Test
        @DisplayName("cada tipo del catálogo tiene el suyo; uno desconocido entra como OTRO y no se pierde")
        void tipos() {
            assertThat(tipo("REACTIVACION_PRODUCTO")).isEqualTo("REACTIVACION");
            assertThat(tipo("REVERSION_PRODUCTO")).isEqualTo("REVERSION");
            assertThat(tipo("CAMBIO_DE_BALANCE")).isEqualTo("ACTUALIZACION");
            assertThat(tipo("IMPORTACION_DE_DISENO")).isEqualTo("ACTUALIZACION");
            assertThat(tipo("RETIRO_RECURSO")).isEqualTo("ELIMINACION_LOGICA");
            assertThat(tipo("RETIRO_BANNER")).isEqualTo("ELIMINACION_LOGICA");
            assertThat(tipo("ALTA_RECURSO")).isEqualTo("CREACION");
            assertThat(tipo("ALGO_NUEVO")).isEqualTo("OTRO");
            assertThat(ImportadorDelCatalogo.aEvento(operacion("op-3", "2026-10-14T15:00:00Z", "ALTA_RECURSO",
                "RECURSO_GRAFICO", null, "1.1.1.1")).afectado()).isEqualTo("recurso:obj-op-3");
            assertThat(ImportadorDelCatalogo.aEvento(operacion("op-4", "2026-10-14T15:00:00Z", "ALTA_RECURSO",
                "OTRA_COSA", null, "1.1.1.1")).afectado()).isEqualTo("catalogo:obj-op-4");
        }

        private String tipo(String delCatalogo) {
            return ImportadorDelCatalogo.aEvento(operacion("x", "2026-10-14T15:00:00Z", delCatalogo, "PRODUCTO",
                null, "1.1.1.1")).tipoAccion();
        }

        @Test
        @DisplayName("sin motivo ni descripción, «Operación del catálogo» (la bitácora exige motivo)")
        void sinNada() {
            RegistrarAuditoriaRequest evento = ImportadorDelCatalogo.aEvento(new OperacionDelCatalogo("op-5",
                Instant.parse("2026-10-14T15:00:00Z"), ADMIN, "ALTA_BANNER", "BANNER", "b", null, null, "x", null,
                null, "1.1.1.1"));

            assertThat(evento.motivo()).isEqualTo("Operación del catálogo");
        }
    }

    @Nested
    @DisplayName("las rondas")
    class Rondas {

        @Test
        @DisplayName("la primera, sin nada importado, pide desde la primera operación")
        void primeraRonda() {
            when(repositorio.ultimoMomentoConReferencia("productos:%")).thenReturn(null);
            when(cliente.consultar(null, 100)).thenReturn(lote("2026-10-14T15:00:00Z", true,
                operacion("op-1", "2026-10-14T14:00:00Z", "ALTA_PRODUCTO", "PRODUCTO", null, "1.1.1.1"),
                operacion("op-2", "2026-10-14T15:00:00Z", "ALTA_BANNER", "BANNER", null, "1.1.1.1")));

            assertThat(importador.importar()).isEqualTo(2);
            verify(bitacora, times(2)).registrarEvento(any(RegistrarAuditoriaRequest.class));
        }

        @Test
        @DisplayName("las siguientes piden desde la última importada menos el solape, y las repetidas no cuentan")
        void conSolape() {
            when(repositorio.ultimoMomentoConReferencia("productos:%"))
                .thenReturn(Instant.parse("2026-10-14T15:00:00Z"));
            when(cliente.consultar(Instant.parse("2026-10-14T14:58:00Z"), 100)).thenReturn(lote(
                "2026-10-14T15:01:00Z", true,
                operacion("op-2", "2026-10-14T15:00:00Z", "ALTA_BANNER", "BANNER", null, "1.1.1.1"),
                operacion("op-3", "2026-10-14T15:01:00Z", "RETIRO_BANNER", "BANNER", "Evento cancelado", "1.1.1.1")));
            when(bitacora.registrarEvento(any(RegistrarAuditoriaRequest.class)))
                .thenReturn(new AuditLogService.ResultadoDeRegistro(mock(AuditLog.class), false))
                .thenReturn(new AuditLogService.ResultadoDeRegistro(mock(AuditLog.class), true));

            assertThat(importador.importar()).isEqualTo(1);

            // La segunda ronda ya no relee la base: sigue desde lo último que vio.
            when(cliente.consultar(Instant.parse("2026-10-14T14:59:00Z"), 100))
                .thenReturn(lote("2026-10-14T14:59:00Z", true));
            assertThat(importador.importar()).isZero();
            verify(repositorio, times(1)).ultimoMomentoConReferencia("productos:%");
        }

        @Test
        @DisplayName("un lote incompleto pide el siguiente desde su hasta, sin pasar del máximo de lotes")
        void lotesIncompletos() {
            when(cliente.consultar(isNull(), eq(100))).thenReturn(lote("2026-10-14T10:00:00Z", false,
                operacion("a", "2026-10-14T10:00:00Z", "ALTA_PRODUCTO", "PRODUCTO", null, "1.1.1.1")));
            when(cliente.consultar(Instant.parse("2026-10-14T10:00:00Z"), 100)).thenReturn(lote(
                "2026-10-14T11:00:00Z", false,
                operacion("b", "2026-10-14T11:00:00Z", "ALTA_PRODUCTO", "PRODUCTO", null, "1.1.1.1")));
            when(cliente.consultar(Instant.parse("2026-10-14T11:00:00Z"), 100)).thenReturn(lote(
                "2026-10-14T12:00:00Z", false,
                operacion("c", "2026-10-14T12:00:00Z", "ALTA_PRODUCTO", "PRODUCTO", null, "1.1.1.1")));

            assertThat(importador.importar()).isEqualTo(3);
            verify(cliente, never()).consultar(Instant.parse("2026-10-14T12:00:00Z"), 100);
        }

        @Test
        @DisplayName("una operación que la bitácora no admite no detiene a las demás")
        void unaInvalida() {
            when(cliente.consultar(isNull(), eq(100))).thenReturn(lote("2026-10-14T10:00:00Z", true,
                operacion("mala", "2026-10-14T09:00:00Z", "ALTA_PRODUCTO", "PRODUCTO", null, "1.1.1.1"),
                operacion("buena", "2026-10-14T10:00:00Z", "ALTA_PRODUCTO", "PRODUCTO", null, "1.1.1.1")));
            when(bitacora.registrarEvento(any(RegistrarAuditoriaRequest.class)))
                .thenThrow(new IllegalArgumentException("Faltan campos obligatorios: administradorId"))
                .thenReturn(new AuditLogService.ResultadoDeRegistro(mock(AuditLog.class), true));

            assertThat(importador.importar()).isEqualTo(1);
            ArgumentCaptor<RegistrarAuditoriaRequest> eventos = ArgumentCaptor.forClass(RegistrarAuditoriaRequest.class);
            verify(bitacora, times(2)).registrarEvento(eventos.capture());
            assertThat(eventos.getAllValues()).extracting(RegistrarAuditoriaRequest::referencia)
                .containsExactly("productos:mala", "productos:buena");
        }

        @Test
        @DisplayName("si productos o el emisor no responden, la ronda programada no lanza y no escribe nada")
        void catalogoCaido() {
            when(cliente.consultar(isNull(), eq(100)))
                .thenThrow(new CatalogoNoDisponibleException("productos no responde"));

            assertThatCode(importador::importarProgramado).doesNotThrowAnyException();
            verify(bitacora, never()).registrarEvento(any(RegistrarAuditoriaRequest.class));
        }

        @Test
        @DisplayName("si la base de la bitácora falla, la ronda programada tampoco lanza: la siguiente reintenta")
        void baseCaida() {
            when(cliente.consultar(isNull(), eq(100))).thenReturn(lote("2026-10-14T10:00:00Z", true,
                operacion("a", "2026-10-14T10:00:00Z", "ALTA_PRODUCTO", "PRODUCTO", null, "1.1.1.1")));
            when(bitacora.registrarEvento(any(RegistrarAuditoriaRequest.class)))
                .thenThrow(new AuditWriteException("sin base", new IllegalStateException()));

            assertThatCode(importador::importarProgramado).doesNotThrowAnyException();

            // No avanzó: la ronda siguiente vuelve a pedir desde el principio.
            when(bitacora.registrarEvento(any(RegistrarAuditoriaRequest.class)))
                .thenReturn(new AuditLogService.ResultadoDeRegistro(mock(AuditLog.class), true));
            assertThat(importador.importar()).isEqualTo(1);
            verify(cliente, times(2)).consultar(isNull(), eq(100));
        }
    }
}
