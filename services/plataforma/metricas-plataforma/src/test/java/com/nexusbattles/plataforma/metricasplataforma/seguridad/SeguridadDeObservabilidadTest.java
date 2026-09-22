package com.nexusbattles.plataforma.metricasplataforma.seguridad;

import com.nexusbattles.comun.seguridad.pruebas.DecodificadorDePrueba;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.plataforma.metricasplataforma.moderacion.FuenteDeModeracion;
import com.nexusbattles.plataforma.metricasplataforma.moderacion.ModeracionController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-MET-001 (#527): la observabilidad del bloque es de administracion.
 *
 * <p>Hasta el 22-sep-2026 este servicio no tenia cadena de seguridad y
 * {@code GET /api/v1/tecnicas} devolvia por el borde, sin token, el consumo y
 * los 5xx de los siete servicios de plataforma. Esta prueba existe para que no
 * vuelva a pasar: se ejercita la cadena REAL con tokens firmados de verdad por
 * el emisor de pruebas (mismo JWKS que valida produccion).
 */
@WebMvcTest(controllers = ModeracionController.class)
@Import({SecurityConfig.class, DecodificadorDePrueba.class, SeguridadDeObservabilidadTest.Dobles.class})
@DisplayName("Observabilidad · solo administracion (HU-MET-001)")
class SeguridadDeObservabilidadTest {

    private static final UUID ADMIN = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID JUGADORA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MODERADORA = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @TestConfiguration
    static class Dobles {
        @Bean
        Clock relojDePrueba() {
            return Clock.fixed(Instant.parse("2026-09-22T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private FuenteDeModeracion fuente;

    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();

    private void conAgregadosVacios() {
        OffsetDateTime hasta = OffsetDateTime.parse("2026-09-22T10:00:00Z");
        when(fuente.consultar(any(), any())).thenReturn(new FuenteDeModeracion.Agregados(
                hasta.minusDays(30), hasta, 0, Map.of(), List.of(), Map.of(), 0, 0));
    }

    @Test
    @DisplayName("sin token no se ve nada: 401 y no se consulta la fuente")
    void sinToken() throws Exception {
        mvc.perform(get("/api/v1/moderacion")).andExpect(status().isUnauthorized());
        verifyNoInteractions(fuente);
    }

    @Test
    @DisplayName("un jugador no ve la observabilidad del bloque: 403")
    void jugador() throws Exception {
        mvc.perform(get("/api/v1/moderacion")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeJugador("lyra", JUGADORA)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(fuente);
    }

    @Test
    @DisplayName("un moderador tampoco: el panel de observabilidad ya lo reservaba la cabecera a administracion")
    void moderador() throws Exception {
        mvc.perform(get("/api/v1/moderacion")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + emisor.tokenDeUsuario("mod_e2e", MODERADORA, "MODERADOR")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(fuente);
    }

    @Test
    @DisplayName("un administrador entra")
    void administrador() throws Exception {
        conAgregadosVacios();
        mvc.perform(get("/api/v1/moderacion")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + emisor.tokenDeUsuario("admin_e2e", ADMIN, "ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sanciones.total").value(0))
                .andExpect(jsonPath("$.alertasConfiguradas").value(false));
    }

    @Test
    @DisplayName("un servicio con su credencial tambien: consulta la degradacion del bloque sin persona detras")
    void servicio() throws Exception {
        conAgregadosVacios();
        mvc.perform(get("/api/v1/moderacion")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeServicio("salas-partidas")))
                .andExpect(status().isOk());
    }
}
