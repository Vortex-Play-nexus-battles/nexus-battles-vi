package com.nexusbattles.plataforma.metricasplataforma.disponibilidad;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HU-DIS-001 — lo que ve quien consulta la disponibilidad.
 *
 * <p>Verifica el contrato publicado en contracts/openapi/metricas-plataforma.yaml:
 * el estado en vivo (CP-01) y el informe del periodo (CP-02).
 */
@WebMvcTest(controllers = DisponibilidadController.class)
class DisponibilidadControllerTest {

    private static final Instant DESDE = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant HASTA = Instant.parse("2026-09-08T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MonitorDeDisponibilidad monitor;

    @Test
    void elEstadoReportaCadaServicioDelBloqueConSuUltimaComprobacion() throws Exception {
        given(monitor.estadoActual())
                .willReturn(List.of(
                        Comprobacion.disponible("correo", DESDE),
                        Comprobacion.caido("salas-partidas", DESDE, "connection refused")));

        mockMvc.perform(get("/api/v1/disponibilidad"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servicios.length()").value(2))
                .andExpect(jsonPath("$.servicios[0].servicio").value("correo"))
                .andExpect(jsonPath("$.servicios[0].estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$.servicios[1].estado").value("INDISPONIBLE"))
                .andExpect(jsonPath("$.servicios[1].detalle").value("connection refused"));
    }

    @Test
    void elInformeTraeTiempoDisponibleInterrupcionesYSiCumpleElUmbral() throws Exception {
        RegistroDeDisponibilidad registro = new RegistroDeDisponibilidad();
        registro.registrar(Comprobacion.caido("salas-partidas", DESDE, "caida"));
        registro.registrar(
                Comprobacion.disponible("salas-partidas", DESDE.plus(Duration.ofHours(1))));

        given(monitor.informe(any(), any()))
                .willReturn(registro.informe(List.of("salas-partidas"), DESDE, HASTA, 99.95));

        mockMvc.perform(get("/api/v1/disponibilidad/informe")
                        .param("desde", "2026-09-01T00:00:00Z")
                        .param("hasta", "2026-09-08T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodoMinutos").value(10080))
                .andExpect(jsonPath("$.umbral").value(99.95))
                .andExpect(jsonPath("$.cumpleElUmbral").value(false))
                .andExpect(jsonPath("$.servicios[0].disponibleMinutos").value(10020))
                .andExpect(jsonPath("$.servicios[0].indisponibleMinutos").value(60))
                .andExpect(jsonPath("$.servicios[0].interrupciones.length()").value(1))
                .andExpect(jsonPath("$.servicios[0].interrupciones[0].detalle").value("caida"));
    }

    @Test
    void sinFechasElInformeEsElDeLosUltimosTreintaDias() throws Exception {
        given(monitor.informeMensual())
                .willReturn(new RegistroDeDisponibilidad()
                        .informe(List.of("correo"), DESDE, HASTA, 99.95));

        mockMvc.perform(get("/api/v1/disponibilidad/informe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cumpleElUmbral").value(true))
                .andExpect(jsonPath("$.porcentajeDelBloque").value(100.0));
    }

    @Test
    void unPeriodoAlRevesSaleEnFormatoProblemDetails() throws Exception {
        // Regla 4 de plataforma: formato de error identico en los 20 modulos.
        given(monitor.informe(any(), any()))
                .willThrow(new IllegalArgumentException("el fin del periodo debe ser posterior a su inicio"));

        mockMvc.perform(get("/api/v1/disponibilidad/informe")
                        .param("desde", "2026-09-08T00:00:00Z")
                        .param("hasta", "2026-09-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Periodo invalido"));
    }
}
