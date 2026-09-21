package com.nexusbattles.plataforma.comentarios.publicacion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.nexusbattles.comun.seguridad.pruebas.DecodificadorDePrueba;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.plataforma.comentarios.Comentario;
import com.nexusbattles.plataforma.comentarios.HiloDeComentarios;
import com.nexusbattles.plataforma.comentarios.HiloDeComentarios.MotivoDeRechazo;
import com.nexusbattles.plataforma.comentarios.seguridad.SecurityConfig;

/**
 * Pruebas del contrato HTTP de HU-COM-001 (contrato 1.1.0): cada estado de
 * respuesta tiene su caso, y la identidad del autor se comprueba con tokens
 * reales —firmados y verificados contra un JWKS— no con un principal
 * inventado.
 */
@WebMvcTest(ComentariosController.class)
@Import({ManejadorErroresComentarios.class, SecurityConfig.class, DecodificadorDePrueba.class})
class ComentariosControllerTest {

    private static final String RUTA = "/api/v1/products/espada-del-alba/comments";
    private static final UUID UID_LYRA = UUID.fromString("7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55");

    /**
     * Cuerpo de un cliente de la 1.0.0: manda autorId y apodoAutor. Traen a
     * proposito OTRA identidad, para afirmar que el servicio no los lee.
     */
    private static final String CUERPO = """
            {
              "autorId": "jugador-suplantado",
              "apodoAutor": "OtraPersona",
              "texto": "Muy buena espada",
              "imagenes": ["captura.jpg"],
              "estrellas": 4
            }
            """;

    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ServicioDePublicacionDeComentarios servicio;

    private static Comentario comentario(Comentario.Estado estado, Integer estrellas) {
        return new Comentario(
                "com-1", "espada-del-alba", UID_LYRA.toString(), "LyraRoja",
                "Muy buena espada", List.of("captura.jpg"), estrellas,
                Instant.parse("2026-08-30T03:00:00Z"), estado);
    }

    private MockHttpServletRequestBuilder publicarComoLyra() {
        return post(RUTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeJugador("LyraRoja", UID_LYRA))
                .contentType(MediaType.APPLICATION_JSON)
                .content(CUERPO);
    }

    @Nested
    @DisplayName("identidad del autor")
    class Identidad {

        @Test
        @DisplayName("el autor y su apodo salen del token, aunque el cuerpo diga otra cosa")
        void elAutorSaleDelToken() throws Exception {
            when(servicio.publicar(eq("espada-del-alba"), eq(UID_LYRA.toString()), eq("LyraRoja"),
                    eq("Muy buena espada"), eq(List.of("captura.jpg")), eq(4)))
                    .thenReturn(comentario(Comentario.Estado.PUBLICADO, 4));

            mvc.perform(publicarComoLyra())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.autorId").value(UID_LYRA.toString()))
                    .andExpect(jsonPath("$.apodoAutor").value("LyraRoja"));

            verify(servicio).publicar(eq("espada-del-alba"), eq(UID_LYRA.toString()), eq("LyraRoja"),
                    anyString(), any(), any());
        }

        @Test
        @DisplayName("sin token no se publica: 401, y el servicio ni se entera")
        void sinTokenEs401() throws Exception {
            mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE));

