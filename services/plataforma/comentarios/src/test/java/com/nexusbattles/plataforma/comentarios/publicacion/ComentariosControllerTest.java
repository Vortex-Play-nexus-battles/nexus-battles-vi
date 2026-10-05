package com.nexusbattles.plataforma.comentarios.publicacion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import com.nexusbattles.plataforma.comentarios.ResumenDeCalificaciones;
import com.nexusbattles.plataforma.comentarios.catalogo.CatalogoNoDisponible;
import com.nexusbattles.plataforma.comentarios.catalogo.ProductoInexistente;
import com.nexusbattles.plataforma.comentarios.seguridad.SecurityConfig;

/**
 * Pruebas del contrato HTTP de los comentarios (1.1.0 a 1.5.0): cada estado de
 * respuesta tiene su caso, y la identidad del autor se comprueba con tokens
 * reales —firmados y verificados contra un JWKS— no con un principal
 * inventado.
 */
@WebMvcTest(ComentariosController.class)
@Import({ManejadorErroresComentarios.class, SecurityConfig.class, DecodificadorDePrueba.class})
class ComentariosControllerTest {

    private static final String RUTA = "/api/v1/products/espada-del-alba/comments";
    private static final UUID UID_LYRA = UUID.fromString("7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55");
    private static final String IMAGEN = "3f1c2b4a-1111-4222-8333-944455566677";

    /**
     * Cuerpo de un cliente de la 1.0.0: manda autorId y apodoAutor. Traen a
     * proposito OTRA identidad, para afirmar que el servicio no los lee.
     */
    private static final String CUERPO = """
            {
              "autorId": "jugador-suplantado",
              "apodoAutor": "OtraPersona",
              "texto": "Muy buena espada",
              "imagenes": ["3f1c2b4a-1111-4222-8333-944455566677"],
              "estrellas": 4
            }
            """;

    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ServicioDePublicacionDeComentarios servicio;

    private static Comentario comentario(Comentario.Estado estado) {
        return new Comentario(
                "com-1", "espada-del-alba", UID_LYRA.toString(), "LyraRoja",
                "Muy buena espada", List.of(IMAGEN),
                Instant.parse("2026-08-30T03:00:00Z"), estado);
    }

