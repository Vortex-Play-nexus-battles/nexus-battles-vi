package com.nexusbattles.ms_chatbot.chat.conocimiento;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.EstadoVersion;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import com.nexusbattles.ms_chatbot.chat.motor.model.VersionBaseConocimiento;
import com.nexusbattles.ms_chatbot.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BaseConocimientoAdminController.class)
@Import(SecurityConfig.class)
class BaseConocimientoAdminControllerTest {

    private static final String RUTA = "/chatbot/admin/base-conocimiento";

    // Cuerpo valido de TemaConocimientoRequest.
    private static final String TEMA_JSON = """
        {
          "categoria": "SUBASTA_Y_COMERCIO",
          "tipoRespuesta": "DIRECTA",
          "titulo": "Como pujar",
          "variantesEs": ["como pujo", "hacer una puja"],
          "variantesEn": ["how to bid"],
          "respuestaEs": "Entra a Subastas y elige un articulo.",
          "respuestaEn": "Go to Auctions and pick an item.",
          "prioridad": 2,
          "activo": true
        }
        """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BaseConocimientoAdminService baseConocimientoAdminService;

    // ---------------------------------------------------------------- roles

    @Test
    void listarVersiones_comoAdministrador_devuelveLasVersiones() throws Exception {
        when(baseConocimientoAdminService.listarVersiones())
            .thenReturn(List.of(VersionBaseConocimiento.nuevaCandidata(2, "Temas de torneos")));

        mockMvc.perform(get(RUTA + "/versiones").with(administrador()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].numero").value(2))
            .andExpect(jsonPath("$[0].estado").value("BORRADOR"))
            .andExpect(jsonPath("$[0].descripcion").value("Temas de torneos"));
    }

    @Test
    void listarVersiones_comoSuperAdministrador_devuelve200() throws Exception {
        when(baseConocimientoAdminService.listarVersiones()).thenReturn(List.of());

        mockMvc.perform(get(RUTA + "/versiones").with(conRol("ROLE_SUPER_ADMINISTRADOR")))
            .andExpect(status().isOk());
    }

    @Test
    void listarVersiones_comoJugador_devuelve403() throws Exception {
        mockMvc.perform(get(RUTA + "/versiones").with(conRol("ROLE_JUGADOR")))
            .andExpect(status().isForbidden());

        verifyNoInteractions(baseConocimientoAdminService);
    }

    @Test
    void listarVersiones_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get(RUTA + "/versiones"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(baseConocimientoAdminService);
    }

    // Un jugador tampoco puede modificar: la regla cubre todos los metodos.
    @Test
    void importar_comoJugador_devuelve403() throws Exception {
        mockMvc.perform(put(RUTA + "/borrador/importacion").with(conRol("ROLE_JUGADOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"temas\": [" + TEMA_JSON + "]}"))
            .andExpect(status().isForbidden());

        verifyNoInteractions(baseConocimientoAdminService);
    }

    // ------------------------------------------------------------ candidata

    @Test
    void crearCandidata_conDescripcion_devuelve201() throws Exception {
        when(baseConocimientoAdminService.crearCandidata("Temas de torneos"))
            .thenReturn(VersionBaseConocimiento.nuevaCandidata(2, "Temas de torneos"));

        mockMvc.perform(post(RUTA + "/borrador").with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"descripcion\": \"Temas de torneos\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.numero").value(2))
            .andExpect(jsonPath("$.estado").value("BORRADOR"));
    }

    // El cuerpo es opcional: sin el, la candidata se crea sin descripcion.
    @Test
    void crearCandidata_sinCuerpo_laCreaSinDescripcion() throws Exception {
        when(baseConocimientoAdminService.crearCandidata(null))
            .thenReturn(VersionBaseConocimiento.nuevaCandidata(2, null));

        mockMvc.perform(post(RUTA + "/borrador").with(administrador()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.numero").value(2));

        verify(baseConocimientoAdminService).crearCandidata(null);
    }

    @Test
    void crearCandidata_conDescripcionDeMasDe500_devuelve400() throws Exception {
        mockMvc.perform(post(RUTA + "/borrador").with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"descripcion\": \"" + "x".repeat(501) + "\"}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(baseConocimientoAdminService);
    }

    @Test
    void crearCandidata_siYaHayUna_devuelve409() throws Exception {
        when(baseConocimientoAdminService.crearCandidata(any()))
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una version candidata."));

        mockMvc.perform(post(RUTA + "/borrador").with(administrador()))
            .andExpect(status().isConflict());
    }

    @Test
    void descartarCandidata_devuelve204() throws Exception {
        mockMvc.perform(delete(RUTA + "/borrador").with(administrador()))
            .andExpect(status().isNoContent());

        verify(baseConocimientoAdminService).descartarCandidata();
    }

    // ---------------------------------------------------------------- temas

    // En la base las variantes son un texto separado por coma; en la API, lista.
    @Test
    void listarTemas_devuelveLasVariantesComoLista() throws Exception {
        when(baseConocimientoAdminService.listarTemasDeCandidata()).thenReturn(List.of(temaDePrueba()));

        mockMvc.perform(get(RUTA + "/borrador/temas").with(administrador()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].clave").value("clave-pujas"))
            .andExpect(jsonPath("$[0].variantesEs[0]").value("como pujo"))
            .andExpect(jsonPath("$[0].variantesEs[1]").value("hacer una puja"))
            .andExpect(jsonPath("$[0].prioridad").value(2));
    }

    @Test
    void listarTemas_sinCandidata_devuelve409() throws Exception {
        when(baseConocimientoAdminService.listarTemasDeCandidata())
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "No hay una version candidata."));

        mockMvc.perform(get(RUTA + "/borrador/temas").with(administrador()))
            .andExpect(status().isConflict());
    }

    @Test
    void agregarTema_devuelve201ConElTemaCreado() throws Exception {
        when(baseConocimientoAdminService.agregarTema(any())).thenReturn(temaDePrueba());

        mockMvc.perform(post(RUTA + "/borrador/temas").with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content(TEMA_JSON))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.titulo").value("Como pujar"));

        ArgumentCaptor<TemaConocimientoRequest> captor = ArgumentCaptor.forClass(TemaConocimientoRequest.class);
        verify(baseConocimientoAdminService).agregarTema(captor.capture());
        assertEquals(Categoria.SUBASTA_Y_COMERCIO, captor.getValue().categoria());
        assertEquals(List.of("como pujo", "hacer una puja"), captor.getValue().variantesEs());
        assertEquals(2, captor.getValue().prioridad());
    }

    // Sin variantes en espanol el tema no se podria encontrar nunca: 400.
    @Test
    void agregarTema_sinVariantesEnEspanol_devuelve400() throws Exception {
        String sinVariantes = TEMA_JSON.replace("[\"como pujo\", \"hacer una puja\"]", "[]");

        mockMvc.perform(post(RUTA + "/borrador/temas").with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content(sinVariantes))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(baseConocimientoAdminService);
    }

    @Test
    void agregarTema_conCategoriaInexistente_devuelve400() throws Exception {
        String categoriaMala = TEMA_JSON.replace("SUBASTA_Y_COMERCIO", "NO_EXISTE");

        mockMvc.perform(post(RUTA + "/borrador/temas").with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content(categoriaMala))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(baseConocimientoAdminService);
    }

    @Test
    void editarTema_devuelveElTemaActualizado() throws Exception {
        UUID temaId = UUID.randomUUID();
        when(baseConocimientoAdminService.editarTema(eq(temaId), any())).thenReturn(temaDePrueba());

        mockMvc.perform(put(RUTA + "/borrador/temas/" + temaId).with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content(TEMA_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.clave").value("clave-pujas"));
    }

    @Test
    void editarTema_queNoEsDeLaCandidata_devuelve404() throws Exception {
        UUID temaId = UUID.randomUUID();
        when(baseConocimientoAdminService.editarTema(eq(temaId), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe ese tema en la version candidata."));

        mockMvc.perform(put(RUTA + "/borrador/temas/" + temaId).with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content(TEMA_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    void eliminarTema_devuelve204() throws Exception {
        UUID temaId = UUID.randomUUID();

        mockMvc.perform(delete(RUTA + "/borrador/temas/" + temaId).with(administrador()))
            .andExpect(status().isNoContent());

        verify(baseConocimientoAdminService).eliminarTema(temaId);
    }

    @Test
    void eliminarTema_conIdQueNoEsUuid_devuelve400() throws Exception {
        mockMvc.perform(delete(RUTA + "/borrador/temas/no-es-un-uuid").with(administrador()))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(baseConocimientoAdminService);
    }

    // ------------------------------------------------ exportar / importar

    @Test
    void exportar_devuelveUnJsonDescargable() throws Exception {
        UUID versionId = UUID.randomUUID();
        when(baseConocimientoAdminService.exportar(versionId)).thenReturn(new ExportacionBaseConocimiento(
            3, EstadoVersion.PRODUCCION, Instant.parse("2026-09-23T20:00:00Z"),
            List.of(TemaIntercambio.desde(temaDePrueba()))));

        mockMvc.perform(get(RUTA + "/versiones/" + versionId + "/exportacion").with(administrador()))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                containsString("base-conocimiento-v3.json")))
            .andExpect(jsonPath("$.version").value(3))
            .andExpect(jsonPath("$.estado").value("PRODUCCION"))
            .andExpect(jsonPath("$.temas[0].clave").value("clave-pujas"))
            .andExpect(jsonPath("$.temas[0].variantesEs[1]").value("hacer una puja"));
    }

    @Test
    void exportar_versionInexistente_devuelve404() throws Exception {
        UUID versionId = UUID.randomUUID();
        when(baseConocimientoAdminService.exportar(versionId))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe esa version."));

        mockMvc.perform(get(RUTA + "/versiones/" + versionId + "/exportacion").with(administrador()))
            .andExpect(status().isNotFound());
    }

    // El archivo descargado en /exportacion se sube tal cual: version, estado
    // y fechaExportacion se ignoran, y la clave de cada tema se conserva.
    @Test
    void importar_aceptaElArchivoExportadoTalCual() throws Exception {
        when(baseConocimientoAdminService.importarEnCandidata(any())).thenReturn(List.of(temaDePrueba()));
        String archivoExportado = """
            {
              "version": 3,
              "estado": "PRODUCCION",
              "fechaExportacion": "2026-09-23T20:00:00Z",
              "temas": [
                {
                  "clave": "clave-pujas",
                  "categoria": "SUBASTA_Y_COMERCIO",
                  "tipoRespuesta": "DIRECTA",
                  "titulo": "Como pujar",
                  "variantesEs": ["como pujo", "hacer una puja"],
                  "variantesEn": [],
                  "respuestaEs": "Entra a Subastas y elige un articulo.",
                  "respuestaEn": null,
                  "prioridad": 2,
                  "activo": true
                }
              ]
            }
            """;

        mockMvc.perform(put(RUTA + "/borrador/importacion").with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content(archivoExportado))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].clave").value("clave-pujas"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TemaIntercambio>> captor = ArgumentCaptor.forClass(List.class);
        verify(baseConocimientoAdminService).importarEnCandidata(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals("clave-pujas", captor.getValue().get(0).clave());
        assertNull(captor.getValue().get(0).respuestaEn());
    }

    @Test
    void importar_sinTemas_devuelve400() throws Exception {
        mockMvc.perform(put(RUTA + "/borrador/importacion").with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"temas\": []}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(baseConocimientoAdminService);
    }

    // @Valid llega a cada tema del archivo: uno sin titulo rechaza todo.
    @Test
    void importar_conUnTemaInvalido_devuelve400() throws Exception {
        String sinTitulo = TEMA_JSON.replace("\"titulo\": \"Como pujar\"", "\"titulo\": \"\"");

        mockMvc.perform(put(RUTA + "/borrador/importacion").with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"temas\": [" + sinTitulo + "]}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(baseConocimientoAdminService);
    }

    @Test
    void importar_conVarianteConComa_devuelve400() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Una variante no puede contener ','."))
            .when(baseConocimientoAdminService).importarEnCandidata(any());

        mockMvc.perform(put(RUTA + "/borrador/importacion").with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"temas\": [" + TEMA_JSON.replace("como pujo", "como pujo, rapido") + "]}"))
            .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------- ayudas

    private static RequestPostProcessor administrador() {
        return conRol("ROLE_ADMINISTRADOR");
    }

    private static RequestPostProcessor conRol(String rol) {
        return jwt().authorities(new SimpleGrantedAuthority(rol));
    }

    private static TemaConocimiento temaDePrueba() {
        return new TemaConocimiento(
            VersionBaseConocimiento.nuevaCandidata(2, null),
            "clave-pujas",
            Categoria.SUBASTA_Y_COMERCIO,
            TipoRespuesta.DIRECTA,
            "Como pujar",
            "como pujo, hacer una puja",
            "how to bid",
            "Entra a Subastas y elige un articulo.",
            "Go to Auctions and pick an item.",
            2,
            true);
    }
}
