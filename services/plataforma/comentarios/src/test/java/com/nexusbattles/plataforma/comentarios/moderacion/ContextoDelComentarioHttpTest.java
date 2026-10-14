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

/** GET /comentarios/moderacion/{id}/contexto — contrato 1.17.0 (SA-16, §7.3.8). */
@WebMvcTest(ModeracionController.class)
@Import({ManejadorErroresComentarios.class, SecurityConfig.class, DecodificadorDePrueba.class})
@DisplayName("GET /comentarios/moderacion/{id}/contexto (1.17.0)")
class ContextoDelComentarioHttpTest {

    private static final UUID MODERADORA = UUID.fromString("5b2c1d3e-4f5a-4b6c-8d7e-9f0a1b2c3d4e");
    private static final String RUTA = "/api/v1/comentarios/moderacion/c-3/contexto";

    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ServicioDeModeracion servicio;

    private static ResumenDeComentario item(String id, String apodo, Comentario.Estado estado, String fecha) {
        return new ResumenDeComentario(id, "espada", apodo, "texto de " + id, Instant.parse(fecha), estado, false);
    }

    private String como(String rol) {
        return "Bearer " + emisor.tokenDeUsuario("marta", MODERADORA, rol);
    }

    @Test
    @DisplayName("la moderacion lo lee: los de antes y los de despues, con su autor y su estado")
    void loLeeLaModeracion() throws Exception {
        when(servicio.contexto("c-3", 2)).thenReturn(new ServicioDeModeracion.Contexto("c-3", "espada",
                List.of(item("c-1", "Ana", Comentario.Estado.PUBLICADO, "2026-10-10T10:00:00Z"),
                        item("c-2", "Bruno", Comentario.Estado.OCULTO, "2026-10-10T11:00:00Z")),
                List.of(item("c-4", "Carla", Comentario.Estado.ELIMINADO, "2026-10-10T13:00:00Z")),
                6));

        for (String rol : List.of("MODERADOR", "ADMINISTRADOR", "SUPER_ADMINISTRADOR")) {
            mvc.perform(get(RUTA).param("alrededor", "2").header(HttpHeaders.AUTHORIZATION, como(rol)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.comentarioId").value("c-3"))
                    .andExpect(jsonPath("$.productoId").value("espada"))
                    .andExpect(jsonPath("$.anteriores[0].apodoAutor").value("Ana"))
                    .andExpect(jsonPath("$.anteriores[1].estado").value("OCULTO"))
                    .andExpect(jsonPath("$.posteriores[0].estado").value("ELIMINADO"))
                    .andExpect(jsonPath("$.posteriores[0].fechaPublicacion").value("2026-10-10T13:00:00Z"))
                    .andExpect(jsonPath("$.totalEnElHilo").value(6));
        }
    }

    @Test
    @DisplayName("un comentario que no existe es 404 en problem details")
    void inexistente() throws Exception {
        when(servicio.contexto("c-3", 3)).thenThrow(new ServicioDeModeracion.ComentarioNoEncontrado("c-3"));

        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, como("MODERADOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("un jugador no lo lee (403) y sin sesion es 401; no se consulta nada")
    void soloLaModeracion() throws Exception {
        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, como("JUGADOR")))
                .andExpect(status().isForbidden());
        verify(servicio, never()).contexto(anyString(), anyInt());
    }
}
