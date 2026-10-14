package com.nexusbattles.plataforma.comentarios.moderacion;

import static org.mockito.ArgumentMatchers.any;
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
import com.nexusbattles.plataforma.comentarios.seguridad.SecurityConfig;

/**
 * La ruta de los reportes de una cuenta — contrato 1.12.0. Tokens firmados de
 * verdad: lo que se prueba es quien entra.
 */
@WebMvcTest(ReportesDelUsuarioController.class)
@Import({ManejadorErroresComentarios.class, SecurityConfig.class, DecodificadorDePrueba.class})
@DisplayName("GET /comentarios/moderacion/usuarios/{usuarioId}/reportes (1.12.0)")
class ReportesDelUsuarioHttpTest {

    private static final String ANA = "7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55";
    private static final String RUTA = "/api/v1/comentarios/moderacion/usuarios/" + ANA + "/reportes";
    private static final UUID UID_ADMIN = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ReportesDelUsuario reportes;

    private String con(String rol) {
        return "Bearer " + emisor.tokenDeUsuario("ada", UID_ADMIN, rol);
    }

    @Test
    @DisplayName("un administrador lee los reportes recibidos, con la forma del contrato y sin el reportante")
    void formaDelContrato() throws Exception {
        when(reportes.consultar(ANA, ReportesDelUsuario.Direccion.RECIBIDOS, 1, 10))
                .thenReturn(new ReportesDelUsuario.Pagina(ANA, ReportesDelUsuario.Direccion.RECIBIDOS,
                        List.of(new ReportesDelUsuario.Reporte("r-1", "c-1", CategoriaDeReporte.SPAM, null,
                                Instant.parse("2026-10-10T10:00:00Z"),
                                new ReportesDelUsuario.Comentado("c-1", "espada", ANA, "Ana", "Compra aqui",
                                        Comentario.Estado.EN_REVISION))),
                        11, 1, 10, 2, 11));

        mvc.perform(get(RUTA).param("direccion", "RECIBIDOS").param("pagina", "1").param("tamano", "10")
                        .header(HttpHeaders.AUTHORIZATION, con("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(ANA))
                .andExpect(jsonPath("$.direccion").value("RECIBIDOS"))
                .andExpect(jsonPath("$.total").value(11))
                .andExpect(jsonPath("$.totalRealizados").value(2))
                .andExpect(jsonPath("$.totalRecibidos").value(11))
                .andExpect(jsonPath("$.reportes[0].categoria").value("SPAM"))
                .andExpect(jsonPath("$.reportes[0].comentario.estado").value("EN_REVISION"))
                .andExpect(jsonPath("$.reportes[0].comentario.extracto").value("Compra aqui"))
                .andExpect(jsonPath("$.reportes[0].reportanteId").doesNotExist());
    }

    @Test
    @DisplayName("sin direccion pide los realizados; la moderadora tambien entra")
    void porOmisionRealizados() throws Exception {
        when(reportes.consultar(anyString(), any(), anyInt(), anyInt()))
                .thenReturn(new ReportesDelUsuario.Pagina(ANA, ReportesDelUsuario.Direccion.REALIZADOS, List.of(),
                        0, 0, 20, 0, 0));

        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, con("MODERADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportes").isEmpty());
        verify(reportes).consultar(ANA, ReportesDelUsuario.Direccion.REALIZADOS, 0, 20);
    }

    @Test
    @DisplayName("un jugador no entra (403), sin token 401, y una direccion inventada es 400")
    void quienEntra() throws Exception {
        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeJugador("lyra", UUID.randomUUID())))
                .andExpect(status().isForbidden());
        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
        mvc.perform(get(RUTA).param("direccion", "TODOS").header(HttpHeaders.AUTHORIZATION, con("ADMINISTRADOR")))
                .andExpect(status().isBadRequest());
        verify(reportes, never()).consultar(anyString(), any(), anyInt(), anyInt());
    }
}