    private static ServicioDePublicacionDeComentarios.Publicado publicado(
            Comentario.Estado estado, Integer estrellas) {
        return new ServicioDePublicacionDeComentarios.Publicado(comentario(estado), estrellas, false);
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
                    eq("Muy buena espada"), eq(List.of(IMAGEN)), eq(4)))
                    .thenReturn(publicado(Comentario.Estado.PUBLICADO, 4));

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
                    .thenReturn(publicado(Comentario.Estado.PUBLICADO, 4));

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
                    .thenReturn(publicado(Comentario.Estado.PUBLICADO, 4));

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
                    .thenReturn(publicado(Comentario.Estado.PUBLICADO, null));

            mvc.perform(post(RUTA)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeJugador("LyraRoja", UID_LYRA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"texto\": \"Solo texto\", \"imagenes\": []}"))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    @DisplayName("publicado responde 201 con el comentario completo, sus imagenes por id y las estrellas del autor")
    void publicadoResponde201() throws Exception {
        when(servicio.publicar(eq("espada-del-alba"), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(publicado(Comentario.Estado.PUBLICADO, 4));

        mvc.perform(publicarComoLyra())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PUBLICADO"))
                .andExpect(jsonPath("$.estrellas").value(4))
                .andExpect(jsonPath("$.imagenes[0]").value(IMAGEN))
                .andExpect(jsonPath("$.editado").value(false))
                .andExpect(jsonPath("$.marcado").doesNotExist())
                .andExpect(jsonPath("$.apodoAutor").value("LyraRoja"));
    }

    @Test
    @DisplayName("retenido por el filtro responde 202, no 201")
    void retenidoResponde202() throws Exception {
        when(servicio.publicar(eq("espada-del-alba"), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(publicado(Comentario.Estado.EN_REVISION, 4));

        mvc.perform(publicarComoLyra())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.estado").value("EN_REVISION"));
    }

    @Test
    @DisplayName("autor silenciado responde 403 con su tipo y el motivo en el problem detail")
    void silenciadoResponde403() throws Exception {
        when(servicio.publicar(eq("espada-del-alba"), anyString(), anyString(), anyString(), any(), any()))
                .thenThrow(new HiloDeComentarios.PublicacionRechazada(
                        MotivoDeRechazo.AUTOR_SILENCIADO,
                        "el autor tiene una sancion activa que le impide publicar"));

        mvc.perform(publicarComoLyra())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/autor-silenciado"))
                .andExpect(jsonPath("$.motivo").value("AUTOR_SILENCIADO"));
    }

    @Test
    @DisplayName("un nombre de archivo o una imagen ajena responde 400 imagenes-no-validas (1.4.0)")
    void imagenesNoValidasResponde400() throws Exception {
        when(servicio.publicar(eq("espada-del-alba"), anyString(), anyString(), anyString(), any(), any()))
                .thenThrow(new HiloDeComentarios.ImagenesNoValidas("no es un id de imagen"));

        mvc.perform(publicarComoLyra())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/imagenes-no-validas"));
    }

    @Test
    @DisplayName("un producto que no esta en el catalogo responde 404 producto-inexistente")
    void productoInexistenteResponde404() throws Exception {
        when(servicio.publicar(eq("espada-del-alba"), anyString(), anyString(), anyString(), any(), any()))
                .thenThrow(new ProductoInexistente("espada-del-alba"));

        mvc.perform(publicarComoLyra())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/producto-inexistente"));
    }

    @Test
    @DisplayName("sin catalogo no se publica a ciegas: 503 catalogo-no-disponible")
    void catalogoCaidoResponde503() throws Exception {
        when(servicio.publicar(eq("espada-del-alba"), anyString(), anyString(), anyString(), any(), any()))
                .thenThrow(new CatalogoNoDisponible("tiempo agotado"));

        mvc.perform(publicarComoLyra())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/catalogo-no-disponible"));
    }

    @Test
    @DisplayName("sin sanciones tampoco: 503 sanciones-no-disponibles")
    void sancionesCaidasResponde503() throws Exception {
        when(servicio.publicar(eq("espada-del-alba"), anyString(), anyString(), anyString(), any(), any()))
                .thenThrow(new SancionesNoDisponibles("caido"));

        mvc.perform(publicarComoLyra())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/sanciones-no-disponibles"));
    }

    @Test
    @DisplayName("la segunda calificación entra, la respuesta lo dice y trae las estrellas que ya tenia (D-07)")
    void calificacionDuplicadaEntraYSeDice() throws Exception {
        when(servicio.publicar(eq("espada-del-alba"), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new ServicioDePublicacionDeComentarios.Publicado(
                        comentario(Comentario.Estado.PUBLICADO), 5, true));

        mvc.perform(publicarComoLyra())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estrellas").value(5))
                .andExpect(jsonPath("$.calificacionDescartada").value(true));
    }

    /* HU-COM-004: retirar un comentario propio. */

    private static final String RUTA_COMENTARIO = RUTA + "/com-1";

    @Test
    @DisplayName("DELETE propio responde 204 y el autor es el uid del token, nunca el cuerpo")
    void eliminarPropioResponde204() throws Exception {
        when(servicio.eliminar("espada-del-alba", "com-1", UID_LYRA.toString()))
                .thenReturn(comentario(Comentario.Estado.ELIMINADO));

        mvc.perform(delete(RUTA_COMENTARIO)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeJugador("LyraRoja", UID_LYRA)))
                .andExpect(status().isNoContent());

        verify(servicio).eliminar("espada-del-alba", "com-1", UID_LYRA.toString());
    }

    @Test
    @DisplayName("DELETE sin token es 401 y no llega al servicio")
    void eliminarSinToken() throws Exception {
        mvc.perform(delete(RUTA_COMENTARIO)).andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("DELETE de un comentario ajeno es 403 con su tipo de problema")
    void eliminarAjenoResponde403() throws Exception {
        when(servicio.eliminar(eq("espada-del-alba"), eq("com-1"), anyString()))
                .thenThrow(new HiloDeComentarios.ComentarioAjeno("com-1"));

        mvc.perform(delete(RUTA_COMENTARIO)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeJugador("LyraRoja", UID_LYRA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/comentario-ajeno"));
    }

    @Test
    @DisplayName("DELETE de un comentario que no esta en el hilo es 404 con su tipo de problema")
    void eliminarInexistenteResponde404() throws Exception {
        when(servicio.eliminar(eq("espada-del-alba"), eq("com-1"), anyString()))
                .thenThrow(new HiloDeComentarios.ComentarioNoEncontrado("com-1"));

        mvc.perform(delete(RUTA_COMENTARIO)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeJugador("LyraRoja", UID_LYRA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/comentario-no-encontrado"));
    }

    @Test
    @DisplayName("los datos invalidos que rechaza el dominio responden 400")
    void datosInvalidosResponden400() throws Exception {
        when(servicio.publicar(eq("espada-del-alba"), anyString(), anyString(), anyString(), any(), any()))
                .thenThrow(new IllegalArgumentException("la calificacion va de 1 a 5 estrellas, llego 9"));

        mvc.perform(publicarComoLyra())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("una carrera que choca con otra restriccion de la base responde 409, nunca 500")
    void carreraResponde409() throws Exception {
        when(servicio.publicar(eq("espada-del-alba"), anyString(), anyString(), anyString(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("uk_algo"));

        mvc.perform(publicarComoLyra())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // ---- Lectura del hilo (#438, lado proveedor de HU-INV-014; paginado desde B3) ----

    private static ServicioDePublicacionDeComentarios.HiloConsultado hiloCon(
            List<Comentario> comentarios, Map<String, Integer> estrellas, int pagina, int tamano,
            long total, int totalPaginas, ResumenDeCalificaciones resumen) {
        return new ServicioDePublicacionDeComentarios.HiloConsultado(
                "espada-del-alba", comentarios, estrellas, pagina, tamano, total, totalPaginas, resumen);
    }

    @Test
    @DisplayName("el hilo es publico: 200 sin token, con la pagina, sus datos de paginacion y el resumen de la tabla")
    void hiloResponde200ConPaginacionYPromedio() throws Exception {
        when(servicio.consultarHilo("espada-del-alba", 0, 16)).thenReturn(hiloCon(
                List.of(comentario(Comentario.Estado.PUBLICADO)), Map.of(UID_LYRA.toString(), 4),
                0, 16, 1, 1, ResumenDeCalificaciones.de("espada-del-alba", Map.of(4, 1L, 5, 1L))));

        mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productoId").value("espada-del-alba"))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamano").value(16))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.totalPaginas").value(1))
                .andExpect(jsonPath("$.comentarios[0].id").value("com-1"))
                .andExpect(jsonPath("$.comentarios[0].estrellas").value(4))
                .andExpect(jsonPath("$.comentarios[0].calificacionDescartada").value(false))
                .andExpect(jsonPath("$.comentarios[0].marcado").doesNotExist())
                .andExpect(jsonPath("$.calificacionPromedio").value(4.5))
                .andExpect(jsonPath("$.totalCalificaciones").value(2));
    }

    @Test
    @DisplayName("pagina y tamano viajan al servicio tal cual los pide el cliente")
    void paginacionPedida() throws Exception {
        when(servicio.consultarHilo("espada-del-alba", 2, 5)).thenReturn(hiloCon(
                List.of(), Map.of(), 2, 5, 11, 3, ResumenDeCalificaciones.vacio("espada-del-alba")));

        mvc.perform(get(RUTA).param("pagina", "2").param("tamano", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(2))
                .andExpect(jsonPath("$.totalPaginas").value(3))
                .andExpect(jsonPath("$.total").value(11));
    }

    @Test
    @DisplayName("un producto sin comentarios responde 200 vacio y promedio nulo, nunca 404")
    void hiloVacioNoEs404() throws Exception {
        // Criterio CA-03 de HU-INV-014: "sin valoraciones -> estado vacio,
        // nunca error". No tener comentarios es normal, no un fallo.
        when(servicio.consultarHilo("espada-del-alba", 0, 16)).thenReturn(hiloCon(
                List.of(), Map.of(), 0, 16, 0, 0, ResumenDeCalificaciones.vacio("espada-del-alba")));

        mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comentarios").isEmpty())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.totalPaginas").value(0))
                .andExpect(jsonPath("$.calificacionPromedio").value((Object) null))
                .andExpect(jsonPath("$.totalCalificaciones").value(0));
    }

    @Test
    @DisplayName("una pagina que no es un numero es 400, no 500")
    void paginaNoNumerica() throws Exception {
        mvc.perform(get(RUTA).param("pagina", "primera"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(servicio);
    }

    /**
     * G4 (contrato 1.9.0): el hilo lo lee cualquiera, tambien sin cuenta, asi
     * que no publica el {@code uid} de nadie; lo que el cliente hacia con el
     * —reconocer los suyos— lo dice el servidor en {@code propio}.
     */
    @Nested
    @DisplayName("G4 — el hilo publico no publica el uid de nadie")
    class SinUidEnElHilo {

        private void unComentarioDeLyra() {
            when(servicio.consultarHilo("espada-del-alba", 0, 16)).thenReturn(hiloCon(
                    List.of(comentario(Comentario.Estado.PUBLICADO)), Map.of(UID_LYRA.toString(), 4),
                    0, 16, 1, 1, ResumenDeCalificaciones.vacio("espada-del-alba")));
        }

        @Test
        @DisplayName("sin token: el apodo si, el uid no, y nada es propio")
        void anonimo() throws Exception {
            unComentarioDeLyra();

            mvc.perform(get(RUTA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.comentarios[0].apodoAutor").value("LyraRoja"))
                    .andExpect(jsonPath("$.comentarios[0].autorId").doesNotExist())
                    .andExpect(jsonPath("$.comentarios[0].propio").value(false));
        }

        @Test
        @DisplayName("su autora lo ve propio, sin que el uid viaje")
        void suAutora() throws Exception {
            unComentarioDeLyra();

            mvc.perform(get(RUTA)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeJugador("LyraRoja", UID_LYRA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.comentarios[0].autorId").doesNotExist())
                    .andExpect(jsonPath("$.comentarios[0].propio").value(true));
        }

        @Test
        @DisplayName("otra jugadora no lo ve propio, ni recibe el uid de la autora")
        void otraJugadora() throws Exception {
            unComentarioDeLyra();

            String cuerpo = mvc.perform(get(RUTA)
                            .header(HttpHeaders.AUTHORIZATION,
                                    "Bearer " + emisor.tokenDeJugador("Bruma", UUID.randomUUID())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.comentarios[0].autorId").doesNotExist())
                    .andExpect(jsonPath("$.comentarios[0].propio").value(false))
                    .andReturn().getResponse().getContentAsString();
            org.assertj.core.api.Assertions.assertThat(cuerpo).doesNotContain(UID_LYRA.toString());
        }

        @Test
        @DisplayName("un token de servicio lee el hilo (no es error) y no tiene comentarios propios")
        void tokenDeServicio() throws Exception {
            unComentarioDeLyra();

            mvc.perform(get(RUTA)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeServicio("ms-chatbot")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.comentarios[0].autorId").doesNotExist())
                    .andExpect(jsonPath("$.comentarios[0].propio").value(false));
        }

        @Test
        @DisplayName("la respuesta de publicar si lleva el uid: es el de quien publica, y es suyo")
        void publicarConservaElUidPropio() throws Exception {
            when(servicio.publicar(eq("espada-del-alba"), eq(UID_LYRA.toString()), eq("LyraRoja"),
                    anyString(), any(), any()))
                    .thenReturn(publicado(Comentario.Estado.PUBLICADO, 4));

            mvc.perform(publicarComoLyra())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.autorId").value(UID_LYRA.toString()))
                    .andExpect(jsonPath("$.propio").value(true));
        }

        @Test
        @DisplayName("uidDeQuienMira: null sin token y con un token que no identifica a un jugador")
        void uidDeQuienMira() {
            org.assertj.core.api.Assertions.assertThat(ComentariosController.uidDeQuienMira(null)).isNull();
            org.springframework.security.oauth2.jwt.Jwt sinUid = org.springframework.security.oauth2.jwt.Jwt
                    .withTokenValue("t").header("alg", "none").subject("ms-chatbot").build();
            org.assertj.core.api.Assertions.assertThat(ComentariosController.uidDeQuienMira(sinUid)).isNull();
        }
    }
}
