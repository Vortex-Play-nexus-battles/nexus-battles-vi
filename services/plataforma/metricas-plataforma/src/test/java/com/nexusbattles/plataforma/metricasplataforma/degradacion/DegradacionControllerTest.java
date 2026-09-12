package com.nexusbattles.plataforma.metricasplataforma.degradacion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.nexusbattles.plataforma.resiliencia.RegistroDeDegradacion;

/**
 * HU-DIS-003 — lo que se consulta para saber que secciones estan limitadas.
 */
@WebMvcTest(controllers = DegradacionController.class)
@Import(DegradacionControllerTest.Dobles.class)
class DegradacionControllerTest {

    private static final Instant CAIDA = Instant.parse("2026-09-11T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistroDeDegradacion registro;

    @BeforeEach
    void limpiar() {
        registro.vaciar();
    }

    @Test
    void sinDegradacionesElServicioSeReportaOperativoPorCompleto() throws Exception {
        mockMvc.perform(get("/api/v1/degradacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operativoPorCompleto").value(true))
                .andExpect(jsonPath("$.seccionesLimitadas.length()").value(0));
    }

    @Test
    void conUnaSeccionLimitadaElEndpointSigueRespondiendo200() throws Exception {
        // Esto es CA-01 en una sola aserverion: el servicio no se cae, sigue en
        // pie y ademas cuenta que hay una seccion limitada.
        registro.degradada("inventario", "Inventario", CAIDA);

        mockMvc.perform(get("/api/v1/degradacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operativoPorCompleto").value(false))
                .andExpect(jsonPath("$.seccionesLimitadas.length()").value(1))
                .andExpect(jsonPath("$.seccionesLimitadas[0].dependencia").value("inventario"))
                .andExpect(jsonPath("$.seccionesLimitadas[0].seccion").value("Inventario"))
                .andExpect(jsonPath("$.seccionesLimitadas[0].desde").value("2026-09-11T10:00:00Z"));
    }

    @Test
    void unaSeccionLimitadaNoOcultaQueLasDemasSiguenBien() throws Exception {
        // CP-03: se consultan los demas modulos y siguen respondiendo con
        // normalidad. Aqui: solo aparece la que de verdad esta caida.
        registro.degradada("inventario", "Inventario", CAIDA);
        registro.degradada("correo", "Correo", CAIDA.plusSeconds(60));
        registro.recuperada("correo");

        mockMvc.perform(get("/api/v1/degradacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seccionesLimitadas.length()").value(1))
                .andExpect(jsonPath("$.seccionesLimitadas[0].dependencia").value("inventario"));
    }

    @TestConfiguration
    static class Dobles {

        @Bean
        RegistroDeDegradacion registroDeDegradacion() {
            return new RegistroDeDegradacion();
        }
    }
}
