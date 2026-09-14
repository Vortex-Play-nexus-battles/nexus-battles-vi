package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import com.nexusbattles.plataforma.moderacionsanciones.seguridad.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato acordado con ms-subastas para HU-SUB-001: la consulta de sancion
 * activa queda abierta entre microservicios, igual que /lista-negra/verificar.
 */
@WebMvcTest(controllers = SancionesConsultaController.class)
@Import({SecurityConfig.class, SeguridadSancionesTest.CacheDePruebaConfig.class})
class SeguridadSancionesTest {

    @TestConfiguration
    static class CacheDePruebaConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsultaSancionActivaService service;

    @Test
    void laConsultaQuedaAbiertaParaLlamadasEntreMicroservicios() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        when(service.consultar(usuarioId))
                .thenReturn(new ConsultaSancionActivaService.ResultadoSancion(false, null, null));

        mockMvc.perform(get("/api/v1/sanciones/usuarios/{id}/activa", usuarioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sancionActiva").value(false))
                .andExpect(jsonPath("$.motivo").doesNotExist())
                .andExpect(jsonPath("$.vigenteHasta").doesNotExist());
    }

    @Test
    void rechazaUnIdentificadorQueNoTieneFormatoDeUuid() throws Exception {
        mockMvc.perform(get("/api/v1/sanciones/usuarios/{id}/activa", "no-es-un-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
