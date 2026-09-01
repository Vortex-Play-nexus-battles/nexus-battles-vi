package com.nexusbattles.plataforma.notificaciones.bandeja;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nexusbattles.plataforma.notificaciones.BandejaDeNotificaciones;
import com.nexusbattles.plataforma.notificaciones.Notificacion;

/**
 * Pruebas del contrato HTTP de HU-NOT-006: cada estado de respuesta del
 * contrato publicado en contracts/openapi/notificaciones.yaml tiene su caso.
 */
@WebMvcTest(NotificacionesController.class)
@Import(ManejadorErroresNotificaciones.class)
class NotificacionesControllerTest {

    private static final Instant AYER = Instant.parse("2026-08-30T15:00:00Z");
    private static final String JUGADOR = "jugador-1";

    private static final String CUERPO_EMISION = """
            {
              "usuarioId": "jugador-1",
              "id": "evt-1",
              "tipo": "subasta",
              "titulo": "Tu puja fue superada",
              "cuerpo": "Alguien pujo mas alto por la Espada del Alba.",
              "creadaEn": "2026-08-30T15:00:00Z"
            }
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ServicioDeNotificaciones servicio;

    private static Notificacion aviso(String id) {
        return new Notificacion(id, "subasta", "Tu puja fue superada",
                "Alguien pujo mas alto por la Espada del Alba.", AYER);
    }

    @Test
    @DisplayName("la bandeja responde 200 con el historial y la cuenta de no leidos")
    void bandejaResponde200() throws Exception {
        BandejaDeNotificaciones bandeja = BandejaDeNotificaciones.reconstituir(
                JUGADOR, List.of(aviso("evt-1"), aviso("evt-2")),
                Set.of("evt-1"), Set.of(), Map.of());
        when(servicio.consultar(JUGADOR)).thenReturn(bandeja);

        mvc.perform(get("/api/v1/users/jugador-1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noLeidas").value(1))
                .andExpect(jsonPath("$.avisos.length()").value(2))
                .andExpect(jsonPath("$.avisos[0].leida").value(true));
    }

    @Test
    @DisplayName("marcar leido responde 200 con la cuenta actualizada")
    void marcarLeidaResponde200() throws Exception {
        when(servicio.marcarLeida(JUGADOR, "evt-1")).thenReturn(3);

        mvc.perform(post("/api/v1/users/jugador-1/notifications/evt-1/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noLeidas").value(3));
    }

    @Test
    @DisplayName("marcar un aviso inexistente responde 404")
    void avisoInexistenteResponde404() throws Exception {
        when(servicio.marcarLeida(anyString(), anyString()))
                .thenThrow(new AvisoNoEncontrado("el jugador no tiene ese aviso"));

        mvc.perform(post("/api/v1/users/jugador-1/notifications/fantasma/read"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("emitir un evento responde 201 con las sesiones alcanzadas")
    void emitirResponde201() throws Exception {
        when(servicio.emitir(eq(JUGADOR), any(Notificacion.class)))
                .thenReturn(Set.of("movil"));

        mvc.perform(post("/api/v1/internal/notifications")
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
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_EMISION))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("los datos invalidos que rechaza el dominio responden 400")
    void datosInvalidosResponden400() throws Exception {
        when(servicio.emitir(anyString(), any(Notificacion.class)))
                .thenThrow(new IllegalArgumentException("el tipo de la notificacion es obligatorio"));

        mvc.perform(post("/api/v1/internal/notifications")
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_EMISION))
                .andExpect(status().isBadRequest());
    }
}
