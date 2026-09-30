package com.nexusbattles.plataforma.comentarios.moderacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
import org.springframework.test.web.servlet.ResultActions;

import com.nexusbattles.comun.seguridad.pruebas.DecodificadorDePrueba;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.plataforma.comentarios.Comentario;
import com.nexusbattles.plataforma.comentarios.publicacion.ManejadorErroresComentarios;
import com.nexusbattles.plataforma.comentarios.seguridad.SecurityConfig;

/**
 * La mitad HTTP de {@code POST /comentarios/moderacion/decisiones} (contrato
 * 1.8.0). Tokens reales, y {@link ModeracionEnLote} REAL con el servicio
 * simulado: asi los 400 los dice la validacion de verdad (y el servicio no se
 * entera), y lo que llega al servicio es lo que saco el controlador del token.
 */
@WebMvcTest(ModeracionController.class)
@Import({ManejadorErroresComentarios.class, SecurityConfig.class, DecodificadorDePrueba.class,
        ModeracionEnLote.class})
@DisplayName("HU-COM-008 CA-02: el lote por HTTP")
class ModeracionEnLoteHttpTest {

    private static final String RUTA = "/api/v1/comentarios/moderacion/decisiones";
    private static final UUID UID_MODERADORA = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID UID_LYRA = UUID.fromString("7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55");
    private static final Instant CUANDO = Instant.parse("2026-09-23T10:00:00Z");

    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ServicioDeModeracion servicio;

    @MockitoBean
    private AvisoAlAutor aviso;

    @MockitoBean
    private RegistroDeAuditoria auditoria;

    private String comoModeradora() {
        return "Bearer " + emisor.tokenDeUsuario("AdaLaJusta", UID_MODERADORA, "MODERADOR");
    }

    private ResultActions enviar(String credencial, String json) throws Exception {
        var peticion = post(RUTA).contentType(MediaType.APPLICATION_JSON).content(json);
        return mvc.perform(credencial == null ? peticion : peticion.header(HttpHeaders.AUTHORIZATION, credencial));
    }

    private static String cuerpo(String accion, String motivo, String ids) {
        return "{\"moderadorId\":\"suplantado\",\"accion\":" + accion + ",\"motivo\":" + motivo
                + ",\"comentarioIds\":" + ids + "}";
    }

    private static final String VALIDO = cuerpo("\"OCULTAR\"", "\"Incumple la norma\"", "[\"c-1\",\"c-2\"]");

    private static ServicioDeModeracion.Aplicado aplicado(String id) {
        Comentario c = new Comentario(id, "prod", UID_LYRA.toString(), "LyraRoja", "texto", List.of(),
                CUANDO.minusSeconds(60), Comentario.Estado.OCULTO);
        return new ServicioDeModeracion.Aplicado(c, new AsientoDeModeracion("asi-" + id, id,
                UID_MODERADORA.toString(), "AdaLaJusta", AccionDeModeracion.OCULTAR, "Incumple la norma",
                Comentario.Estado.PUBLICADO, Comentario.Estado.OCULTO, CUANDO));
    }

