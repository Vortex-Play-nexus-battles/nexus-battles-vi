package com.nexusbattles.plataforma.notificaciones.bandeja;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nexusbattles.comun.seguridad.pruebas.DecodificadorDePrueba;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.plataforma.notificaciones.BandejaDeNotificaciones;
import com.nexusbattles.plataforma.notificaciones.Notificacion;
import com.nexusbattles.plataforma.notificaciones.seguridad.SecurityConfig;

/**
 * Pruebas del contrato HTTP de HU-NOT-006 (contrato 1.1.0): cada estado de
 * respuesta tiene su caso, y quien puede que se comprueba con tokens reales
 * —firmados y verificados contra un JWKS— no con un principal inventado.
 */
@WebMvcTest(NotificacionesController.class)
@Import({ManejadorErroresNotificaciones.class, SecurityConfig.class, DecodificadorDePrueba.class})
class NotificacionesControllerTest {

    private static final Instant AYER = Instant.parse("2026-08-30T15:00:00Z");
    private static final UUID UID = UUID.fromString("7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55");
    private static final String JUGADOR = UID.toString();
    private static final String BANDEJA = "/api/v1/users/" + JUGADOR + "/notifications";

    private static final String CUERPO_EMISION = """
            {
              "usuarioId": "%s",
              "id": "evt-1",
              "tipo": "subasta",
              "titulo": "Tu puja fue superada",
              "cuerpo": "Alguien pujo mas alto por la Espada del Alba.",
              "creadaEn": "2026-08-30T15:00:00Z"
            }
            """.formatted(JUGADOR);

    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ServicioDeNotificaciones servicio;

    private static Notificacion aviso(String id) {
        return new Notificacion(id, "subasta", "Tu puja fue superada",
                "Alguien pujo mas alto por la Espada del Alba.", AYER);
    }

    private String comoElDueno() {
        return "Bearer " + emisor.tokenDeJugador("Ana", UID);
    }

    private String comoOtroJugador() {
        return "Bearer " + emisor.tokenDeJugador("Intruso", UUID.randomUUID());
    }

    private String comoServicio(String clientId) {
        return "Bearer " + emisor.tokenDeServicio(clientId);
    }

    @Nested
    @DisplayName("la bandeja es de su dueno")
    class Propiedad {

        @Test
        @DisplayName("sin token, 401")
        void sinToken() throws Exception {
            mvc.perform(get(BANDEJA)).andExpect(status().isUnauthorized());
            mvc.perform(post(BANDEJA + "/evt-1/read")).andExpect(status().isUnauthorized());
            verifyNoInteractions(servicio);
        }

        @Test
        @DisplayName("con el token de otro usuario, 403: no se lee ni se marca la bandeja ajena")
        void otroUsuario() throws Exception {
            mvc.perform(get(BANDEJA).header(HttpHeaders.AUTHORIZATION, comoOtroJugador()))
                    .andExpect(status().isForbidden());
            mvc.perform(post(BANDEJA + "/evt-1/read").header(HttpHeaders.AUTHORIZATION, comoOtroJugador()))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/users/" + JUGADOR + "/sessions/movil/pending")
                            .header(HttpHeaders.AUTHORIZATION, comoOtroJugador()))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(servicio);
        }

        @Test
        @DisplayName("un token de servicio no tiene bandeja: 403")
        void tokenDeServicio() throws Exception {
            mvc.perform(get(BANDEJA).header(HttpHeaders.AUTHORIZATION, comoServicio("ms-subastas")))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(servicio);
        }

