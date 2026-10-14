package com.nexusbattles.plataforma.metricasplataforma.moderacion;

import com.nexusbattles.plataforma.metricasplataforma.seguridad.SeguridadAbiertaDePrueba;
import com.nexusbattles.plataforma.resiliencia.parametros.LectorDeParametros;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-MET-001 y §7.3.8: agregados de la fuente, umbrales solo si estan configurados, la frecuencia de reportes y lo
 * que no se pudo leer, por su causa y por su nombre.
 */
@WebMvcTest(controllers = ModeracionController.class)
// Rebanada con una cadena ABIERTA a proposito: lo que se prueba aqui es el
// comportamiento del endpoint. Que la observabilidad exija rol administrativo
// (HU-MET-001, #527) lo afirma SeguridadDeObservabilidadTest con la cadena real.
@Import({ModeracionControllerTest.Dobles.class, SeguridadAbiertaDePrueba.class})
class ModeracionControllerTest {

    private static final Instant AHORA = Instant.parse("2026-10-01T10:00:00Z");

    @TestConfiguration
    static class Dobles {
        @Bean
        Clock reloj() {
            return Clock.fixed(AHORA, ZoneOffset.UTC);
        }

        /** Sin catalogo: el de sanciones sale del respaldo (3, D-25) y el de reportes no tiene cifra. */
        @Bean
        UmbralesDeModeracion umbrales() {
            return new UmbralesDeModeracion(LectorDeParametros.soloRespaldo(), 3, null);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FuenteDeModeracion fuente;

    @MockitoBean
    private FuenteDeUsuarios usuarios;

    @MockitoBean
    private FuenteDeReportes reportes;

    private static FuenteDeReportes.Frecuencia frecuencia() {
        OffsetDateTime hasta = OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC);
        return new FuenteDeReportes.Frecuencia(hasta.minusDays(30), hasta, 7L,
                List.of(new FuenteDeReportes.PorDia("2026-09-28", 6L), new FuenteDeReportes.PorDia("2026-09-30", 1L)),
                Map.of("SPAM", 4L, "CONTENIDO_OFENSIVO", 1L, "ACOSO", 2L, "INFORMACION_FALSA", 0L,
                        "CONTENIDO_INAPROPIADO", 0L, "VIOLACION_DE_DERECHOS", 0L), 5L, 6L);
    }

    private static FuenteDeModeracion.Agregados agregados() {
        OffsetDateTime hasta = OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC);
        return new FuenteDeModeracion.Agregados(hasta.minusDays(30), hasta, 6,
                Map.of("ADVERTENCIA", 4L, "SUSPENSION", 2L, "BANEO", 0L),
                List.of(new FuenteDeModeracion.PorDia("2026-09-20", 1), new FuenteDeModeracion.PorDia("2026-09-28", 5)),
                Map.of("PENDIENTE", 1L, "MANTENIDA", 0L, "REDUCIDA", 0L, "REVERTIDA", 1L), 2, 1);
    }

