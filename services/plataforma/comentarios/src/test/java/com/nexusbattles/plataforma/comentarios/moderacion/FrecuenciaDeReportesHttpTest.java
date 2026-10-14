package com.nexusbattles.plataforma.comentarios.moderacion;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nexusbattles.comun.seguridad.pruebas.DecodificadorDePrueba;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.plataforma.comentarios.publicacion.ManejadorErroresComentarios;
import com.nexusbattles.plataforma.comentarios.seguridad.SecurityConfig;

/**
 * La frecuencia de reportes por HTTP (SA-4, comentarios.yaml 1.15.0): quien la
 * lee, con que forma y que no sale. Tokens reales, firmados y verificados; el
 * servicio de verdad, con el repositorio doblado y un reloj fijo.
 */
@WebMvcTest(FrecuenciaDeReportesController.class)
@Import({ManejadorErroresComentarios.class, SecurityConfig.class, DecodificadorDePrueba.class,
        FrecuenciaDeReportes.class, FrecuenciaDeReportesHttpTest.RelojFijo.class})
@DisplayName("La frecuencia de reportes por HTTP (§7.3.8)")
class FrecuenciaDeReportesHttpTest {

    private static final String RUTA = "/api/v1/comentarios/moderacion/reportes/frecuencia";
    private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");

    @TestConfiguration
    static class RelojFijo {
        @Bean
        Clock reloj() {
            return Clock.fixed(AHORA, ZoneOffset.UTC);
        }
    }

    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ReporteRepository repositorio;

    private String token(String rol) {
        return "Bearer " + emisor.tokenDeUsuario("AdaLaJusta", UUID.randomUUID(), rol);
    }

    @ParameterizedTest(name = "rol {0}")
    @ValueSource(strings = {"MODERADOR", "ADMINISTRADOR", "SUPER_ADMINISTRADOR"})
    @DisplayName("quien modera la lee: total, por dia, las seis categorias y los distintos, sin identificadores")
    void quienModera(String rol) throws Exception {
        when(repositorio.contablesEntre(any(), any())).thenReturn(List.of(
                new ReporteContado(Instant.parse("2026-10-12T10:00:00Z"), CategoriaDeReporte.SPAM,
                        "comentario-secreto", "reportante-secreto"),
                new ReporteContado(Instant.parse("2026-10-12T11:00:00Z"), CategoriaDeReporte.ACOSO,
                        "comentario-secreto", "otra-persona")));

        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, token(rol))
                        .param("desde", "2026-10-01T00:00:00Z").param("hasta", "2026-10-15T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").value("2026-10-01T00:00:00Z"))
                .andExpect(jsonPath("$.hasta").value("2026-10-15T00:00:00Z"))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.porDia[0].fecha").value("2026-10-12"))
                .andExpect(jsonPath("$.porDia[0].reportes").value(2))
                .andExpect(jsonPath("$.porCategoria.SPAM").value(1))
                .andExpect(jsonPath("$.porCategoria.ACOSO").value(1))
                .andExpect(jsonPath("$.porCategoria.VIOLACION_DE_DERECHOS").value(0))
                .andExpect(jsonPath("$.comentariosReportados").value(1))
                .andExpect(jsonPath("$.reportantes").value(2))
                .andExpect(content().string(not(containsString("secreto"))))
                .andExpect(content().string(not(containsString("otra-persona"))));
    }

    @Test
    @DisplayName("sin periodo, los ultimos 30 dias")
    void sinPeriodo() throws Exception {
        when(repositorio.contablesEntre(any(), any())).thenReturn(List.of());

        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, token("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").value("2026-09-14T15:00:00Z"))
                .andExpect(jsonPath("$.hasta").value("2026-10-14T15:00:00Z"))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.porDia").isEmpty());
        verify(repositorio).contablesEntre(Instant.parse("2026-09-14T15:00:00Z"), AHORA);
    }

    @Test
    @DisplayName("un jugador no la lee (403) y sin token tampoco (401)")
    void jugador() throws Exception {
        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer "
                        + emisor.tokenDeJugador("LyraRoja", UUID.randomUUID())))
                .andExpect(status().isForbidden());
        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
        verifyNoInteractions(repositorio);
    }

    @Test
    @DisplayName("una fecha que no es ISO, un periodo al reves o de mas de un año es 400")
    void rechazos() throws Exception {
        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, token("MODERADOR")).param("desde", "ayer"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, token("MODERADOR"))
                        .param("desde", "2026-10-15T00:00:00Z").param("hasta", "2026-10-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("«desde» tiene que ser anterior a «hasta»"));
        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, token("MODERADOR"))
                        .param("desde", "2024-01-01T00:00:00Z").param("hasta", "2026-10-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Se pueden pedir como mucho 366 días de reportes a la vez."));
        verifyNoInteractions(repositorio);
    }
}
