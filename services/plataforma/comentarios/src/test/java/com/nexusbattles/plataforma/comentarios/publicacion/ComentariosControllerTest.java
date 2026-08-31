package com.nexusbattles.plataforma.comentarios.publicacion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nexusbattles.plataforma.comentarios.Comentario;
import com.nexusbattles.plataforma.comentarios.HiloDeComentarios;
import com.nexusbattles.plataforma.comentarios.HiloDeComentarios.MotivoDeRechazo;

/**
 * Pruebas del contrato HTTP de HU-COM-001: cada estado de respuesta del
 * contrato publicado en contracts/openapi/comentarios.yaml tiene su caso.
 */
@WebMvcTest(ComentariosController.class)
@Import(ManejadorErroresComentarios.class)
class ComentariosControllerTest {

    private static final String RUTA = "/api/v1/products/espada-del-alba/comments";

    private static final String CUERPO = """
            {
              "autorId": "jugador-1",
              "apodoAutor": "LyraRoja",
              "texto": "Muy buena espada",
              "imagenes": ["captura.jpg"],
              "estrellas": 4
            }
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ServicioDePublicacionDeComentarios servicio;

    private static Comentario comentario(Comentario.Estado estado, Integer estrellas) {
        return new Comentario(
                "com-1", "espada-del-alba", "jugador-1", "LyraRoja",
                "Muy buena espada", List.of("captura.jpg"), estrellas,
                Instant.parse("2026-08-30T03:00:00Z"), estado);
    }

    @Test
    @DisplayName("publicado responde 201 con el comentario completo")
    void publicadoResponde201() throws Exception {
        when(servicio.publicar(eq("espada-del-alba"), anyString(), anyString(),
                anyString(), any(), any()))
                .thenReturn(comentario(Comentario.Estado.PUBLICADO, 4));

        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PUBLICADO"))
                .andExpect(jsonPath("$.estrellas").value(4))
                .andExpect(jsonPath("$.apodoAutor").value("LyraRoja"));
    }

    @Test
    @DisplayName("retenido por el filtro responde 202, no 201")
    void retenidoResponde202() throws Exception {
        when(servicio.publicar(eq("espada-del-alba"), anyString(), anyString(),
                anyString(), any(), any()))
                .thenReturn(comentario(Comentario.Estado.EN_REVISION, 4));

        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.estado").value("EN_REVISION"));
    }

    @Test
    @DisplayName("autor silenciado responde 403 con el motivo en el problem detail")
    void silenciadoResponde403() throws Exception {
        when(servicio.publicar(eq("espada-del-alba"), anyString(), anyString(),
                anyString(), any(), any()))
                .thenThrow(new HiloDeComentarios.PublicacionRechazada(
                        MotivoDeRechazo.AUTOR_SILENCIADO,
                        "el autor tiene una sancion activa que le impide publicar"));

        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.motivo").value("AUTOR_SILENCIADO"));
    }

    @Test
    @DisplayName("imagen con formato no admitido responde 422 con el motivo")
    void imagenNoAdmitidaResponde422() throws Exception {
        when(servicio.publicar(eq("espada-del-alba"), anyString(), anyString(),
                anyString(), any(), any()))
                .thenThrow(new HiloDeComentarios.PublicacionRechazada(
                        MotivoDeRechazo.FORMATO_DE_IMAGEN_NO_ADMITIDO,
                        "el formato de la imagen virus.exe no esta admitido"));

        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.motivo").value("FORMATO_DE_IMAGEN_NO_ADMITIDO"));
    }

    @Test
    @DisplayName("los datos invalidos que rechaza el dominio responden 400")
    void datosInvalidosResponden400() throws Exception {
        when(servicio.publicar(eq("espada-del-alba"), anyString(), anyString(),
                anyString(), any(), any()))
                .thenThrow(new IllegalArgumentException(
                        "la calificacion debe estar entre 1 y 5 estrellas"));

        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isBadRequest());
    }
}