        @Test
        @DisplayName("un token caducado o firmado por otro es 401")
        void tokenInvalido() throws Exception {
            mvc.perform(get(BANDEJA)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenCaducado("Ana", UID)))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get(BANDEJA)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenFirmadoPorOtro("Ana", UID)))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(servicio);
        }

        @Test
        @DisplayName("un usuario no emite avisos: /internal es de servicios")
        void usuarioNoEmite() throws Exception {
            mvc.perform(post("/api/v1/internal/notifications")
                            .header(HttpHeaders.AUTHORIZATION, comoElDueno())
                            .contentType(MediaType.APPLICATION_JSON).content(CUERPO_EMISION))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/internal/notifications")
                            .contentType(MediaType.APPLICATION_JSON).content(CUERPO_EMISION))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(servicio);
        }

        @Test
        @DisplayName("con la forma de Keycloak (sujeto estable) el dueno tambien entra")
        void tokenDeKeycloak() throws Exception {
            when(servicio.consultar(JUGADOR)).thenReturn(BandejaDeNotificaciones.reconstituir(
                    JUGADOR, List.of(), Set.of(), Set.of(), Map.of()));

            mvc.perform(get(BANDEJA).header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + emisor.tokenDeKeycloak(UID, "ana", List.of("JUGADOR"))))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("la bandeja responde 200 con el historial y la cuenta de no leidos")
    void bandejaResponde200() throws Exception {
        BandejaDeNotificaciones bandeja = BandejaDeNotificaciones.reconstituir(
                JUGADOR, List.of(aviso("evt-1"), aviso("evt-2")),
                Set.of("evt-1"), Set.of(), Map.of());
        when(servicio.consultar(JUGADOR)).thenReturn(bandeja);

        mvc.perform(get(BANDEJA).header(HttpHeaders.AUTHORIZATION, comoElDueno()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(JUGADOR))
                .andExpect(jsonPath("$.noLeidas").value(1))
                .andExpect(jsonPath("$.avisos.length()").value(2))
                .andExpect(jsonPath("$.avisos[0].leida").value(true));
    }

    @Test
    @DisplayName("marcar leido responde 200 con la cuenta actualizada")
    void marcarLeidaResponde200() throws Exception {
        when(servicio.marcarLeida(JUGADOR, "evt-1")).thenReturn(3);

        mvc.perform(post(BANDEJA + "/evt-1/read").header(HttpHeaders.AUTHORIZATION, comoElDueno()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noLeidas").value(3));
    }

    @Test
    @DisplayName("marcar un aviso inexistente responde 404")
    void avisoInexistenteResponde404() throws Exception {
        when(servicio.marcarLeida(anyString(), anyString()))
                .thenThrow(new AvisoNoEncontrado("el jugador no tiene ese aviso"));

        mvc.perform(post(BANDEJA + "/fantasma/read").header(HttpHeaders.AUTHORIZATION, comoElDueno()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("los pendientes de una sesion responden 200 al dueno")
    void pendientesResponden200() throws Exception {
        when(servicio.consultar(JUGADOR)).thenReturn(BandejaDeNotificaciones.reconstituir(
                JUGADOR, List.of(aviso("evt-1")), Set.of(), Set.of(), Map.of()));
        when(servicio.registrarSesion(JUGADOR, "movil")).thenReturn(List.of(aviso("evt-1")));

        mvc.perform(post("/api/v1/users/" + JUGADOR + "/sessions/movil/pending")
                        .header(HttpHeaders.AUTHORIZATION, comoElDueno()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("evt-1"))
                .andExpect(jsonPath("$[0].leida").value(false));
    }

    @Test
    @DisplayName("emitir un evento con token de servicio responde 201 con las sesiones alcanzadas")
    void emitirResponde201() throws Exception {
        when(servicio.emitir(eq(JUGADOR), any(Notificacion.class)))
                .thenReturn(Set.of("movil"));

        mvc.perform(post("/api/v1/internal/notifications")
                        .header(HttpHeaders.AUTHORIZATION, comoServicio("ms-identidad"))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_EMISION))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.aviso.id").value("evt-1"))
                .andExpect(jsonPath("$.sesionesNotificadas[0]").value("movil"));
    }

    @Test
    @DisplayName("el mismo evento repetido responde 409")
    void eventoRepetidoResponde409() throws Exception {
        when(servicio.emitir(anyString(), any(Notificacion.class)))
                .thenThrow(new AvisoDuplicado("ya existe un aviso con ese identificador"));

        mvc.perform(post("/api/v1/internal/notifications")
                        .header(HttpHeaders.AUTHORIZATION, comoServicio("ms-subastas"))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_EMISION))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("los datos invalidos que rechaza el dominio responden 400")
    void datosInvalidosResponden400() throws Exception {
        when(servicio.emitir(anyString(), any(Notificacion.class)))
                .thenThrow(new IllegalArgumentException("el tipo de la notificacion es obligatorio"));

        mvc.perform(post("/api/v1/internal/notifications")
                        .header(HttpHeaders.AUTHORIZATION, comoServicio("ms-subastas"))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_EMISION))
                .andExpect(status().isBadRequest());
    }
}
