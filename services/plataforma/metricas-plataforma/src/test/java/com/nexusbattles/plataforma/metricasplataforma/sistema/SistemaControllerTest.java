package com.nexusbattles.plataforma.metricasplataforma.sistema;

import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.Comprobacion;
import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.SondaDeSalud;
import com.nexusbattles.plataforma.metricasplataforma.seguridad.SeguridadAbiertaDePrueba;
import com.nexusbattles.plataforma.metricasplataforma.sondeo.ResultadoReciente;
import com.nexusbattles.plataforma.metricasplataforma.sondeo.RondaEnParalelo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/v1/admin/sistema/servicios} publica lo que dice el contrato
 * (metricas-plataforma.yaml 1.9.0): los conteos de siempre, los LENTOS y si la
 * respuesta reutiliza la ultima ronda. Las reglas del sondeo se prueban en
 * {@link EstadoDelSistemaTest}; aqui, la forma JSON que lee la consola.
 */
@WebMvcTest(controllers = SistemaController.class)
// Cadena abierta: que la ruta exija rol administrativo lo afirma
// SeguridadDeObservabilidadTest con la cadena real.
@Import({SistemaControllerTest.Dobles.class, SeguridadAbiertaDePrueba.class})
class SistemaControllerTest {

    private static final Instant AHORA = Instant.parse("2026-10-05T15:00:00Z");

    @TestConfiguration
    static class Dobles {

        @Bean
        RondaEnParalelo rondaDePrueba() {
            return RondaEnParalelo.acotada(4, Duration.ofSeconds(2));
        }

        @Bean
        EstadoDelSistema estadoDelSistema(RondaEnParalelo ronda) {
            Map<String, String> servicios = new LinkedHashMap<>();
            servicios.put("torneos", "http://srv-torneos:8083/actuator/health");
            servicios.put("misiones", "http://34.193.90.11:8105/actuator/health");
            servicios.put("ms-chatbot", ConfiguracionDelSistema.NO_DESPLEGADO);
            SondaDeSalud sonda = (servicio, url, instante) -> "torneos".equals(servicio)
                    ? Comprobacion.disponible(servicio, instante)
                    : Comprobacion.sinRespuesta(servicio, instante, "sin respuesta en 1500 ms");
            return new EstadoDelSistema(new ConfiguracionDelSistema(servicios), sonda, ronda,
                    new ResultadoReciente<>(Duration.ofSeconds(60)), Clock.fixed(AHORA, ZoneOffset.UTC));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicaLosConteosLosLentosYSiReutilizaLaRonda() throws Exception {
        mockMvc.perform(get("/api/v1/admin/sistema/servicios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.operativos").value(1))
                .andExpect(jsonPath("$.caidos").value(0))
                .andExpect(jsonPath("$.lentos").value(1))
                .andExpect(jsonPath("$.noDesplegados").value(1))
                .andExpect(jsonPath("$.noObservables").value(0))
                .andExpect(jsonPath("$.instante").value(AHORA.toString()))
                .andExpect(jsonPath("$.desdeCache").value(false))
                .andExpect(jsonPath("$.servicios[0].servicio").value("torneos"))
                .andExpect(jsonPath("$.servicios[0].estado").value("OPERATIVO"))
                .andExpect(jsonPath("$.servicios[1].servicio").value("misiones"))
                .andExpect(jsonPath("$.servicios[1].estado").value("LENTO"))
                .andExpect(jsonPath("$.servicios[1].detalle").value("sin respuesta en 1500 ms"))
                .andExpect(jsonPath("$.servicios[1].instante").value(AHORA.toString()))
                .andExpect(jsonPath("$.servicios[2].estado").value("NO_DESPLEGADO"));

        // La consola pide lo mismo dos veces al cargar (Resumen y Sistema): la
        // segunda no vuelve a sondear y lo dice.
        mockMvc.perform(get("/api/v1/admin/sistema/servicios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desdeCache").value(true))
                .andExpect(jsonPath("$.instante").value(AHORA.toString()));
    }
}
