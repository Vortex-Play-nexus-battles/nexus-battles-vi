package com.nexusbattles.plataforma.comentarios.calificacion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
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
import com.nexusbattles.plataforma.comentarios.Calificacion;
import com.nexusbattles.plataforma.comentarios.HiloDeComentarios;
import com.nexusbattles.plataforma.comentarios.ResumenDeCalificaciones;
import com.nexusbattles.plataforma.comentarios.catalogo.CatalogoNoDisponible;
import com.nexusbattles.plataforma.comentarios.catalogo.ProductoInexistente;
import com.nexusbattles.plataforma.comentarios.publicacion.ManejadorErroresComentarios;
import com.nexusbattles.plataforma.comentarios.seguridad.SecurityConfig;

/**
 * {@code /products/{productId}/rating} por HTTP — contrato 1.4.0, B3: quien
 * puede entrar, de donde sale el autor y como se ve cada respuesta. Tokens
 * reales, firmados y verificados contra un JWKS.
 */
@WebMvcTest(CalificacionesController.class)
@Import({ManejadorErroresComentarios.class, SecurityConfig.class, DecodificadorDePrueba.class})
class CalificacionesControllerTest {

    private static final String PRODUCTO = "1647b2ea-096d-37e7-b580-0172e4c62313";
    private static final String RUTA = "/api/v1/products/" + PRODUCTO + "/rating";
    private static final UUID UID_LYRA = UUID.fromString("7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55");
    private static final Instant CUANDO = Instant.parse("2026-09-25T12:00:00Z");

    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ServicioDeCalificaciones servicio;

    private String comoLyra() {
        return "Bearer " + emisor.tokenDeJugador("LyraRoja", UID_LYRA);
    }

    @Test
    @DisplayName("el resumen es publico: sin token, con promedio, total y las cinco claves de la distribucion")
    void resumenPublico() throws Exception {
        when(servicio.resumenPublico(PRODUCTO))
                .thenReturn(ResumenDeCalificaciones.de(PRODUCTO, Map.of(5, 2L, 4, 1L)));

        mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productoId").value(PRODUCTO))
                .andExpect(jsonPath("$.promedio").value(4.7))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.distribucion['1']").value(0))
                .andExpect(jsonPath("$.distribucion['4']").value(1))
                .andExpect(jsonPath("$.distribucion['5']").value(2));
    }

    @Test
    @DisplayName("sin calificaciones: promedio null y total 0, nunca 404")
    void resumenVacio() throws Exception {
        when(servicio.resumenPublico(PRODUCTO)).thenReturn(ResumenDeCalificaciones.vacio(PRODUCTO));

        mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promedio").value((Object) null))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.distribucion['3']").value(0));
    }

    @Test
    @DisplayName("el resumen de un producto que no existe es 404 producto-inexistente")
    void resumenDeInexistente() throws Exception {
        when(servicio.resumenPublico(PRODUCTO)).thenThrow(new ProductoInexistente(PRODUCTO));

        mvc.perform(get(RUTA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/producto-inexistente"));
    }

    @Test
    @DisplayName("calificar: 201 con la calificacion y el resumen; el autor es el uid del token")
    void calificar() throws Exception {
        when(servicio.calificar(PRODUCTO, UID_LYRA.toString(), 4)).thenReturn(new ServicioDeCalificaciones.Calificado(
                new Calificacion("c-1", PRODUCTO, UID_LYRA.toString(), 4, CUANDO),
                ResumenDeCalificaciones.de(PRODUCTO, Map.of(4, 1L))));

        mvc.perform(post(RUTA)
                        .header(HttpHeaders.AUTHORIZATION, comoLyra())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estrellas\": 4, \"autorId\": \"suplantado\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productoId").value(PRODUCTO))
                .andExpect(jsonPath("$.estrellas").value(4))
                .andExpect(jsonPath("$.fecha").value(CUANDO.toString()))
                .andExpect(jsonPath("$.resumen.promedio").value(4.0))
                .andExpect(jsonPath("$.resumen.total").value(1));

        verify(servicio).calificar(PRODUCTO, UID_LYRA.toString(), 4);
    }

    @Test
    @DisplayName("la segunda vez es 409 ya-calificado con motivo CALIFICACION_DUPLICADA")
    void segundaVez() throws Exception {
        when(servicio.calificar(eq(PRODUCTO), anyString(), any())).thenThrow(new YaCalificado(PRODUCTO));

        mvc.perform(post(RUTA)
                        .header(HttpHeaders.AUTHORIZATION, comoLyra())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estrellas\": 5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/ya-calificado"))
                .andExpect(jsonPath("$.motivo").value("CALIFICACION_DUPLICADA"));
    }

    @Test
    @DisplayName("estrellas fuera de rango 400, sancionado 403, producto inexistente 404, catalogo caido 503")
    void rechazos() throws Exception {
        when(servicio.calificar(eq(PRODUCTO), anyString(), eq(9)))
                .thenThrow(new IllegalArgumentException("la calificacion va de 1 a 5 estrellas, llego 9"));
        when(servicio.calificar(eq(PRODUCTO), anyString(), eq(1))).thenThrow(new HiloDeComentarios.PublicacionRechazada(
                HiloDeComentarios.MotivoDeRechazo.AUTOR_SILENCIADO, "sancion activa"));
        when(servicio.calificar(eq(PRODUCTO), anyString(), eq(2))).thenThrow(new ProductoInexistente(PRODUCTO));
        when(servicio.calificar(eq(PRODUCTO), anyString(), eq(3))).thenThrow(new CatalogoNoDisponible("caido"));

        mvc.perform(post(RUTA).header(HttpHeaders.AUTHORIZATION, comoLyra())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estrellas\": 9}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(RUTA).header(HttpHeaders.AUTHORIZATION, comoLyra())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estrellas\": 1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.motivo").value("AUTOR_SILENCIADO"));
        mvc.perform(post(RUTA).header(HttpHeaders.AUTHORIZATION, comoLyra())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estrellas\": 2}"))
                .andExpect(status().isNotFound());
        mvc.perform(post(RUTA).header(HttpHeaders.AUTHORIZATION, comoLyra())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estrellas\": 3}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/catalogo-no-disponible"));
    }

    @Test
    @DisplayName("sin sesion no se califica (401) y un token de servicio no es una persona (403)")
    void seguridad() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("{\"estrellas\": 4}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(RUTA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeServicio("ms-subastas"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estrellas\": 4}"))
                .andExpect(status().isForbidden());
        mvc.perform(get(RUTA + "/mia")).andExpect(status().isUnauthorized());

        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("la propia: 200 sin resumen; 404 si todavia no califico")
    void propia() throws Exception {
        when(servicio.de(PRODUCTO, UID_LYRA.toString())).thenReturn(Optional.of(
                new Calificacion("c-1", PRODUCTO, UID_LYRA.toString(), 3, CUANDO)));

        mvc.perform(get(RUTA + "/mia").header(HttpHeaders.AUTHORIZATION, comoLyra()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estrellas").value(3))
                .andExpect(jsonPath("$.resumen").doesNotExist());

        UUID otra = UUID.randomUUID();
        when(servicio.de(PRODUCTO, otra.toString())).thenReturn(Optional.empty());
        mvc.perform(get(RUTA + "/mia").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + emisor.tokenDeJugador("Otra", otra)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/calificacion-no-encontrada"));
    }
}
