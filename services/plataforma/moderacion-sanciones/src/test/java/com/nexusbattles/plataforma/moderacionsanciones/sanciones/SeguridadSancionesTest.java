package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import com.nexusbattles.comun.seguridad.pruebas.DecodificadorDePrueba;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.plataforma.moderacionsanciones.seguridad.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato de consulta 1.4.0 (B2): la sancion activa deja de ser publica. Los
 * consumidores entre servicios (salas, comentarios, torneos, subastas) la
 * piden con su credencial de servicio; un jugador, solo la suya.
 */
@WebMvcTest(controllers = SancionesConsultaController.class)
@Import({SecurityConfig.class, DecodificadorDePrueba.class, SeguridadSancionesTest.CacheDePruebaConfig.class})
@DisplayName("Sancion activa · exige token (moderacion-sanciones-consulta.yaml 1.4.0)")
class SeguridadSancionesTest {

    @TestConfiguration
    static class CacheDePruebaConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }

        @Bean
        java.time.Clock relojDePrueba() {
            return java.time.Clock.systemUTC();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsultaSancionActivaService service;

    /** El controlador de consulta tambien publica /sanciones/metricas (HU-MET-001). */
    @MockitoBean
    private SancionesService sanciones;

    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();

    @Test
    @DisplayName("sin token: 401 y no se consulta nada")
    void sinToken() throws Exception {
        mockMvc.perform(get("/api/v1/sanciones/usuarios/{id}/activa", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("con credencial de servicio responde, con la forma del contrato")
    void conCredencialDeServicio() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        when(service.consultar(any(), eq(usuarioId)))
                .thenReturn(new ConsultaSancionActivaService.ResultadoSancion(false, null, null));

        mockMvc.perform(get("/api/v1/sanciones/usuarios/{id}/activa", usuarioId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeServicio("ms-subastas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sancionActiva").value(false))
                .andExpect(jsonPath("$.motivo").doesNotExist())
                .andExpect(jsonPath("$.vigenteHasta").doesNotExist())
                .andExpect(jsonPath("$.sancionId").doesNotExist());
    }

    @Test
    @DisplayName("un uid que no es UUID es 400")
    void rechazaUnIdentificadorQueNoTieneFormatoDeUuid() throws Exception {
        mockMvc.perform(get("/api/v1/sanciones/usuarios/{id}/activa", "no-es-un-uuid")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeServicio("comentarios")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("las metricas de moderacion siguen publicas (solo cuentas, HU-MET-001)")
    void metricasPublicas() throws Exception {
        when(sanciones.metricas(any(), any())).thenReturn(MetricasDeModeracion.de(
                java.time.OffsetDateTime.now().minusDays(30), java.time.OffsetDateTime.now(),
                java.util.List.of(), java.util.List.of()));

        mockMvc.perform(get("/api/v1/sanciones/metricas")).andExpect(status().isOk());
    }
}
