package com.nexusbattles.plataforma.adminparametros.politicas;

import com.nexusbattles.comun.seguridad.pruebas.DecodificadorDePrueba;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.plataforma.adminparametros.parametros.Actor;
import com.nexusbattles.plataforma.adminparametros.parametros.ManejadorErroresParametros;
import com.nexusbattles.plataforma.adminparametros.parametros.ParametroRechazado;
import com.nexusbattles.plataforma.adminparametros.seguridad.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PoliticasController.class)
@Import({SecurityConfig.class, DecodificadorDePrueba.class, ManejadorErroresParametros.class})
@DisplayName("SA-11 · API de los cambios en las politicas de uso")
class PoliticasControllerTest {

    private static final UUID ADMIN = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final OffsetDateTime PUBLICADO = OffsetDateTime.of(2026, 10, 14, 15, 0, 0, 0, ZoneOffset.UTC);
    private static final String CUERPO = "{\"politica\":\"TERMINOS_Y_CONDICIONES\",\"titulo\":\"Nuevas reglas del chat\","
            + "\"descripcion\":\"Los mensajes con enlaces externos se ocultan hasta que un moderador los revise.\","
            + "\"vigenteDesde\":\"2026-10-20T05:00:00Z\",\"motivo\":\"Acuerdo del comité\"}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PoliticasService servicio;

    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();

    private static CambioDePolitica cambio(OffsetDateTime publicadoEn) {
        return new CambioDePolitica(UUID.fromString("9d0c6a52-7a59-4d4b-8f3e-2d64c1b2a001"),
                Politica.TERMINOS_Y_CONDICIONES, "Nuevas reglas del chat",
                "Los mensajes con enlaces externos se ocultan hasta que un moderador los revise.",
                "Acuerdo del comité", PUBLICADO.plusDays(6), ADMIN, publicadoEn, "203.0.113.9");
    }

    @Test
    @DisplayName("publicar exige token (401); el actor y la IP salen del token y del borde; responde 201 sin datos internos")
    void publicar() throws Exception {
        mvc.perform(post("/api/v1/parametros/politicas/cambios").contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isUnauthorized());
        verify(servicio, never()).publicar(any(), any(), any());

        when(servicio.publicar(any(), any(), any())).thenReturn(cambio(PUBLICADO));
        mvc.perform(post("/api/v1/parametros/politicas/cambios")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + emisor.tokenDeUsuario("superadmin", ADMIN, "SUPER_ADMINISTRADOR"))
                        .header("X-Real-IP", "203.0.113.9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("9d0c6a52-7a59-4d4b-8f3e-2d64c1b2a001"))
                .andExpect(jsonPath("$.politica").value("TERMINOS_Y_CONDICIONES"))
                .andExpect(jsonPath("$.nombreDeLaPolitica").value("Términos y condiciones del servicio"))
                .andExpect(jsonPath("$.titulo").value("Nuevas reglas del chat"))
                .andExpect(jsonPath("$.publicadoPor").doesNotExist())
                .andExpect(jsonPath("$.motivo").doesNotExist())
                .andExpect(jsonPath("$.ipOrigen").doesNotExist());

        ArgumentCaptor<Actor> actor = ArgumentCaptor.forClass(Actor.class);
        ArgumentCaptor<PoliticasService.Publicacion> pedido =
                ArgumentCaptor.forClass(PoliticasService.Publicacion.class);
        verify(servicio).publicar(actor.capture(), pedido.capture(), eq("203.0.113.9"));
        assertThat(actor.getValue()).isEqualTo(new Actor(ADMIN, "SUPER_ADMINISTRADOR"));
        assertThat(pedido.getValue().politica()).isEqualTo("TERMINOS_Y_CONDICIONES");
        assertThat(pedido.getValue().motivo()).isEqualTo("Acuerdo del comité");
        assertThat(pedido.getValue().vigenteDesde()).isEqualTo(OffsetDateTime.parse("2026-10-20T05:00:00Z"));
    }

    @Test
    @DisplayName("los rechazos del servicio salen como problem details con su motivo: 403 y 400")
    void rechazos() throws Exception {
        when(servicio.publicar(any(), any(), any()))
                .thenThrow(new ParametroRechazado(ParametroRechazado.Motivo.PERMISO_INSUFICIENTE, "de la administración"));
        mvc.perform(post("/api/v1/parametros/politicas/cambios")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + emisor.tokenDeUsuario("jugadora", UUID.randomUUID(), "JUGADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.motivo").value("PERMISO_INSUFICIENTE"));

        doThrow(new ParametroRechazado(ParametroRechazado.Motivo.SOLICITUD_INVALIDA, "Elige la política."))
                .when(servicio).publicar(any(), any(), any());
        mvc.perform(post("/api/v1/parametros/politicas/cambios")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + emisor.tokenDeUsuario("admin", ADMIN, "ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/solicitud-invalida"))
                .andExpect(jsonPath("$.detail").value("Elige la política."));
    }

    @Test
    @DisplayName("leer es publico: el historial y lo posterior a un punto, con su hasta y si el lote esta completo")
    void lecturas() throws Exception {
        when(servicio.historial()).thenReturn(List.of(cambio(PUBLICADO)));
        mvc.perform(get("/api/v1/parametros/politicas/cambios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].politica").value("TERMINOS_Y_CONDICIONES"))
                .andExpect(jsonPath("$[0].vigenteDesde").value("2026-10-20T15:00:00Z"))
                .andExpect(jsonPath("$[0].publicadoPor").doesNotExist());

        OffsetDateTime desde = PUBLICADO.minusDays(30);
        when(servicio.posteriores(eq(desde), anyInt())).thenReturn(List.of(cambio(PUBLICADO)));
        mvc.perform(get("/api/v1/parametros/politicas/cambios/posteriores")
                        .param("desde", "2026-09-14T15:00:00Z").param("limite", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cambios[0].id").value("9d0c6a52-7a59-4d4b-8f3e-2d64c1b2a001"))
                .andExpect(jsonPath("$.hasta").value("2026-10-14T15:00:00Z"))
                .andExpect(jsonPath("$.completo").value(true));

        when(servicio.posteriores(eq(PUBLICADO), anyInt())).thenReturn(List.of());
        mvc.perform(get("/api/v1/parametros/politicas/cambios/posteriores")
                        .param("desde", "2026-10-14T15:00:00Z").param("limite", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cambios").isEmpty())
                .andExpect(jsonPath("$.hasta").value("2026-10-14T15:00:00Z"))
                .andExpect(jsonPath("$.completo").value(true));

        when(servicio.posteriores(eq(desde), eq(1))).thenReturn(List.of(cambio(PUBLICADO)));
        mvc.perform(get("/api/v1/parametros/politicas/cambios/posteriores")
                        .param("desde", "2026-09-14T15:00:00Z").param("limite", "1"))
                .andExpect(jsonPath("$.completo").value(false));
    }

    @Test
    @DisplayName("sin desde, o con una fecha que no se entiende, es 400")
    void desdeInvalido() throws Exception {
        mvc.perform(get("/api/v1/parametros/politicas/cambios/posteriores"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/parametros/politicas/cambios/posteriores").param("desde", "ayer"))
                .andExpect(status().isBadRequest());
        verify(servicio, never()).posteriores(any(), anyInt());
    }
}
