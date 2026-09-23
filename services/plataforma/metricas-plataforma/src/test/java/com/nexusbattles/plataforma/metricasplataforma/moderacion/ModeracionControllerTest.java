package com.nexusbattles.plataforma.metricasplataforma.moderacion;

import com.nexusbattles.plataforma.metricasplataforma.seguridad.SeguridadAbiertaDePrueba;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HU-MET-001: agregados de la fuente, umbral solo si esta configurado, pendientes por su nombre. */
@WebMvcTest(controllers = ModeracionController.class)
// Rebanada con una cadena ABIERTA a proposito: lo que se prueba aqui es el
// comportamiento del endpoint. Que la observabilidad exija rol administrativo
// (HU-MET-001, #527) lo afirma SeguridadDeObservabilidadTest con la cadena real.
@Import({ModeracionControllerTest.Dobles.class, SeguridadAbiertaDePrueba.class})
@TestPropertySource(properties = "metricas.moderacion.umbral-sanciones-por-dia=3")
class ModeracionControllerTest {

    private static final Instant AHORA = Instant.parse("2026-10-01T10:00:00Z");

    @TestConfiguration
    static class Dobles {
        @Bean
        Clock reloj() {
            return Clock.fixed(AHORA, ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FuenteDeModeracion fuente;

    private static FuenteDeModeracion.Agregados agregados() {
        OffsetDateTime hasta = OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC);
        return new FuenteDeModeracion.Agregados(hasta.minusDays(30), hasta, 6,
                Map.of("ADVERTENCIA", 4L, "SUSPENSION", 2L, "BANEO", 0L),
                List.of(new FuenteDeModeracion.PorDia("2026-09-20", 1), new FuenteDeModeracion.PorDia("2026-09-28", 5)),
                Map.of("PENDIENTE", 1L, "MANTENIDA", 0L, "REDUCIDA", 0L, "REVERTIDA", 1L), 2, 1);
    }

    @Test
    void publicaLosAgregadosLasAlertasPorUmbralYLoPendiente() throws Exception {
        given(fuente.consultar(any(), any())).willReturn(agregados());
        mockMvc.perform(get("/api/v1/moderacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sanciones.total").value(6))
                .andExpect(jsonPath("$.sanciones.porTipo.ADVERTENCIA").value(4))
                .andExpect(jsonPath("$.sanciones.moderadoresActivos").value(2))
                .andExpect(jsonPath("$.alertasConfiguradas").value(true))
                .andExpect(jsonPath("$.umbralSancionesPorDia").value(3))
                .andExpect(jsonPath("$.alertas.length()").value(1))
                .andExpect(jsonPath("$.alertas[0]").value(org.hamcrest.Matchers.containsString("2026-09-28")))
                .andExpect(jsonPath("$.pendientes.length()").value(2));
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
