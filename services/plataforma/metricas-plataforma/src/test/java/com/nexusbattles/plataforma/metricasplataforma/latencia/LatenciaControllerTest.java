package com.nexusbattles.plataforma.metricasplataforma.latencia;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

import com.nexusbattles.plataforma.observabilidad.MuestraDeLatencia;
import com.nexusbattles.plataforma.observabilidad.PropiedadesDeLatencia;
import com.nexusbattles.plataforma.observabilidad.RegistroDeLatencia;

/**
 * HU-REN-001, CA-02 — el informe que se anexa como evidencia de RNF-REN-001.
 *
 * <p>El registro es real y no un doble: lo que interesa comprobar es que el
 * percentil que sale por el endpoint es el que de verdad calcula la biblioteca
 * compartida, no el que un mock diga que calcula.
 */
@WebMvcTest(controllers = LatenciaController.class)
@Import(LatenciaControllerTest.Dobles.class)
class LatenciaControllerTest {

    private static final Instant AHORA = Instant.parse("2026-09-10T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistroDeLatencia registro;

    @Autowired
    private PropiedadesDeLatencia propiedades;

    @BeforeEach
    void limpiar() {
        registro.vaciar();
        propiedades.setObjetivoMs(500);
        propiedades.setPercentil(95d);
        propiedades.setOperacionesEnInforme(5);
    }

    private void medir(String metodo, String ruta, long... duraciones) {
        for (long duracion : duraciones) {
            registro.registrar(new MuestraDeLatencia("metricas-plataforma", metodo, ruta, 200, duracion, AHORA));
        }
    }

    @Test
    void elInformeTraeElPercentilElMaximoYSiCumpleElObjetivo() throws Exception {
        medir("GET", "/api/v1/salas", 100, 150, 200, 250, 300);

        mockMvc.perform(get("/api/v1/latencia/informe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servicio").value("metricas-plataforma"))
                .andExpect(jsonPath("$.muestras").value(5))
                .andExpect(jsonPath("$.percentil").value("p95"))
                .andExpect(jsonPath("$.objetivoMs").value(500))
                .andExpect(jsonPath("$.percentilMs").value(300))
                .andExpect(jsonPath("$.maximoMs").value(300))
                .andExpect(jsonPath("$.cumple").value(true));
    }

    @Test
    void cuandoElPercentilSupera500MsElInformeDiceQueNoCumple() throws Exception {
        // El objetivo de RNF-REN-001 es innegociable: por encima, no cumple.
        medir("POST", "/api/v1/salas", 100, 200, 300, 400, 900);

        mockMvc.perform(get("/api/v1/latencia/informe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.percentilMs").value(900))
                .andExpect(jsonPath("$.cumple").value(false));
    }

    @Test
    void elInformeSinMuestrasNoSePresentaComoCumplido() throws Exception {
        // No medir no es lo mismo que cumplir.
        mockMvc.perform(get("/api/v1/latencia/informe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.muestras").value(0))
                .andExpect(jsonPath("$.sinDatos").value(true))
                .andExpect(jsonPath("$.cumple").value(false));
    }

    @Test
    void elInformeListaLasOperacionesMasLentasPrimero() throws Exception {
        medir("GET", "/api/v1/salas", 10, 20);
        medir("POST", "/api/v1/salas", 800);

        mockMvc.perform(get("/api/v1/latencia/informe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operacionesMasLentas.length()").value(2))
                .andExpect(jsonPath("$.operacionesMasLentas[0].metodo").value("POST"))
                .andExpect(jsonPath("$.operacionesMasLentas[0].percentilMs").value(800))
                .andExpect(jsonPath("$.operacionesMasLentas[1].metodo").value("GET"))
                .andExpect(jsonPath("$.operacionesMasLentas[1].muestras").value(2));
    }

    @Test
    void sinPercentilAprobadoElInformeFallaDeFormaExplicitaYNoInventaUnValor() throws Exception {
        // CA-03: mientras el Product Owner no apruebe p95 o p99 por escrito, el
        // servicio NO elige uno. Falla diciendo que falta la decision, que
        // variable la configura, y deja constancia de que la medicion sigue viva.
        propiedades.setPercentil(null);
        medir("GET", "/api/v1/salas", 42, 43, 44);

        mockMvc.perform(get("/api/v1/latencia/informe"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Percentil de evaluacion no acordado"))
                .andExpect(jsonPath("$.variable").value("LATENCIA_PERCENTIL"))
                .andExpect(jsonPath("$.criterio").value("HU-REN-001 CA-03"))
                .andExpect(jsonPath("$.muestrasAcumuladas").value(3));
    }

    @Test
    void cambiarElPercentilCambiaElInformeSinTocarCodigo() throws Exception {
        // La prueba de que CA-03 se resuelve con configuracion: el dia que el PO
        // decida, es cambiar LATENCIA_PERCENTIL y nada mas.
        for (long ms = 1; ms <= 100; ms++) {
            medir("GET", "/api/v1/salas", ms);
        }

        mockMvc.perform(get("/api/v1/latencia/informe"))
                .andExpect(jsonPath("$.percentilMs").value(95));

        propiedades.setPercentil(99d);

        mockMvc.perform(get("/api/v1/latencia/informe"))
                .andExpect(jsonPath("$.percentil").value("p99"))
                .andExpect(jsonPath("$.percentilMs").value(99));
    }

    @Test
    void elMismoInformeSePuedePedirRedactadoParaAnexarloComoEvidencia() throws Exception {
        medir("GET", "/api/v1/salas", 100, 150, 200, 250, 300);

        mockMvc.perform(get("/api/v1/latencia/informe/texto"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("RNF-REN-001")))
                .andExpect(content().string(containsString("p95: 300 ms")))
                .andExpect(content().string(containsString("Resultado: CUMPLE")))
                .andExpect(content().string(containsString("GET /api/v1/salas")));
    }

    @Test
    void elTextoSinMuestrasNoSeLeeComoUnVerde() throws Exception {
        mockMvc.perform(get("/api/v1/latencia/informe/texto"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SIN DATOS")))
                .andExpect(content().string(not(containsString("Resultado: CUMPLE"))));
    }

    /**
     * En un {@code @WebMvcTest} no corre la autoconfiguracion de la biblioteca,
     * asi que el registro y las propiedades se declaran aqui. Son las clases
     * reales, no dobles: lo que interesa comprobar es el calculo de verdad.
     */
    @TestConfiguration
    static class Dobles {

        @Bean
        RegistroDeLatencia registroDeLatencia() {
            return new RegistroDeLatencia("metricas-plataforma");
        }

        @Bean
        PropiedadesDeLatencia propiedadesDeLatencia() {
            return new PropiedadesDeLatencia();
        }
    }
}
