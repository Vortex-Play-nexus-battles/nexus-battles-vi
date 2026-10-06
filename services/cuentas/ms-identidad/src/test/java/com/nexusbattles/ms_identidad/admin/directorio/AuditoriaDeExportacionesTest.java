package com.nexusbattles.ms_identidad.admin.directorio;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nexusbattles.ms_identidad.auditoria.client.AuditoriaClient;
import com.nexusbattles.ms_identidad.onboarding.ServidorFalso;
import com.nexusbattles.ms_identidad.onboarding.traza.Traza;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * HU-USR-009 (#562) — la auditoria de cada exportacion del directorio.
 *
 * <p>Decision del PO del 5-oct: se registra quien, cuando, con que filtros y
 * cuantas filas, nunca el contenido; y si ms-cumplimiento no responde, la
 * exportacion sigue y el fallo queda en la bitacora con su {@code traceId}.
 * Aqui se prueba con el cliente simulado (que forma tiene el evento) y con
 * el cliente HTTP de verdad contra un ms-cumplimiento falso, arriba y caido.
 */
@DisplayName("Auditoria de las exportaciones del directorio (HU-USR-009)")
class AuditoriaDeExportacionesTest {

    private static final String TRAZA = "0af7651916cd43dd8448eb211c80319c";
    private static final String UID = "6f1c2d3e-4a5b-4c6d-8e9f-0a1b2c3d4e5f";
    private static final String EVENTOS = "/api/v1/admin/auditoria/eventos";

    private ListAppender<ILoggingEvent> bitacoraDelServicio;
    private Logger registrador;

    @BeforeEach
    void escucharLaBitacora() {
        registrador = (Logger) LoggerFactory.getLogger(AuditoriaDeExportaciones.class);
        bitacoraDelServicio = new ListAppender<>();
        bitacoraDelServicio.start();
        registrador.addAppender(bitacoraDelServicio);
        Traza.abrir(TRAZA);
    }

    @AfterEach
    void soltar() {
        registrador.detachAppender(bitacoraDelServicio);
        Traza.cerrar();
    }

    private static FiltroDelDirectorio filtroCompleto() {
        return FiltroDelDirectorio.de("ana", true, "jugador", "activo", "2026-09-01", "2026-09-30");
    }

    private List<ILoggingEvent> avisos() {
        return bitacoraDelServicio.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
    }

    @Nested
    @DisplayName("la forma del evento")
    class FormaDelEvento {

        private final AuditoriaClient cliente = mock(AuditoriaClient.class);
        private final AuditoriaDeExportaciones auditoria = new AuditoriaDeExportaciones(cliente);

        @Test
        @DisplayName("tipo OTRO sobre el directorio, con quien, IP, filtros, filas, uid y traza; nada del archivo")
        void eventoCompleto() {
            boolean auditada = auditoria.registrar(filtroCompleto(), 42, "operadora", UID, "203.0.113.9");

            assertThat(auditada).isTrue();
            ArgumentCaptor<String> metadatos = ArgumentCaptor.forClass(String.class);
            verify(cliente).registrar(eq(AuditoriaDeExportaciones.TIPO), eq("operadora"),
                    eq(AuditoriaDeExportaciones.AFECTADO), isNull(), metadatos.capture(),
                    eq(AuditoriaDeExportaciones.MOTIVO), eq("203.0.113.9"));
            assertThat(metadatos.getValue()).isEqualTo("filas=42; buscar=«ana»; rol=JUGADOR; estado=ACTIVO; "
                    + "registradoDesde=2026-09-01; registradoHasta=2026-09-30; ocultarPruebas=true; "
                    + "administradorUid=" + UID + "; traceId=" + TRAZA);
            assertThat(AuditoriaDeExportaciones.TIPO).isEqualTo("OTRO");
            assertThat(AuditoriaDeExportaciones.MOTIVO).startsWith("EXPORTACION_JUGADORES").hasSizeLessThan(500);
            assertThat(avisos()).isEmpty();
        }

        @Test
        @DisplayName("sin filtros lo dice; sin administrador, uid o IP, valores que ms-cumplimiento acepta")
        void sinFiltrosYSinIdentidad() {
            auditoria.registrar(FiltroDelDirectorio.de(null, false, null, null, null, null), 0, " ", null, null);

            ArgumentCaptor<String> metadatos = ArgumentCaptor.forClass(String.class);
            verify(cliente).registrar(eq("OTRO"), eq(AuditoriaDeExportaciones.DESCONOCIDO),
                    eq(AuditoriaDeExportaciones.AFECTADO), isNull(), metadatos.capture(),
                    eq(AuditoriaDeExportaciones.MOTIVO), eq(AuditoriaDeExportaciones.DESCONOCIDA));
            assertThat(metadatos.getValue())
                    .isEqualTo("filas=0; sin filtros; administradorUid=DESCONOCIDO; traceId=" + TRAZA);
        }

        @Test
        @DisplayName("el texto buscado se guarda en una linea y recortado; sin traza abierta, lo dice")
        void textoRecortadoYSinTraza() {
            Traza.cerrar();
            String largo = "linea uno\nlinea dos " + "x".repeat(200);

            auditoria.registrar(FiltroDelDirectorio.de(largo, false, null, null, null, null), 1, "operadora", UID,
                    "10.0.0.1");

            ArgumentCaptor<String> metadatos = ArgumentCaptor.forClass(String.class);
            verify(cliente).registrar(anyString(), anyString(), anyString(), isNull(), metadatos.capture(),
                    anyString(), anyString());
            String buscado = metadatos.getValue().substring(metadatos.getValue().indexOf('«') + 1,
                    metadatos.getValue().indexOf('»'));
            assertThat(buscado).doesNotContain("\n").startsWith("linea uno linea dos ").endsWith("…")
                    .hasSize(AuditoriaDeExportaciones.LARGO_MAXIMO_DEL_TEXTO + 1);
            assertThat(metadatos.getValue()).endsWith("traceId=" + AuditoriaDeExportaciones.SIN_TRAZA);
        }

        @Test
        @DisplayName("si la auditoria falla: no lanza, devuelve false y lo deja en la bitacora con la traza")
        void falloSinLanzar() {
            doThrow(new IllegalStateException("el servicio de auditoría no respondió"))
                    .when(cliente).registrar(anyString(), anyString(), anyString(), any(), anyString(), anyString(),
                            anyString());

            boolean auditada = auditoria.registrar(filtroCompleto(), 42, "operadora", UID, "203.0.113.9");

            assertThat(auditada).isFalse();
            assertThat(avisos()).singleElement().satisfies(aviso -> assertThat(aviso.getFormattedMessage())
                    .startsWith("EXPORTACION_DIRECTORIO_SIN_AUDITAR")
                    .contains("administrador=operadora").contains("ip=203.0.113.9")
                    .contains("filas=42").contains("rol=JUGADOR").contains("traceId=" + TRAZA)
                    .contains("el servicio de auditoría no respondió"));
        }
    }

    @Nested
    @DisplayName("contra un ms-cumplimiento por HTTP")
    class PorHttp {

        private ServidorFalso cumplimiento;
        private AuditoriaDeExportaciones auditoria;

        @BeforeEach
        void montar() {
            cumplimiento = new ServidorFalso();
            AuditoriaClient cliente = new AuditoriaClient(RestClient.builder().build());
            ReflectionTestUtils.setField(cliente, "urlAuditoria", cumplimiento.url() + EVENTOS);
            auditoria = new AuditoriaDeExportaciones(cliente);
        }

        @AfterEach
        void desmontar() {
            cumplimiento.close();
        }

        @Test
        @DisplayName("arriba: llega un evento con los metadatos y sin ninguna fila del archivo")
        void arriba() {
            cumplimiento.responder("POST", EVENTOS, 201, "{}");

            boolean auditada = auditoria.registrar(filtroCompleto(), 2, "operadora", UID, "203.0.113.9");

            assertThat(auditada).isTrue();
            assertThat(cumplimiento.recibidas("POST", EVENTOS)).singleElement().satisfies(peticion ->
                    assertThat(peticion.cuerpo())
                            .contains("\"tipoAccion\":\"OTRO\"")
                            .contains("\"administradorId\":\"operadora\"")
                            .contains("\"afectado\":\"DIRECTORIO_DE_CUENTAS\"")
                            .contains("\"ipOrigen\":\"203.0.113.9\"")
                            .contains("filas=2").contains("traceId=" + TRAZA)
                            .contains("EXPORTACION_JUGADORES")
                            .doesNotContain("Apodo,Correo").doesNotContain("@"));
            assertThat(avisos()).isEmpty();
        }

        @Test
        @DisplayName("responde 503: la exportacion no se entera y queda el aviso")
        void respondeConError() {
            cumplimiento.responder("POST", EVENTOS, 503, "{\"title\":\"Bitacora de auditoria no disponible\"}");

            boolean auditada = auditoria.registrar(filtroCompleto(), 2, "operadora", UID, "203.0.113.9");

            assertThat(auditada).isFalse();
            assertThat(avisos()).singleElement().satisfies(aviso -> assertThat(aviso.getFormattedMessage())
                    .startsWith("EXPORTACION_DIRECTORIO_SIN_AUDITAR").contains("traceId=" + TRAZA));
        }

        @Test
        @DisplayName("caido (nadie escucha): tampoco lanza, y queda el aviso")
        void caido() {
            ServidorFalso apagado = new ServidorFalso();
            String direccion = apagado.url();
            apagado.close();
            AuditoriaClient cliente = new AuditoriaClient(RestClient.builder().build());
            ReflectionTestUtils.setField(cliente, "urlAuditoria", direccion + EVENTOS);

            boolean auditada = new AuditoriaDeExportaciones(cliente)
                    .registrar(filtroCompleto(), 2, "operadora", UID, "203.0.113.9");

            assertThat(auditada).isFalse();
            assertThat(avisos()).singleElement().satisfies(aviso -> assertThat(aviso.getFormattedMessage())
                    .startsWith("EXPORTACION_DIRECTORIO_SIN_AUDITAR").contains("filas=2"));
        }
    }
}