    private static FuenteDeUsuarios.Indicadores indicadores() {
        return new FuenteDeUsuarios.Indicadores(12L,
                Map.of("ACTIVO", 8L, "PENDIENTE_VERIFICACION", 1L, "INACTIVO", 0L, "SUSPENDIDO", 2L, "BANEADO", 1L),
                new FuenteDeUsuarios.Registros("2026-09-01", "2026-10-01", 3L,
                        List.of(new FuenteDeUsuarios.DiaDeRegistro("2026-09-30", 1L),
                                new FuenteDeUsuarios.DiaDeRegistro("2026-10-01", 2L))),
                false, OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC));
    }

    @Test
    void publicaLosAgregadosLasAlertasPorUmbralYLoPendiente() throws Exception {
        given(fuente.consultar(any(), any())).willReturn(agregados());
        given(usuarios.consultar(any(), any(), any()))
                .willThrow(new FuenteDeUsuarios.NoDisponible("ms-identidad no responde"));
        given(reportes.consultar(any(), any(), any())).willReturn(frecuencia());
        mockMvc.perform(get("/api/v1/moderacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sanciones.total").value(6))
                .andExpect(jsonPath("$.sanciones.porTipo.ADVERTENCIA").value(4))
                .andExpect(jsonPath("$.sanciones.moderadoresActivos").value(2))
                .andExpect(jsonPath("$.alertasConfiguradas").value(true))
                .andExpect(jsonPath("$.umbralSancionesPorDia").value(3))
                .andExpect(jsonPath("$.umbralReportesPorDia").doesNotExist())
                .andExpect(jsonPath("$.alertas.length()").value(1))
                .andExpect(jsonPath("$.alertas[0]").value(org.hamcrest.Matchers.containsString("2026-09-28")))
                .andExpect(jsonPath("$.avisos[0].tipo").value("SANCIONES"))
                .andExpect(jsonPath("$.avisos[0].fecha").value("2026-09-28"))
                .andExpect(jsonPath("$.avisos[0].cantidad").value(5))
                .andExpect(jsonPath("$.avisos[0].umbral").value(3))
                .andExpect(jsonPath("$.reportes.total").value(7))
                .andExpect(jsonPath("$.reportes.porCategoria.ACOSO").value(2))
                .andExpect(jsonPath("$.pendientes.length()").value(1))
                .andExpect(jsonPath("$.noDisponibles[0].indicador").value("REGISTRO_DE_USUARIOS"))
                .andExpect(jsonPath("$.noDisponibles[0].causa").value("NO_RESPONDE"));
    }

    @Test
    void laFrecuenciaDeReportesSePideConElPeriodoYElTokenDeQuienMira() throws Exception {
        given(fuente.consultar(any(), any())).willReturn(agregados());
        given(usuarios.consultar(any(), any(), any())).willReturn(indicadores());
        given(reportes.consultar(any(), any(), any())).willReturn(frecuencia());

        mockMvc.perform(get("/api/v1/moderacion?desde=2026-09-01T00:00:00Z&hasta=2026-10-01T00:00:00Z")
                        .header("Authorization", "Bearer token-del-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportes.porDia[0].reportes").value(6))
                .andExpect(jsonPath("$.pendientes").isEmpty())
                .andExpect(jsonPath("$.noDisponibles").isEmpty());
        verify(reportes).consultar(OffsetDateTime.parse("2026-09-01T00:00:00Z"),
                OffsetDateTime.parse("2026-10-01T00:00:00Z"), "Bearer token-del-admin");
    }

    @Test
    void siComentariosNoDaLosReportesElTableroSigueYDiceLaCausa() throws Exception {
        given(fuente.consultar(any(), any())).willReturn(agregados());
        given(usuarios.consultar(any(), any(), any())).willReturn(indicadores());
        given(reportes.consultar(any(), any(), any())).willThrow(new FuenteDeReportes.NoDisponible(
                CausaDeIndisponibilidad.SIN_PERMISO, "comentarios nego el permiso"));

        mockMvc.perform(get("/api/v1/moderacion").header("Authorization", "Bearer de-servicio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sanciones.total").value(6))
                .andExpect(jsonPath("$.reportes").doesNotExist())
                .andExpect(jsonPath("$.pendientes[0]").value("frecuencia de reportes: comentarios nego el permiso"))
                .andExpect(jsonPath("$.noDisponibles[0].indicador").value("FRECUENCIA_DE_REPORTES"))
                .andExpect(jsonPath("$.noDisponibles[0].causa").value("SIN_PERMISO"));
    }

    @Test
    void publicaElRegistroDeUsuariosYReenviaElTokenDelAdministrador() throws Exception {
        given(fuente.consultar(any(), any())).willReturn(agregados());
        given(usuarios.consultar(eq("2026-09-01"), eq("2026-10-01"), eq("Bearer token-del-admin")))
                .willReturn(indicadores());

        given(reportes.consultar(any(), any(), any())).willReturn(frecuencia());

        mockMvc.perform(get("/api/v1/moderacion").header("Authorization", "Bearer token-del-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registroDeUsuarios.total").value(12))
                .andExpect(jsonPath("$.registroDeUsuarios.porEstado.SUSPENDIDO").value(2))
                .andExpect(jsonPath("$.registroDeUsuarios.registros.porDia[1].cuentas").value(2))
                .andExpect(jsonPath("$.pendientes").isEmpty());
        verify(usuarios).consultar("2026-09-01", "2026-10-01", "Bearer token-del-admin");
    }

    @Test
    void siIdentidadNoDaLosUsuariosElTableroSigueConLasSancionesYDiceElMotivo() throws Exception {
        given(fuente.consultar(any(), any())).willReturn(agregados());
        given(usuarios.consultar(any(), any(), any())).willThrow(new FuenteDeUsuarios.NoDisponible(
                CausaDeIndisponibilidad.SIN_PERMISO,
                "ms-identidad nego el permiso (se necesita un administrador con GESTIONAR_CUENTAS)"));
        given(reportes.consultar(any(), any(), any())).willReturn(frecuencia());

        mockMvc.perform(get("/api/v1/moderacion").header("Authorization", "Bearer de-servicio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sanciones.total").value(6))
                .andExpect(jsonPath("$.registroDeUsuarios").doesNotExist())
                .andExpect(jsonPath("$.pendientes[0]")
                        .value(org.hamcrest.Matchers.containsString("GESTIONAR_CUENTAS")))
                .andExpect(jsonPath("$.noDisponibles[0].causa").value("SIN_PERMISO"));
    }

    @Test
    void laFuenteCaidaEs503ConProblemDetailYElPeriodoInvalido400() throws Exception {
        given(fuente.consultar(any(), any())).willThrow(new FuenteDeModeracion.FuenteNoDisponible("no responde"));
        mockMvc.perform(get("/api/v1/moderacion"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/fuente-no-disponible"));
        mockMvc.perform(get("/api/v1/moderacion?desde=2026-10-02T00:00:00Z&hasta=2026-10-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/periodo-invalido"));
    }
}
