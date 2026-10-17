package com.nexusbattles.plataforma.comentarios.moderacion;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import com.nexusbattles.plataforma.comentarios.publicacion.ResumenDeComentario;
import com.nexusbattles.plataforma.comentarios.seguridad.SecurityConfig;

/** GET /comentarios/mios — contrato 1.16.0 (SA-14, §7.3.6): cada persona, los suyos. */
@WebMvcTest(MisComentariosController.class)
@Import({ManejadorErroresComentarios.class, SecurityConfig.class, DecodificadorDePrueba.class})
@DisplayName("GET /comentarios/mios (1.16.0)")
class MisComentariosHttpTest {

    private static final UUID ANA = UUID.fromString("7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55");

    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ServicioDeModeracion servicio;

    @Test
    @DisplayName("una jugadora lee los suyos, en todos sus estados; el autor sale del token")
    void losSuyos() throws Exception {
        when(servicio.historialDelAutor(ANA.toString(), 0, 100)).thenReturn(new ServicioDeModeracion.Historial(
                ANA.toString(), "Ana", List.of(
                        new ResumenDeComentario("c-2", "espada", "Ana", "Retirado por mí",
                                Instant.parse("2026-10-11T10:00:00Z"), Comentario.Estado.ELIMINADO, false),
                        new ResumenDeComentario("c-1", "espada", "Ana", "Muy buena espada",
                                Instant.parse("2026-10-10T10:00:00Z"), Comentario.Estado.PUBLICADO, true)),
                2, 0, 100));

        mvc.perform(get("/api/v1/comentarios/mios").param("tamano", "500")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeUsuario("ana", ANA, "JUGADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autorId").value(ANA.toString()))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.comentarios[0].estado").value("ELIMINADO"))
                .andExpect(jsonPath("$.comentarios[1].texto").value("Muy buena espada"))
                .andExpect(jsonPath("$.comentarios[1].editado").value(true));
    }

    @Test
    @DisplayName("sin sesión es 401 y no se consulta nada; un token de servicio no es una persona: 403")
    void soloPersonas() throws Exception {
        mvc.perform(get("/api/v1/comentarios/mios")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/comentarios/mios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeServicio("metricas-plataforma")))
                .andExpect(status().isForbidden());
        verify(servicio, never()).historialDelAutor(anyString(), anyInt(), anyInt());
    }
}
