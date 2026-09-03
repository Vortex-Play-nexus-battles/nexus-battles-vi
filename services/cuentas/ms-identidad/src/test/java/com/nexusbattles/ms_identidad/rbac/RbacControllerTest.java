package com.nexusbattles.ms_identidad.rbac;

import com.nexusbattles.ms_identidad.rbac.controller.RbacController;
import com.nexusbattles.ms_identidad.rbac.repository.RbacMatrixRepository;
import com.nexusbattles.ms_identidad.rbac.service.RbacAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("HU-RBAC-001: Pruebas unitarias de RbacController")
class RbacControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RbacMatrixRepository repository = new RbacMatrixRepository();
        RbacAuthorizationService authorizationService = new RbacAuthorizationService(repository);
        RbacController controller = new RbacController(authorizationService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/v1/rbac/roles debe listar los 4 roles oficiales del catálogo")
    void getRoles_retornaCatalogoCompletoDeCuatroRoles() throws Exception {
        mockMvc.perform(get("/api/v1/rbac/roles")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].role", is("JUGADOR")))
                .andExpect(jsonPath("$[1].role", is("MODERADOR")))
                .andExpect(jsonPath("$[2].role", is("ADMINISTRADOR")))
                .andExpect(jsonPath("$[3].role", is("SUPER_ADMINISTRADOR")));
    }

    @Test
    @DisplayName("GET /api/v1/rbac/matrix debe retornar la versión oficial y el mapa completo 12x4")
    void getMatrix_retornaMatrizOficialTabla24Extendida() throws Exception {
        mockMvc.perform(get("/api/v1/rbac/matrix")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version", containsString("Tabla 24 extendida")))
                .andExpect(jsonPath("$.matrix.JUGADOR", notNullValue()))
                .andExpect(jsonPath("$.matrix.MODERADOR", notNullValue()))
                .andExpect(jsonPath("$.matrix.ADMINISTRADOR", notNullValue()))
                .andExpect(jsonPath("$.matrix.SUPER_ADMINISTRADOR", notNullValue()))
                .andExpect(jsonPath("$.matrix.JUGADOR.CREAR_CUENTA_JUGADOR", is("GRANTED")))
                .andExpect(jsonPath("$.matrix.JUGADOR.BANEAR_DEFINITIVAMENTE", is("DENIED")))
                .andExpect(jsonPath("$.matrix.MODERADOR.SUSPENDER_USUARIOS", is("TEMPORARY")));
    }

    @Test
    @DisplayName("POST /api/v1/rbac/authorize debe autorizar acción válida de Jugador")
    void evaluatePermission_accionPermitidaJugador() throws Exception {
        String payload = """
            {
                "role": "JUGADOR",
                "action": "MODIFICAR_PERFIL_PROPIO"
            }
            """;

        mockMvc.perform(post("/api/v1/rbac/authorize")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permitted", is(true)))
                .andExpect(jsonPath("$.permissionType", is("GRANTED")))
                .andExpect(jsonPath("$.role", is("JUGADOR")))
                .andExpect(jsonPath("$.action", is("MODIFICAR_PERFIL_PROPIO")))
                .andExpect(jsonPath("$.reason", containsString("autorizada")));
    }

    @Test
    @DisplayName("POST /api/v1/rbac/authorize debe denegar acción restringida para Jugador")
    void evaluatePermission_accionDenegadaJugador() throws Exception {
        String payload = """
            {
                "role": "JUGADOR",
                "action": "BANEAR_DEFINITIVAMENTE"
            }
            """;

        mockMvc.perform(post("/api/v1/rbac/authorize")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permitted", is(false)))
                .andExpect(jsonPath("$.permissionType", is("DENIED")))
                .andExpect(jsonPath("$.role", is("JUGADOR")))
                .andExpect(jsonPath("$.action", is("BANEAR_DEFINITIVAMENTE")))
                .andExpect(jsonPath("$.reason", containsString("denegado")));
    }

    @Test
    @DisplayName("POST /api/v1/rbac/authorize debe reconocer permiso TEMPORARY en Moderador")
    void evaluatePermission_accionTemporalModerador() throws Exception {
        String payload = """
            {
                "role": "MODERADOR",
                "action": "SUSPENDER_USUARIOS"
            }
            """;

        mockMvc.perform(post("/api/v1/rbac/authorize")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permitted", is(true)))
                .andExpect(jsonPath("$.permissionType", is("TEMPORARY")))
                .andExpect(jsonPath("$.role", is("MODERADOR")))
                .andExpect(jsonPath("$.action", is("SUSPENDER_USUARIOS")));
    }

    @Test
    @DisplayName("POST /api/v1/rbac/authorize debe rechazar con 400 payload inválido o incompleto")
    void evaluatePermission_payloadInvalido_retorna400() throws Exception {
        String payload = "{}";

        mockMvc.perform(post("/api/v1/rbac/authorize")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest());
    }
}
