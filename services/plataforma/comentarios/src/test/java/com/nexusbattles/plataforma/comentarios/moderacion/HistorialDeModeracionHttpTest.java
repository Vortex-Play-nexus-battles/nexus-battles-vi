package com.nexusbattles.plataforma.comentarios.moderacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nexusbattles.comun.seguridad.pruebas.DecodificadorDePrueba;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.plataforma.comentarios.Comentario;
import com.nexusbattles.plataforma.comentarios.publicacion.ManejadorErroresComentarios;
import com.nexusbattles.plataforma.comentarios.seguridad.SecurityConfig;

/**
 * El historico de moderacion por HTTP (SA-4, comentarios.yaml 1.14.0): quien
 * lo lee, con que forma y que filtros llegan al servicio. Tokens reales,
 * firmados y verificados, como en {@link ModeracionHttpTest}.
 */
@WebMvcTest(HistorialDeModeracionController.class)
@Import({ManejadorErroresComentarios.class, SecurityConfig.class, DecodificadorDePrueba.class})
@DisplayName("El historico de moderacion por HTTP (SA-4)")
class HistorialDeModeracionHttpTest {

    private static final String RUTA = "/api/v1/comentarios/moderacion/historial";
    private static final Instant CUANDO = Instant.parse("2026-10-14T15:00:00Z");

    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private HistorialDeModeracion historial;

    private String token(String rol) {
        return "Bearer " + emisor.tokenDeUsuario("AdaLaJusta", UUID.randomUUID(), rol);
    }

    private static HistorialDeModeracion.Pagina unaFila() {
        AsientoDeModeracion asiento = new AsientoDeModeracion("asi-1", "com-1", "mod-1", "AdaLaJusta",
                AccionDeModeracion.EDITAR, "quitar el insulto", Comentario.Estado.PUBLICADO,
                Comentario.Estado.PUBLICADO, CUANDO, "texto con insulto", "texto moderado", "198.51.100.7");
        Comentario comentario = new Comentario("com-1", "espada-del-alba", "autor-1", "LyraRoja", "texto moderado",
                List.of(), CUANDO.minusSeconds(3600), Comentario.Estado.PUBLICADO);
        return new HistorialDeModeracion.Pagina(List.of(new HistorialDeModeracion.Fila(asiento, comentario)),
                0, 16, 1, 1);
    }

    @ParameterizedTest(name = "rol {0}")
    @ValueSource(strings = {"MODERADOR", "ADMINISTRADOR", "SUPER_ADMINISTRADOR"})
    @DisplayName("quien modera lo lee, con el comentario de cada asiento y sin la IP de origen")
    void quienModera(String rol) throws Exception {
        when(historial.consultar(any(), anyInt(), anyInt())).thenReturn(unaFila());

        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, token(rol)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].id").value("asi-1"))
                .andExpect(jsonPath("$.contenido[0].fecha").value("2026-10-14T15:00:00Z"))
                .andExpect(jsonPath("$.contenido[0].accion").value("EDITAR"))
                .andExpect(jsonPath("$.contenido[0].apodoModerador").value("AdaLaJusta"))
                .andExpect(jsonPath("$.contenido[0].motivo").value("quitar el insulto"))
                .andExpect(jsonPath("$.contenido[0].estadoAnterior").value("PUBLICADO"))
                .andExpect(jsonPath("$.contenido[0].textoEditado").value(true))
                .andExpect(jsonPath("$.contenido[0].comentarioId").value("com-1"))
                .andExpect(jsonPath("$.contenido[0].productoId").value("espada-del-alba"))
                .andExpect(jsonPath("$.contenido[0].apodoAutor").value("LyraRoja"))
                .andExpect(jsonPath("$.contenido[0].extracto").value("texto moderado"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.totalPaginas").value(1))
                .andExpect(content().string(not(containsString("198.51.100.7"))))
                .andExpect(content().string(not(containsString("ipOrigen"))));
    }

    @Test
    @DisplayName("los filtros y la pagina llegan al servicio tal cual")
    void filtros() throws Exception {
        when(historial.consultar(any(), anyInt(), anyInt())).thenReturn(unaFila());

        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, token("MODERADOR"))
                        .param("accion", "OCULTAR").param("moderador", "ada")
                        .param("desde", "2026-10-01T05:00:00Z").param("hasta", "2026-10-15T05:00:00Z")
                        .param("pagina", "2").param("tamano", "50"))
                .andExpect(status().isOk());

        ArgumentCaptor<HistorialDeModeracion.Filtro> filtro = ArgumentCaptor.forClass(HistorialDeModeracion.Filtro.class);
        verify(historial).consultar(filtro.capture(), eq(2), eq(50));
        assertThat(filtro.getValue()).isEqualTo(new HistorialDeModeracion.Filtro(AccionDeModeracion.OCULTAR, "ada",
                Instant.parse("2026-10-01T05:00:00Z"), Instant.parse("2026-10-15T05:00:00Z")));
    }

    @Test
    @DisplayName("un jugador no lo lee (403) y sin token tampoco (401): el servicio ni se entera")
    void jugador() throws Exception {
        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer "
                        + emisor.tokenDeJugador("LyraRoja", UUID.randomUUID())))
                .andExpect(status().isForbidden());
        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
        verifyNoInteractions(historial);
    }

    @Test
    @DisplayName("una accion que no existe o una fecha que no es ISO es 400; un periodo al reves, tambien")
    void rechazos() throws Exception {
        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, token("MODERADOR")).param("accion", "BORRAR_TODO"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, token("MODERADOR")).param("desde", "ayer"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, token("MODERADOR"))
                        .param("desde", "2026-10-15T00:00:00Z").param("hasta", "2026-10-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("«desde» tiene que ser anterior a «hasta»"));
        verifyNoInteractions(historial);
    }
}
