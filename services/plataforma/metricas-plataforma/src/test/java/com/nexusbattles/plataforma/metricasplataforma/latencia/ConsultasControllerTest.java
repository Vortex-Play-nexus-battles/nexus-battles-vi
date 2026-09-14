package com.nexusbattles.plataforma.metricasplataforma.latencia;

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

import com.nexusbattles.plataforma.observabilidad.MuestraDeConsulta;
import com.nexusbattles.plataforma.observabilidad.PropiedadesDeLatencia;
import com.nexusbattles.plataforma.observabilidad.RegistroDeConsultas;

/**
 * HU-REN-003 — lo que ve quien revisa si las busquedas cumplen el objetivo.
 *
 * <p>El registro es real y no un doble: lo que interesa comprobar es que el
 * percentil que sale por el endpoint es el que de verdad calcula la biblioteca.
 */
@WebMvcTest(controllers = ConsultasController.class)
@Import(ConsultasControllerTest.Dobles.class)
class ConsultasControllerTest {

    private static final Instant AHORA = Instant.parse("2026-09-11T10:00:00Z");

    private static final String LISTA_NEGRA =
            "select t.id from terminos_prohibidos t where upper(t.termino) = upper(?)";
    private static final String BANDEJA =
            "select n.* from notificaciones n where n.usuario_id = ? order by n.creada_en desc";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistroDeConsultas registro;

    @Autowired
    private PropiedadesDeLatencia propiedades;

    @BeforeEach
    void limpiar() {
        registro.vaciar();
        propiedades.setObjetivoMs(500);
        propiedades.setPercentil(95d);
        propiedades.getConsultas().setSentenciasEnInforme(10);
    }

    private void medir(String sentencia, long... duraciones) {
        for (long duracion : duraciones) {
            registro.registrar(new MuestraDeConsulta("metricas-plataforma", sentencia, duracion, AHORA, false));
        }
    }

    @Test
    void elInformeDiceSiLasConsultasSeMantienenBajoElObjetivo() throws Exception {
        medir(BANDEJA, 2, 3, 4, 5, 6);

        mockMvc.perform(get("/api/v1/consultas/informe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servicio").value("metricas-plataforma"))
                .andExpect(jsonPath("$.muestras").value(5))
                .andExpect(jsonPath("$.percentil").value("p95"))
                .andExpect(jsonPath("$.percentilMs").value(6))
                .andExpect(jsonPath("$.maximoMs").value(6))
                .andExpect(jsonPath("$.cumple").value(true))
                .andExpect(jsonPath("$.cuantasLentas").value(0));
    }

    @Test
    void unInformeSinConsultasNoSePresentaComoCumplido() throws Exception {
        mockMvc.perform(get("/api/v1/consultas/informe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinDatos").value(true))
                .andExpect(jsonPath("$.cumple").value(false));
    }

    @Test
    void elInformeSenalaQueSentenciaEsLaLenta() throws Exception {
        // CA-01 dice si se cumple; esto dice donde mirar cuando no.
        medir(BANDEJA, 2, 3);
        medir(LISTA_NEGRA, 900);

        mockMvc.perform(get("/api/v1/consultas/informe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentenciasMasLentas.length()").value(2))
                .andExpect(jsonPath("$.sentenciasMasLentas[0].sentencia").value(LISTA_NEGRA))
                .andExpect(jsonPath("$.sentenciasMasLentas[0].percentilMs").value(900))
                .andExpect(jsonPath("$.sentenciasMasLentas[1].muestras").value(2));
    }

    @Test
    void lasConsultasLentasQuedanMarcadasParaOptimizar() throws Exception {
        // CA-03 literal: «el sistema la marca en el registro de consultas
        // lentas para que el equipo proceda a su optimizacion».
        medir(BANDEJA, 3);
        medir(LISTA_NEGRA, 640);

        mockMvc.perform(get("/api/v1/consultas/lentas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.umbralLentaMs").value(500))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.consultas.length()").value(1))
                .andExpect(jsonPath("$.consultas[0].sentencia").value(LISTA_NEGRA))
                .andExpect(jsonPath("$.consultas[0].duracionMs").value(640));
    }

    @Test
    void elRegistroDeLentasRespondeAunqueElProductOwnerNoHayaElegidoPercentil() throws Exception {
        // Marcar una consulta lenta es comparar contra un umbral, no evaluar un
        // percentil: no tiene por que esperar a la decision del PO.
        propiedades.setPercentil(null);
        medir(LISTA_NEGRA, 900);

        mockMvc.perform(get("/api/v1/consultas/lentas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void sinPercentilAprobadoElInformeFallaDeFormaExplicita() throws Exception {
        propiedades.setPercentil(null);
        medir(BANDEJA, 3, 4);

        mockMvc.perform(get("/api/v1/consultas/informe"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.variable").value("LATENCIA_PERCENTIL"))
                .andExpect(jsonPath("$.criterio").value("HU-REN-001 CA-03"))
                .andExpect(jsonPath("$.muestrasAcumuladas").value(2));
    }

    @TestConfiguration
    static class Dobles {

        @Bean
        RegistroDeConsultas registroDeConsultas() {
            return new RegistroDeConsultas("metricas-plataforma", 500);
        }

        @Bean
        PropiedadesDeLatencia propiedadesDeLatencia() {
            return new PropiedadesDeLatencia();
        }
    }
}