    @Test
    @DisplayName("200: total y un resultado por comentario con comentario, asiento y autorNotificado")
    void forma() throws Exception {
        when(servicio.aplicarLote(any(), anyString(), anyString(), any(), anyString(), any()))
                .thenReturn(List.of(aplicado("c-1"), aplicado("c-2")));
        when(aviso.notificar(any(), any())).thenReturn(true, false);

        enviar(comoModeradora(), VALIDO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.resultados.length()").value(2))
                .andExpect(jsonPath("$.resultados[0].comentario.id").value("c-1"))
                .andExpect(jsonPath("$.resultados[0].comentario.estado").value("OCULTO"))
                .andExpect(jsonPath("$.resultados[0].asiento.accion").value("OCULTAR"))
                .andExpect(jsonPath("$.resultados[0].asiento.estadoAnterior").value("PUBLICADO"))
                .andExpect(jsonPath("$.resultados[0].asiento.estadoNuevo").value("OCULTO"))
                .andExpect(jsonPath("$.resultados[0].asiento.ipOrigen").doesNotExist())
                .andExpect(jsonPath("$.resultados[0].autorNotificado").value(true))
                .andExpect(jsonPath("$.resultados[1].comentario.id").value("c-2"))
                .andExpect(jsonPath("$.resultados[1].autorNotificado").value(false));
    }

    @Test
    @DisplayName("el moderador sale del token: el moderadorId del cuerpo no llega al servicio")
    void identidadDelToken() throws Exception {
        when(servicio.aplicarLote(any(), anyString(), anyString(), any(), anyString(), any()))
                .thenReturn(List.of(aplicado("c-1"), aplicado("c-2")));

        enviar(comoModeradora(), VALIDO).andExpect(status().isOk());

        ArgumentCaptor<String> id = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> apodo = ArgumentCaptor.forClass(String.class);
        verify(servicio).aplicarLote(eq(List.of("c-1", "c-2")), id.capture(), apodo.capture(),
                eq(AccionDeModeracion.OCULTAR), eq("Incumple la norma"), any());
        assertEquals(UID_MODERADORA.toString(), id.getValue());
        assertEquals("AdaLaJusta", apodo.getValue());
    }

    @Test
    @DisplayName("cada 400: el servicio ni se entera")
    void cuatrocientos() throws Exception {
        // 51 ids distintos, para que falle por el maximo y no por repetidos.
        StringBuilder distintos = new StringBuilder("[");
        for (int i = 0; i < 51; i++) {
            distintos.append(i == 0 ? "" : ",").append("\"c-").append(i).append('"');
        }
        distintos.append(']');

        List<String> malos = List.of(
                cuerpo("null", "\"motivo valido\"", "[\"c-1\"]"),
                cuerpo("\"EDITAR\"", "\"motivo valido\"", "[\"c-1\"]"),
                cuerpo("\"OCULTAR\"", "\"ok\"", "[\"c-1\"]"),
                cuerpo("\"OCULTAR\"", "null", "[\"c-1\"]"),
                cuerpo("\"OCULTAR\"", "\"motivo valido\"", "[]"),
                cuerpo("\"OCULTAR\"", "\"motivo valido\"", "null"),
                cuerpo("\"OCULTAR\"", "\"motivo valido\"", distintos.toString()),
                cuerpo("\"OCULTAR\"", "\"motivo valido\"", "[\"c-1\",\"c-1\"]"),
                cuerpo("\"OCULTAR\"", "\"motivo valido\"", "[\"c-1\",\"  \"]"),
                cuerpo("\"INVENTADA\"", "\"motivo valido\"", "[\"c-1\"]"));
        for (String malo : malos) {
            enviar(comoModeradora(), malo).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("sin token es 401 y el servicio no se invoca")
    void sinToken() throws Exception {
        enviar(null, VALIDO).andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("un JUGADOR es 403 y el servicio no se invoca")
    void jugador() throws Exception {
        enviar("Bearer " + emisor.tokenDeJugador("LyraRoja", UID_LYRA), VALIDO)
                .andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("409 LOTE_RECHAZADO con fallidos: comentarioId, motivo y detalle de cada uno")
    void rechazado() throws Exception {
        when(servicio.aplicarLote(any(), anyString(), anyString(), any(), anyString(), any()))
                .thenThrow(new ServicioDeModeracion.LoteRechazado(List.of(
                        new ServicioDeModeracion.Fallo("c-1", ServicioDeModeracion.Fallo.Motivo.TRANSICION_INVALIDA,
                                "No se puede OCULTAR un comentario que esta en ELIMINADO"),
                        new ServicioDeModeracion.Fallo("c-2",
                                ServicioDeModeracion.Fallo.Motivo.COMENTARIO_NO_ENCONTRADO,
                                "No existe el comentario c-2"))));

        enviar(comoModeradora(), VALIDO)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.motivo").value("LOTE_RECHAZADO"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.fallidos.length()").value(2))
                .andExpect(jsonPath("$.fallidos[0].comentarioId").value("c-1"))
                .andExpect(jsonPath("$.fallidos[0].motivo").value("TRANSICION_INVALIDA"))
                .andExpect(jsonPath("$.fallidos[0].detalle").value("No se puede OCULTAR un comentario que esta en ELIMINADO"))
                .andExpect(jsonPath("$.fallidos[1].motivo").value("COMENTARIO_NO_ENCONTRADO"));
        verifyNoInteractions(aviso, auditoria);
    }
}
