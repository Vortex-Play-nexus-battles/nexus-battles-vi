package com.nexusbattles.plataforma.comentarios.moderacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.nexusbattles.comun.seguridad.pruebas.DecodificadorDePrueba;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.plataforma.comentarios.Comentario;
import com.nexusbattles.plataforma.comentarios.publicacion.ManejadorErroresComentarios;
import com.nexusbattles.plataforma.comentarios.seguridad.SecurityConfig;

/**
 * La mitad HTTP del flujo de moderacion — R10.1 (RF-COM-005, 006 y 008).
 *
 * <h2>Que prueba esto que no pruebe {@link FlujoDeModeracionTest}</h2>
 *
 * <p>Aquella prueba recorre el flujo sobre el servicio: que un comentario
 * reportado entra en la cola y sale de ella. Esta prueba mira lo que el
 * servicio NO puede mirar desde dentro: <b>quien tiene permiso para entrar por
 * cada puerta</b> y <b>de donde sale la identidad de quien actua</b>.
 *
 * <p>Las dos cosas son la sustancia de RF-COM-008, no un detalle de
 * transporte. Un flujo de moderacion impecable donde el jugador denunciado
 * pueda llamar a {@code /decision} y archivar su propio caso no modera nada; y
 * un asiento de auditoria que diga quien decidio a partir de un campo del
 * cuerpo es una firma que cualquiera puede falsificar. Por eso los tokens son
 * reales —firmados y verificados contra un JWKS— y no un principal inventado:
 * un {@code @WithMockUser} habria dado por buena la parte que justamente hay
 * que comprobar.
 *
 * <p>Se cargan los dos controladores juntos porque son las dos mitades de un
 * mismo flujo: quien reporta no decide, y quien decide no necesita reportar.
 * Separarlos en dos pruebas habria escondido justo esa frontera.
 */
@WebMvcTest({ReportesController.class, ModeracionController.class})
@Import({ManejadorErroresComentarios.class, SecurityConfig.class, DecodificadorDePrueba.class})
@DisplayName("R10.1: quien reporta no decide, y quien decide sale del token")
class ModeracionHttpTest {

    private static final String PRODUCTO = "espada-del-alba";
    private static final String COMENTARIO = "com-1";
    private static final String RUTA_REPORTES =
            "/api/v1/products/" + PRODUCTO + "/comments/" + COMENTARIO + "/reportes";
    private static final String RUTA_COLA = "/api/v1/comentarios/moderacion";
    private static final String RUTA_DECISION = RUTA_COLA + "/" + COMENTARIO + "/decision";

    private static final UUID UID_LYRA = UUID.fromString("7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55");
    private static final UUID UID_MODERADORA = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Instant CUANDO = Instant.parse("2026-09-23T10:00:00Z");

    /**
     * Cuerpo de un reporte que ademas intenta decir quien lo firma. El
     * contrato 1.3.0 no declara esos campos: viajan aqui a proposito, para
     * afirmar que el servicio recibe el {@code uid} del token y no lo que
     * traiga el cliente.
     */
    private static final String CUERPO_REPORTE = """
            {
              "reportanteId": "otro-cualquiera",
              "categoria": "CONTENIDO_OFENSIVO",
              "descripcion": "Insulta a otro jugador"
            }
            """;

    private static final String CUERPO_DECISION = """
            {
              "moderadorId": "moderador-suplantado",
              "apodoModerador": "OtraPersona",
              "accion": "OCULTAR",
              "motivo": "Incumple la norma de convivencia"
            }
            """;

    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ServicioDeModeracion servicio;

    // ------------------------------------------------------------- utilidades

    private static Comentario comentario(Comentario.Estado estado) {
        return new Comentario(COMENTARIO, PRODUCTO, UID_LYRA.toString(), "LyraRoja",
                "Un texto cualquiera", List.of(), 4, CUANDO.minusSeconds(3600), estado);
    }

    private static RegistroDeReporte reporte(CategoriaDeReporte categoria) {
        return new RegistroDeReporte("rep-1", COMENTARIO, UID_LYRA.toString(),
                categoria, "Insulta a otro jugador", CUANDO);
    }

    private static AsientoDeModeracion asiento() {
        return new AsientoDeModeracion("asi-1", COMENTARIO, UID_MODERADORA.toString(),
                "AdaLaJusta", AccionDeModeracion.OCULTAR, "Incumple la norma de convivencia",
                Comentario.Estado.EN_REVISION, Comentario.Estado.OCULTO, CUANDO);
    }

    private String comoJugadora() {
        return "Bearer " + emisor.tokenDeJugador("LyraRoja", UID_LYRA);
    }

    private String comoModeradora() {
        return "Bearer " + emisor.tokenDeUsuario("AdaLaJusta", UID_MODERADORA, "MODERADOR");
    }

