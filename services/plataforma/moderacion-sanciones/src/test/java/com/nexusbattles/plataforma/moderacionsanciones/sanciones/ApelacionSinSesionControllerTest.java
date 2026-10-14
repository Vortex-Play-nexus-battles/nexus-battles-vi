package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import com.nexusbattles.comun.seguridad.pruebas.DecodificadorDePrueba;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.plataforma.moderacionsanciones.seguridad.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SA-4 — las rutas internas de la apelacion sin sesion con tokens reales:
 * solo ms-identidad, con su credencial de servicio, llega al servicio.
 */
@WebMvcTest(controllers = ApelacionSinSesionController.class)
@Import({SecurityConfig.class, DecodificadorDePrueba.class, ManejadorErroresSanciones.class,
        ApelacionSinSesionControllerTest.Config.class})
@DisplayName("Apelacion sin sesion · rutas internas, solo para ms-identidad")
class ApelacionSinSesionControllerTest {

    private static final UUID JUGADOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MODERADORA = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final OffsetDateTime AHORA = OffsetDateTime.of(2026, 9, 21, 10, 0, 0, 0, ZoneOffset.UTC);

    @TestConfiguration
    static class Config {
        /** La aplicacion activa la cache (lista negra): el corte web necesita un gestor. */
        @Bean
        org.springframework.cache.CacheManager cacheManager() {
            return new org.springframework.cache.concurrent.ConcurrentMapCacheManager();
        }

        @Bean
        Clock relojDePrueba() {
            return Clock.fixed(AHORA.toInstant(), ZoneOffset.UTC);
        }

        @Bean
        PlazoDeDecision plazoDePrueba() {
            return PlazoDeDecision.deLunesAViernes();
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SancionesService servicio;

    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();

    private static Sancion baneo() {
        return new Sancion(UUID.randomUUID(), JUGADOR, Sancion.Tipo.BANEO, Causal.FRAUDE, "Duplico creditos",
                "Juego limpio", null, MODERADORA, "ADMINISTRADOR", AHORA, null);
    }

    private String identidad() {
        return "Bearer " + emisor.tokenDeServicio("ms-identidad");
    }

    @Test
    @DisplayName("ms-identidad lee la sancion que impide entrar, hasta cuando se apela y la ultima apelacion")
    void estado() throws Exception {
        Sancion baneo = baneo();
        Apelacion abierta = new Apelacion(UUID.randomUUID(), baneo.id(), JUGADOR, "No fui yo", AHORA);
        when(servicio.estadoDeApelacionDe(JUGADOR)).thenReturn(new SancionesService.EstadoDeApelacion(baneo,
                AHORA.plusDays(30), SancionesService.MotivoNoApelable.APELACION_ABIERTA, abierta));

        mvc.perform(get("/api/v1/internal/sanciones/usuarios/{uid}/apelacion", JUGADOR)
                        .header(HttpHeaders.AUTHORIZATION, identidad()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sancion.id").value(baneo.id().toString()))
                .andExpect(jsonPath("$.sancion.tipo").value("BANEO"))
                .andExpect(jsonPath("$.sancion.causal").value("FRAUDE"))
                .andExpect(jsonPath("$.apelableHasta").value("2026-10-21T10:00:00Z"))
                .andExpect(jsonPath("$.puedeApelar").value(false))
                .andExpect(jsonPath("$.motivoNoApelable").value("APELACION_ABIERTA"))
                .andExpect(jsonPath("$.apelacion.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.apelacion.decisionAntesDe").value("2026-10-13T04:59:59.999Z"));
    }

    @Test
    @DisplayName("sin sancion que impida entrar: sancion nula y SIN_SANCION")
    void sinSancion() throws Exception {
        when(servicio.estadoDeApelacionDe(JUGADOR)).thenReturn(new SancionesService.EstadoDeApelacion(null, null,
                SancionesService.MotivoNoApelable.SIN_SANCION, null));

        mvc.perform(get("/api/v1/internal/sanciones/usuarios/{uid}/apelacion", JUGADOR)
                        .header(HttpHeaders.AUTHORIZATION, identidad()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puedeApelar").value(false))
                .andExpect(jsonPath("$.motivoNoApelable").value("SIN_SANCION"));
    }

    @Test
    @DisplayName("ms-identidad presenta la apelacion: 201 con su plazo de decision")
    void apelar() throws Exception {
        Sancion baneo = baneo();
        when(servicio.apelarSinSesion(eq(JUGADOR), eq(baneo.id()), eq("No fui yo")))
                .thenReturn(new Apelacion(UUID.randomUUID(), baneo.id(), JUGADOR, "No fui yo", AHORA));

        mvc.perform(post("/api/v1/internal/sanciones/usuarios/{uid}/apelaciones", JUGADOR)
                        .header(HttpHeaders.AUTHORIZATION, identidad())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sancionId\":\"" + baneo.id() + "\",\"argumento\":\"No fui yo\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.vencida").value(false))
                .andExpect(jsonPath("$.decisionAntesDe").value("2026-10-13T04:59:59.999Z"));
    }

    @Test
    @DisplayName("la apelacion que no procede llega con su 422 y su motivo")
    void noProcede() throws Exception {
        when(servicio.apelarSinSesion(any(), any(), any())).thenThrow(new SancionRechazada(
                SancionRechazada.Motivo.APELACION_NO_PROCEDE, "ya hay una apelacion abierta sobre esta sancion"));

        mvc.perform(post("/api/v1/internal/sanciones/usuarios/{uid}/apelaciones", JUGADOR)
                        .header(HttpHeaders.AUTHORIZATION, identidad())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sancionId\":\"" + UUID.randomUUID() + "\",\"argumento\":\"otra\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.motivo").value("APELACION_NO_PROCEDE"));
    }

    @Test
    @DisplayName("otro servicio: 403; un jugador o un administrador: 403; sin token: 401. Nunca llegan al servicio")
    void soloIdentidad() throws Exception {
        String ruta = "/api/v1/internal/sanciones/usuarios/{uid}/apelacion";
        mvc.perform(get(ruta, JUGADOR).header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + emisor.tokenDeServicio("salas-partidas")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.motivo").value("PERMISO_INSUFICIENTE"));
        mvc.perform(get(ruta, JUGADOR).header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + emisor.tokenDeJugador("lyra", JUGADOR)))
                .andExpect(status().isForbidden());
        mvc.perform(get(ruta, JUGADOR).header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + emisor.tokenDeUsuario("super", MODERADORA, "SUPER_ADMINISTRADOR")))
                .andExpect(status().isForbidden());
        mvc.perform(get(ruta, JUGADOR)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/internal/sanciones/usuarios/{uid}/apelaciones", JUGADOR)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeJugador("lyra", JUGADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sancionId\":\"" + UUID.randomUUID() + "\",\"argumento\":\"x\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(servicio);
    }
}
