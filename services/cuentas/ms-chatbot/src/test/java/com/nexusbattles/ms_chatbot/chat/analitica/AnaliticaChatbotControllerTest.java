package com.nexusbattles.ms_chatbot.chat.analitica;

import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.PuntoDeTendencia;
import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.TemaFrecuente;
import com.nexusbattles.ms_chatbot.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnaliticaChatbotController.class)
@Import(SecurityConfig.class)
class AnaliticaChatbotControllerTest {

    private static final LocalDate DESDE = LocalDate.of(2026, 9, 9);
    private static final LocalDate HASTA = LocalDate.of(2026, 9, 10);
    private static final String RUTA = "/chatbot/admin/analiticas";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnaliticaChatbotService analiticaChatbotService;

    @Test
    void consultar_comoAdministrador_devuelveElTablero() throws Exception {
        when(analiticaChatbotService.calcular(eq(DESDE), eq(HASTA), any())).thenReturn(analiticaDePrueba());

        mockMvc.perform(get(RUTA).param("desde", "2026-09-09").param("hasta", "2026-09-10")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conversaciones").value(2))
            .andExpect(jsonPath("$.tasaResolucion").value(0.75))
            .andExpect(jsonPath("$.temasFrecuentes[0].titulo").value("Subastas; pujas y ofertas"));
    }

    @Test
    void consultar_comoSuperAdministrador_devuelveElTablero() throws Exception {
        when(analiticaChatbotService.calcular(eq(DESDE), eq(HASTA), any())).thenReturn(analiticaDePrueba());

        mockMvc.perform(get(RUTA).param("desde", "2026-09-09").param("hasta", "2026-09-10")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMINISTRADOR"))))
            .andExpect(status().isOk());
    }

    // Un jugador autenticado no ve las analiticas: 403, y el servicio ni se llama.
    @Test
    void consultar_comoJugador_devuelve403() throws Exception {
        mockMvc.perform(get(RUTA).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_JUGADOR"))))
            .andExpect(status().isForbidden());

        verifyNoInteractions(analiticaChatbotService);
    }

    // A diferencia de /chat/**, el panel no degrada a visitante: sin token es 401.
    @Test
    void consultar_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get(RUTA))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(analiticaChatbotService);
    }

    @Test
    void exportar_devuelveUnCsvDescargable() throws Exception {
        when(analiticaChatbotService.calcular(eq(DESDE), eq(HASTA), any())).thenReturn(analiticaDePrueba());

        mockMvc.perform(get(RUTA + "/exportacion").param("desde", "2026-09-09").param("hasta", "2026-09-10")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                containsString("analiticas-chatbot-2026-09-09-a-2026-09-10.csv")))
            .andExpect(content().string(containsString("Tasa de resolucion (%);75.0")))
            .andExpect(content().string(containsString("2026-09-09;1;2;2;0")))
            // Un titulo con ';' va entre comillas para no partir la columna.
            .andExpect(content().string(containsString("\"Subastas; pujas y ofertas\"")));
    }

    @Test
    void exportar_comoJugador_devuelve403() throws Exception {
        mockMvc.perform(get(RUTA + "/exportacion").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_JUGADOR"))))
            .andExpect(status().isForbidden());

        verifyNoInteractions(analiticaChatbotService);
    }

    private AnaliticaChatbot analiticaDePrueba() {
        return new AnaliticaChatbot(
            Instant.parse("2026-09-09T05:00:00Z"),
            Instant.parse("2026-09-11T05:00:00Z"),
            "America/Bogota",
            2, 3, 4, 1,
            0.75, 200.0,
            3, 2, 2.0 / 3,
            List.of(new TemaFrecuente("clave-subastas", "Subastas; pujas y ofertas", 3)),
            List.of(
                new PuntoDeTendencia(DESDE, 1, 2, 2, 0),
                new PuntoDeTendencia(HASTA, 1, 1, 2, 1)));
    }
}