            verifyNoInteractions(servicio);
        }

        @Test
        @DisplayName("un token caducado o firmado por otro emisor es 401: no vale con que parezca un JWT")
        void tokenInvalidoEs401() throws Exception {
            mvc.perform(post(RUTA)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenCaducado("LyraRoja", UID_LYRA))
                            .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                    .andExpect(status().isUnauthorized());

            mvc.perform(post(RUTA)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenFirmadoPorOtro("LyraRoja", UID_LYRA))
                            .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(servicio);
        }

        @Test
        @DisplayName("un token de servicio (ADR-005) no puede ser autor: 403")
        void tokenDeServicioEs403() throws Exception {
            mvc.perform(post(RUTA)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeServicio("ms-subastas"))
                            .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(servicio);
        }

        @Test
        @DisplayName("un moderador tambien puede comentar: cualquiera de los roles de usuario")
        void moderadorPuedeComentar() throws Exception {
            UUID uid = UUID.randomUUID();
            when(servicio.publicar(eq("espada-del-alba"), eq(uid.toString()), eq("Mod_Ana"),
                    anyString(), any(), any()))
                    .thenReturn(comentario(Comentario.Estado.PUBLICADO, 4));

            mvc.perform(post(RUTA)
                            .header(HttpHeaders.AUTHORIZATION,
                                    "Bearer " + emisor.tokenDeUsuario("Mod_Ana", uid, "MODERADOR"))
                            .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("con la forma de Keycloak (sujeto estable, preferred_username) tambien se identifica al autor")
        void tokenDeKeycloak() throws Exception {
            UUID sujeto = UUID.randomUUID();
            when(servicio.publicar(eq("espada-del-alba"), eq(sujeto.toString()), eq("ana"),
                    anyString(), any(), any()))
                    .thenReturn(comentario(Comentario.Estado.PUBLICADO, 4));

            mvc.perform(post(RUTA)
                            .header(HttpHeaders.AUTHORIZATION,
                                    "Bearer " + emisor.tokenDeKeycloak(sujeto, "ana", List.of("JUGADOR")))
                            .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("el cuerpo de la 1.1.0 no necesita autorId ni apodoAutor")
        void cuerpoSinIdentidad() throws Exception {
            when(servicio.publicar(eq("espada-del-alba"), eq(UID_LYRA.toString()), eq("LyraRoja"),
                    eq("Solo texto"), any(), any()))
                    .thenReturn(comentario(Comentario.Estado.PUBLICADO, null));

            mvc.perform(post(RUTA)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeJugador("LyraRoja", UID_LYRA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"texto\": \"Solo texto\", \"imagenes\": []}"))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    @DisplayName("publicado responde 201 con el comentario completo")
    void publicadoResponde201() throws Exception {
        when(servicio.publicar(eq("espada-del-alba"), anyString(), anyString(),
                anyString(), any(), any()))
                .thenReturn(comentario(Comentario.Estado.PUBLICADO, 4));

        mvc.perform(publicarComoLyra())
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

        mvc.perform(publicarComoLyra())
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

        mvc.perform(publicarComoLyra())
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

        mvc.perform(publicarComoLyra())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.motivo").value("FORMATO_DE_IMAGEN_NO_ADMITIDO"));
    }

    @Test
    @DisplayName("calificación duplicada rechazada por dominio responde 409 con el motivo")
    void calificacionDuplicadaResponde409() throws Exception {
        when(servicio.publicar(eq("espada-del-alba"), anyString(), anyString(),
                anyString(), any(), any()))
                .thenThrow(new HiloDeComentarios.PublicacionRechazada(
                        MotivoDeRechazo.CALIFICACION_DUPLICADA,
                        "Ya calificaste este producto"));

        mvc.perform(publicarComoLyra())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.motivo").value("CALIFICACION_DUPLICADA"));
    }

    @Test
    @DisplayName("los datos invalidos que rechaza el dominio responden 400")
    void datosInvalidosResponden400() throws Exception {
        when(servicio.publicar(eq("espada-del-alba"), anyString(), anyString(),
                anyString(), any(), any()))
                .thenThrow(new IllegalArgumentException(
                        "la calificacion debe estar entre 1 y 5 estrellas"));

        mvc.perform(publicarComoLyra())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("la carrera de dos calificaciones simultaneas responde 409")
    void carreraDeCalificacionesResponde409() throws Exception {
        when(servicio.publicar(eq("espada-del-alba"), anyString(), anyString(),
                anyString(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("uk_calificacion_unica_por_autor"));

        mvc.perform(publicarComoLyra())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // ---- Lectura del hilo (#438, lado proveedor de HU-INV-014) ----

    @Test
    @DisplayName("el hilo de un producto es publico: responde 200 sin token, con sus comentarios y el promedio")
    void hiloResponde200ConPromedio() throws Exception {
        when(servicio.consultarHilo("espada-del-alba"))
                .thenReturn(new ServicioDePublicacionDeComentarios.HiloConsultado(
                        "espada-del-alba",
                        List.of(comentario(Comentario.Estado.PUBLICADO, 4)),
                        OptionalDouble.of(4.0),
                        1));

        mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productoId").value("espada-del-alba"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.comentarios[0].id").value("com-1"))
                .andExpect(jsonPath("$.comentarios[0].apodoAutor").value("LyraRoja"))
                .andExpect(jsonPath("$.calificacionPromedio").value(4.0))
                .andExpect(jsonPath("$.totalCalificaciones").value(1));
    }

    @Test
    @DisplayName("un producto sin comentarios responde 200 vacio y promedio nulo, nunca 404")
    void hiloVacioNoEs404() throws Exception {
        // Criterio CA-03 de HU-INV-014: "sin valoraciones -> estado vacio,
        // nunca error". No tener comentarios es normal, no un fallo.
        when(servicio.consultarHilo("espada-del-alba"))
                .thenReturn(new ServicioDePublicacionDeComentarios.HiloConsultado(
                        "espada-del-alba", List.of(), OptionalDouble.empty(), 0));

        mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comentarios").isEmpty())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.calificacionPromedio").value((Object) null))
                .andExpect(jsonPath("$.totalCalificaciones").value(0));
    }
}
