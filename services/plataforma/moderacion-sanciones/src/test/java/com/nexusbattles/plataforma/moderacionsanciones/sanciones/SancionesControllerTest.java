package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import com.nexusbattles.comun.seguridad.pruebas.DecodificadorDePrueba;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.plataforma.moderacionsanciones.seguridad.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La cadena y el controlador con tokens reales de ms-identidad: quien actua
 * sale del token, y los problem details llevan el motivo.
 */
@WebMvcTest(controllers = {SancionesAdminController.class, ApelacionesController.class, SancionesConsultaController.class})
@Import({SecurityConfig.class, DecodificadorDePrueba.class, ManejadorErroresSanciones.class,
        SancionesControllerTest.CacheDePruebaConfig.class})
@DisplayName("Sanciones · API con tokens de ms-identidad")
class SancionesControllerTest {

    @TestConfiguration
    static class CacheDePruebaConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }

        @Bean
        java.time.Clock relojDePrueba() {
            return java.time.Clock.fixed(AHORA.toInstant(), ZoneOffset.UTC);
        }
    }

    private static final UUID JUGADOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MODERADORA = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final OffsetDateTime AHORA = OffsetDateTime.of(2026, 9, 21, 10, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SancionesService servicio;

    @MockitoBean
    private ConsultaSancionActivaService consulta;

    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();

    private static Sancion advertencia() {
        return new Sancion(UUID.randomUUID(), JUGADOR, Sancion.Tipo.ADVERTENCIA, "Lenguaje ofensivo", null, null,
                MODERADORA, "MODERADOR", AHORA, null);
    }

    @Test
    @DisplayName("las metricas de moderacion son publicas, solo cuentas, y sin periodo cubren 30 dias (HU-MET-001)")
    void metricas() throws Exception {
        when(servicio.metricas(any(), any())).thenReturn(MetricasDeModeracion.de(AHORA.minusDays(30), AHORA,
                List.of(advertencia()), List.of()));
        mvc.perform(get("/api/v1/sanciones/metricas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.porTipo.ADVERTENCIA").value(1))
                .andExpect(jsonPath("$.porTipo.BANEO").value(0))
                .andExpect(jsonPath("$.porDia[0].emitidas").value(1))
                .andExpect(jsonPath("$.moderadoresActivos").value(1))
                .andExpect(jsonPath("$.apelaciones.PENDIENTE").value(0));
        ArgumentCaptor<OffsetDateTime> desde = ArgumentCaptor.forClass(OffsetDateTime.class);
        org.mockito.Mockito.verify(servicio).metricas(desde.capture(), eq(AHORA));
        assertThat(desde.getValue()).isEqualTo(AHORA.minusDays(30));
    }

    @Test
    @DisplayName("emitir: el actor es el uid y el rol del token, nunca el cuerpo; 201 con la sancion")
    void emitir() throws Exception {
        when(servicio.emitir(any(), any())).thenReturn(advertencia());

        mvc.perform(post("/api/v1/sanciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeUsuario("mod_ana", MODERADORA, "MODERADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":\"" + JUGADOR + "\",\"tipo\":\"ADVERTENCIA\",\"motivo\":\"Lenguaje ofensivo\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("ADVERTENCIA"))
                .andExpect(jsonPath("$.usuarioId").value(JUGADOR.toString()))
                .andExpect(jsonPath("$.vigente").value(true));

        ArgumentCaptor<Actor> actor = ArgumentCaptor.forClass(Actor.class);
        org.mockito.Mockito.verify(servicio).emitir(actor.capture(), any());
        assertThat(actor.getValue().id()).isEqualTo(MODERADORA);
        assertThat(actor.getValue().rol()).isEqualTo("MODERADOR");
    }

    @Test
    @DisplayName("sin token 401; con token de servicio 403: un servicio no sanciona a nadie")
    void sinTokenOServicio() throws Exception {
        mvc.perform(post("/api/v1/sanciones").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/sanciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeServicio("salas-partidas"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("un jugador que intenta sancionar recibe 403 con su tipo de problema (CA-04 de HU-USR-004)")
    void jugadorNoSanciona() throws Exception {
        when(servicio.emitir(any(), any())).thenThrow(new SancionRechazada(
                SancionRechazada.Motivo.PERMISO_INSUFICIENTE, "solo moderadores y administradores emiten sanciones"));

        mvc.perform(post("/api/v1/sanciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeJugador("lyra", JUGADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":\"" + MODERADORA + "\",\"tipo\":\"ADVERTENCIA\",\"motivo\":\"x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/permiso-insuficiente"))
                .andExpect(jsonPath("$.motivo").value("PERMISO_INSUFICIENTE"));
    }

    @Test
    @DisplayName("el baneo sin confirmacion es 400 y el usuario ya baneado 409")
    void baneoRechazado() throws Exception {
        when(servicio.emitir(any(), any()))
                .thenThrow(new SancionRechazada(SancionRechazada.Motivo.SOLICITUD_INVALIDA, "exige confirmacion"))
                .thenThrow(new SancionRechazada(SancionRechazada.Motivo.USUARIO_BANEADO, "ya baneado"));
        String cuerpo = "{\"usuarioId\":\"" + JUGADOR + "\",\"tipo\":\"BANEO\",\"motivo\":\"grave\"}";
        String token = "Bearer " + emisor.tokenDeUsuario("admin", MODERADORA, "ADMINISTRADOR");

        mvc.perform(post("/api/v1/sanciones").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/sanciones").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.motivo").value("USUARIO_BANEADO"));
    }

    @Test
    @DisplayName("el sancionado ve su historial y apela con su propio token")
    void historialYApelacion() throws Exception {
        Sancion s = advertencia();
        when(servicio.historialDe(any(), eq(JUGADOR))).thenReturn(List.of(s));
        when(servicio.apelar(any(), eq(s.id()), eq("No fui yo")))
                .thenReturn(new Apelacion(UUID.randomUUID(), s.id(), JUGADOR, "No fui yo", AHORA));
        String token = "Bearer " + emisor.tokenDeJugador("lyra", JUGADOR);

        mvc.perform(get("/api/v1/sanciones/usuarios/{uid}", JUGADOR).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].motivo").value("Lenguaje ofensivo"));
        mvc.perform(post("/api/v1/sanciones/{id}/apelaciones", s.id()).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"argumento\":\"No fui yo\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    @DisplayName("la apelacion que no procede es 422 con motivo")
    void apelacionNoProcede() throws Exception {
        when(servicio.apelar(any(), any(), any())).thenThrow(new SancionRechazada(
                SancionRechazada.Motivo.APELACION_NO_PROCEDE, "el plazo para apelar ya paso"));
        mvc.perform(post("/api/v1/sanciones/{id}/apelaciones", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeJugador("lyra", JUGADOR))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"argumento\":\"tarde\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.motivo").value("APELACION_NO_PROCEDE"));
    }

    @Test
    @DisplayName("el panel resuelve con decision y motivo; las pendientes se listan")
    void panel() throws Exception {
        Apelacion a = new Apelacion(UUID.randomUUID(), UUID.randomUUID(), JUGADOR, "x", AHORA);
        when(servicio.apelacionesPendientes(any())).thenReturn(List.of(a));
        when(servicio.resolver(any(), eq(a.id()), eq(Apelacion.Estado.REVERTIDA), eq("Tiene razon"), any()))
                .thenAnswer(inv -> {
                    a.resolver(Apelacion.Estado.REVERTIDA, "Tiene razon", MODERADORA, AHORA, null);
                    return a;
                });
        String token = "Bearer " + emisor.tokenDeUsuario("admin", MODERADORA, "ADMINISTRADOR");

        mvc.perform(get("/api/v1/apelaciones").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("PENDIENTE"));
        mvc.perform(post("/api/v1/apelaciones/{id}/resolucion", a.id()).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REVERTIDA\",\"motivo\":\"Tiene razon\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("REVERTIDA"))
                .andExpect(jsonPath("$.decisionMotivo").value("Tiene razon"));
    }

    @Test
    @DisplayName("los limites vigentes salen del servicio, no de una constante de la vista (1.3.0)")
    void limitesVigentes() throws Exception {
        // El PO baja el plazo a 7 dias y el maximo de suspension a 2: lo que
        // conteste este endpoint es exactamente lo que va a pintar la vista.
        when(servicio.limitesVigentes()).thenReturn(LimitesDeSancion.Fijos.de(2, 2, 7));

        mvc.perform(get("/api/v1/sanciones/limites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeJugador("lyra", JUGADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspensionMinimaHoras").value(2))
                .andExpect(jsonPath("$.suspensionMaximaHoras").value(48))
                .andExpect(jsonPath("$.suspensionMaximaDias").value(2))
                .andExpect(jsonPath("$.apelacionPlazoDias").value(7));
    }

    @Test
    @DisplayName("los limites exigen sesion: no son publicos como la consulta de sancion activa")
    void limitesExigenSesion() throws Exception {
        mvc.perform(get("/api/v1/sanciones/limites")).andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("la consulta de sancion activa sigue abierta entre servicios y ahora dice el tipo (1.1.0)")
    void consultaConTipo() throws Exception {
        when(consulta.consultar(JUGADOR)).thenReturn(new ConsultaSancionActivaService.ResultadoSancion(
                true, "Lenguaje ofensivo", AHORA.plusHours(24), "SUSPENSION"));
        mvc.perform(get("/api/v1/sanciones/usuarios/{id}/activa", JUGADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sancionActiva").value(true))
                .andExpect(jsonPath("$.tipo").value("SUSPENSION"))
                .andExpect(jsonPath("$.vigenteHasta").exists());
    }
}