    private MockHttpServletRequestBuilder reportarCon(String credencial) {
        MockHttpServletRequestBuilder peticion = post(RUTA_REPORTES)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CUERPO_REPORTE);
        return credencial == null ? peticion : peticion.header(HttpHeaders.AUTHORIZATION, credencial);
    }

    // ---------------------------------------------------------- RF-COM-006

    @Nested
    @DisplayName("reportar (RF-COM-006)")
    class Reportar {

        @Test
        @DisplayName("una jugadora reporta: 201, y el reportante es el uid del token aunque el cuerpo diga otro")
        void reportanteSaleDelToken() throws Exception {
            when(servicio.reportar(eq(PRODUCTO), eq(COMENTARIO), eq(UID_LYRA.toString()),
                    eq(CategoriaDeReporte.CONTENIDO_OFENSIVO), anyString()))
                    .thenReturn(new ServicioDeModeracion.Reportado(
                            reporte(CategoriaDeReporte.CONTENIDO_OFENSIVO),
                            comentario(Comentario.Estado.EN_REVISION), 1L));

            mvc.perform(reportarCon(comoJugadora()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value("rep-1"))
                    .andExpect(jsonPath("$.categoria").value("CONTENIDO_OFENSIVO"))
                    .andExpect(jsonPath("$.estadoDelComentario").value("EN_REVISION"))
                    .andExpect(jsonPath("$.reportesTotales").value(1));

            // La afirmacion que importa: "otro-cualquiera" no llego al servicio.
            verify(servicio).reportar(eq(PRODUCTO), eq(COMENTARIO), eq(UID_LYRA.toString()),
                    any(), anyString());
        }

        @Test
        @DisplayName("sin token no se reporta: 401 y el servicio ni se entera")
        void sinTokenEs401() throws Exception {
            mvc.perform(reportarCon(null)).andExpect(status().isUnauthorized());
            verifyNoInteractions(servicio);
        }

        @Test
        @DisplayName("un token de servicio (ADR-005) no puede reportar: no identifica a ninguna persona")
        void tokenDeServicioEs403() throws Exception {
            mvc.perform(reportarCon("Bearer " + emisor.tokenDeServicio("ms-subastas")))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(servicio);
        }

        @Test
        @DisplayName("reportar dos veces lo mismo es 409 con motivo estable, no un 500")
        void duplicadoEs409() throws Exception {
            when(servicio.reportar(anyString(), anyString(), anyString(), any(), anyString()))
                    .thenThrow(new ServicioDeModeracion.ReporteDuplicado(COMENTARIO));

            mvc.perform(reportarCon(comoJugadora()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.motivo").value("REPORTE_DUPLICADO"));
        }

        @Test
        @DisplayName("agotado el limite diario es 429 con motivo, no un 400 mudo")
        void limiteEs429() throws Exception {
            when(servicio.reportar(anyString(), anyString(), anyString(), any(), anyString()))
                    .thenThrow(new ServicioDeModeracion.LimiteDeReportesAgotado(20));

            mvc.perform(reportarCon(comoJugadora()))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.motivo").value("LIMITE_DE_REPORTES"));
        }
    }

    // ---------------------------------------------------- RF-COM-005 y 008

    @Nested
    @DisplayName("la cola y la decision (RF-COM-005 y RF-COM-008)")
    class Moderar {

        @Test
        @DisplayName("una jugadora NO ve la cola ni resuelve: 403 en las dos puertas")
        void jugadoraNoModera() throws Exception {
            mvc.perform(get(RUTA_COLA).header(HttpHeaders.AUTHORIZATION, comoJugadora()))
                    .andExpect(status().isForbidden());

            mvc.perform(post(RUTA_DECISION)
                            .header(HttpHeaders.AUTHORIZATION, comoJugadora())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CUERPO_DECISION))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(servicio);
        }

        @Test
        @DisplayName("la cola vacia es 200 con lista vacia, no 404: no tener trabajo es una respuesta")
        void colaVaciaEs200() throws Exception {
            when(servicio.cola(any(), anyInt(), anyInt()))
                    .thenReturn(new ServicioDeModeracion.Cola(List.of(), 0, 0, 20));

            mvc.perform(get(RUTA_COLA).header(HttpHeaders.AUTHORIZATION, comoModeradora()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.entradas").isArray())
                    .andExpect(jsonPath("$.entradas").isEmpty())
                    .andExpect(jsonPath("$.total").value(0));
        }

        @Test
        @DisplayName("la cola trae el comentario, su recuento por categoria y el primer reporte")
        void colaConEntrada() throws Exception {
            when(servicio.cola(eq(PRODUCTO), eq(0), anyInt())).thenReturn(
                    new ServicioDeModeracion.Cola(List.of(new ServicioDeModeracion.Entrada(
                            comentario(Comentario.Estado.EN_REVISION), 2,
                            Map.of(CategoriaDeReporte.ACOSO, 2L), CUANDO)), 1, 0, 20));

            mvc.perform(get(RUTA_COLA)
                            .param("productoId", PRODUCTO)
                            .header(HttpHeaders.AUTHORIZATION, comoModeradora()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.entradas[0].comentario.id").value(COMENTARIO))
                    .andExpect(jsonPath("$.entradas[0].comentario.estado").value("EN_REVISION"))
                    .andExpect(jsonPath("$.entradas[0].reportes").value(2))
                    .andExpect(jsonPath("$.entradas[0].porCategoria.ACOSO").value(2))
                    .andExpect(jsonPath("$.entradas[0].primerReporte").value(CUANDO.toString()));
        }

        @Test
        @DisplayName("el tamano de pagina se recorta a 100: el cliente no decide cuanto carga el servidor")
        void tamanoSeRecorta() throws Exception {
            when(servicio.cola(any(), anyInt(), anyInt()))
                    .thenReturn(new ServicioDeModeracion.Cola(List.of(), 0, 0, 100));

            mvc.perform(get(RUTA_COLA)
                            .param("tamano", "5000")
                            .header(HttpHeaders.AUTHORIZATION, comoModeradora()))
                    .andExpect(status().isOk());

            verify(servicio).cola(null, 0, 100);
        }

        @Test
        @DisplayName("el detalle trae el comentario, sus reportes y su historial")
        void detalle() throws Exception {
            when(servicio.detalle(COMENTARIO)).thenReturn(new ServicioDeModeracion.Detalle(
                    comentario(Comentario.Estado.EN_REVISION),
                    List.of(reporte(CategoriaDeReporte.SPAM)),
                    List.of(asiento())));

            mvc.perform(get(RUTA_COLA + "/" + COMENTARIO)
                            .header(HttpHeaders.AUTHORIZATION, comoModeradora()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.comentario.id").value(COMENTARIO))
                    .andExpect(jsonPath("$.reportes[0].categoria").value("SPAM"))
                    .andExpect(jsonPath("$.historial[0].accion").value("OCULTAR"))
                    .andExpect(jsonPath("$.historial[0].estadoAnterior").value("EN_REVISION"))
                    .andExpect(jsonPath("$.historial[0].estadoNuevo").value("OCULTO"));
        }

        @Test
        @DisplayName("un comentario que no existe es 404, tambien para el moderador")
        void detalleInexistenteEs404() throws Exception {
            when(servicio.detalle(anyString()))
                    .thenThrow(new ServicioDeModeracion.ComentarioNoEncontrado(COMENTARIO));

            mvc.perform(get(RUTA_COLA + "/" + COMENTARIO)
                            .header(HttpHeaders.AUTHORIZATION, comoModeradora()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("quien firma la decision es el token: el moderadorId del cuerpo se ignora")
        void elModeradorSaleDelToken() throws Exception {
            when(servicio.resolver(anyString(), anyString(), anyString(), any(), anyString()))
                    .thenReturn(new ServicioDeModeracion.Resuelto(
                            comentario(Comentario.Estado.OCULTO), asiento(), true));

            mvc.perform(post(RUTA_DECISION)
                            .header(HttpHeaders.AUTHORIZATION, comoModeradora())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CUERPO_DECISION))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.comentario.estado").value("OCULTO"))
                    .andExpect(jsonPath("$.asiento.accion").value("OCULTAR"))
                    .andExpect(jsonPath("$.asiento.moderadorId").value(UID_MODERADORA.toString()))
                    .andExpect(jsonPath("$.autorNotificado").value(true));

            ArgumentCaptor<String> id = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> apodo = ArgumentCaptor.forClass(String.class);
            verify(servicio).resolver(eq(COMENTARIO), id.capture(), apodo.capture(),
                    eq(AccionDeModeracion.OCULTAR), anyString());

            org.junit.jupiter.api.Assertions.assertEquals(UID_MODERADORA.toString(), id.getValue());
            org.junit.jupiter.api.Assertions.assertEquals("AdaLaJusta", apodo.getValue());
        }

        @Test
        @DisplayName("una decision sin motivo es 400: no se archiva nada sin decir por que")
        void sinMotivoEs400() throws Exception {
            when(servicio.resolver(anyString(), anyString(), anyString(), any(), any()))
                    .thenThrow(new ServicioDeModeracion.MotivoRequerido());

            mvc.perform(post(RUTA_DECISION)
                            .header(HttpHeaders.AUTHORIZATION, comoModeradora())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"accion\":\"OCULTAR\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("si otro moderador se adelanto, es 409 con motivo: la pantalla estaba vieja")
        void transicionInvalidaEs409() throws Exception {
            when(servicio.resolver(anyString(), anyString(), anyString(), any(), anyString()))
                    .thenThrow(new ServicioDeModeracion.TransicionInvalida(
                            AccionDeModeracion.OCULTAR, Comentario.Estado.ELIMINADO));

            mvc.perform(post(RUTA_DECISION)
                            .header(HttpHeaders.AUTHORIZATION, comoModeradora())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CUERPO_DECISION))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.motivo").value("TRANSICION_INVALIDA"));
        }

        @Test
        @DisplayName("un ADMINISTRADOR tambien modera: la lista de roles no deja fuera a quien manda")
        void administradorTambienModera() throws Exception {
            when(servicio.cola(any(), anyInt(), anyInt()))
                    .thenReturn(new ServicioDeModeracion.Cola(List.of(), 0, 0, 20));

            mvc.perform(get(RUTA_COLA).header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + emisor.tokenDeUsuario("Jefa", UID_MODERADORA, "ADMINISTRADOR")))
                    .andExpect(status().isOk());
        }
    }
}
