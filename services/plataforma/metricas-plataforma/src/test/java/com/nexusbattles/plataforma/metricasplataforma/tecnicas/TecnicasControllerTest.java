package com.nexusbattles.plataforma.metricasplataforma.tecnicas;

import com.nexusbattles.plataforma.metricasplataforma.seguridad.SeguridadAbiertaDePrueba;
import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.Comprobacion;
import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.ConfiguracionDeDisponibilidad;
import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.InformeDeDisponibilidad;
import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.MonitorDeDisponibilidad;
import com.nexusbattles.plataforma.observabilidad.PropiedadesDeLatencia;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HU-MET-004: el tablero recorre los servicios configurados y publica alertas y brechas. */
@WebMvcTest(controllers = TecnicasController.class)
// Rebanada con una cadena ABIERTA a proposito: lo que se prueba aqui es el
// comportamiento del endpoint. Que la observabilidad exija rol administrativo
// (HU-MET-001, #527) lo afirma SeguridadDeObservabilidadTest con la cadena real.
@Import({TecnicasControllerTest.Dobles.class, SeguridadAbiertaDePrueba.class})
class TecnicasControllerTest {

    private static final Instant AHORA = Instant.parse("2026-10-01T10:00:00Z");

    @TestConfiguration
    static class Dobles {
        @Bean
        ConfiguracionDeDisponibilidad configuracion() {
            Map<String, String> servicios = new LinkedHashMap<>();
            servicios.put("salas-partidas", "http://srv-salas:8084/actuator/health");
            servicios.put("correo", "http://srv-correo:8082/actuator/health");
            return new ConfiguracionDeDisponibilidad(servicios, 99.95, 30000);
        }

        @Bean
        PropiedadesDeLatencia latencia() {
            return new PropiedadesDeLatencia();
        }

        @Bean
        Clock reloj() {
            return Clock.fixed(AHORA, ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecolectorDeMetricas recolector;

    @MockitoBean
    private MonitorDeDisponibilidad monitor;

    @Test
    void elTableroRecorreLosServiciosConfiguradosYPublicaAlertasYBrechas() throws Exception {
        given(recolector.recolectar("salas-partidas", "http://srv-salas:8084/actuator/health"))
                .willReturn(new MetricasDeServicio("salas-partidas", 0.8, 150d, 100, 30d, 900d, 1, null));
        given(recolector.recolectar("correo", "http://srv-correo:8082/actuator/health"))
                .willReturn(MetricasDeServicio.brecha("correo", "Connection refused"));
        given(monitor.estadoActual()).willReturn(List.of(
                Comprobacion.disponible("salas-partidas", AHORA), Comprobacion.caido("correo", AHORA, "x")));
        given(monitor.informeMensual()).willReturn(new InformeDeDisponibilidad(AHORA.minus(Duration.ofDays(30)), AHORA,
                Duration.ofDays(30), 99.95, List.of(
                new InformeDeDisponibilidad.LineaDeServicio("salas-partidas", Duration.ofDays(30), Duration.ZERO, 100d, List.of()))));

        mockMvc.perform(get("/api/v1/tecnicas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generadoEn").value(AHORA.toString()))
                .andExpect(jsonPath("$.servicios.length()").value(2))
                .andExpect(jsonPath("$.servicios[0].servicio").value("salas-partidas"))
                .andExpect(jsonPath("$.servicios[0].tasaDeError").value(0.01))
                .andExpect(jsonPath("$.servicios[1].brecha").value("Connection refused"))
                .andExpect(jsonPath("$.brechas[0]").value("correo: Connection refused"))
                .andExpect(jsonPath("$.alertas.length()").value(3))
                .andExpect(jsonPath("$.alertas[0].metrica").value("cpu"))
                .andExpect(jsonPath("$.alertas[1].metrica").value("latencia"))
                .andExpect(jsonPath("$.alertas[2].servicio").value("correo"))
                .andExpect(jsonPath("$.umbrales.cpu").value(0.75))
                .andExpect(jsonPath("$.umbrales.latenciaMs").value(500))
                .andExpect(jsonPath("$.umbrales.disponibilidadPorcentaje").value(99.95));

        mockMvc.perform(get("/api/v1/tecnicas/informe/texto"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("BRECHA DE OBSERVABILIDAD")))
                .andExpect(content().string(containsString("salas-partidas [cpu]")));
    }
}
