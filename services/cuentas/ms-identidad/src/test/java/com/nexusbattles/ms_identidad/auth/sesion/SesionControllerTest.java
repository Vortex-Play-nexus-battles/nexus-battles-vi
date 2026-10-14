package com.nexusbattles.ms_identidad.auth.sesion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("POST /api/v1/auth/sesion/renovacion")
class SesionControllerTest {

    private RenovacionDeSesionService renovacion;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        renovacion = mock(RenovacionDeSesionService.class);
        mvc = MockMvcBuilders.standaloneSetup(new SesionController(renovacion)).build();
    }

    @Test
    @DisplayName("200 con el token nuevo y su caducidad, sin guardarse en ninguna cache")
    void renueva() throws Exception {
        when(renovacion.renovar("Bearer viejo")).thenReturn(
            new RenovacionDeSesionService.SesionRenovada("nuevo", Instant.parse("2026-10-13T12:15:00Z")));

        mvc.perform(post("/api/v1/auth/sesion/renovacion").header("Authorization", "Bearer viejo"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.token").value("nuevo"))
            .andExpect(jsonPath("$.expiraEn").value("2026-10-13T12:15:00Z"));
    }

    @Test
    @DisplayName("401 sesion-no-renovable como problem details")
    void noRenovable() throws Exception {
        when(renovacion.renovar("Bearer caducado"))
            .thenThrow(new SesionNoRenovableException("Tu sesión terminó. Vuelve a iniciar sesión."));

        mvc.perform(post("/api/v1/auth/sesion/renovacion").header("Authorization", "Bearer caducado"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.type").value("https://nexusbattles.upb.edu.co/errors/sesion-no-renovable"))
            .andExpect(jsonPath("$.detail").value("Tu sesión terminó. Vuelve a iniciar sesión."));
    }
}
