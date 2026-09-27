package com.nexusbattles.ms_identidad.perfiles.controller;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.service.ClavesDeFirma;
import com.nexusbattles.ms_identidad.auth.service.JwtService;
import com.nexusbattles.ms_identidad.perfiles.dto.PerfilPublicoResponse;
import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import com.nexusbattles.ms_identidad.perfiles.service.BusquedaDeJugadores;
import com.nexusbattles.ms_identidad.perfiles.service.BusquedaInvalidaException;
import com.nexusbattles.ms_identidad.perfiles.service.PerfilUsuarioService;
import com.nexusbattles.ms_identidad.rbac.repository.RbacMatrixRepository;
import com.nexusbattles.ms_identidad.rbac.security.SecurityInterceptor;
import com.nexusbattles.ms_identidad.rbac.service.RbacAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/v1/perfiles/publicos} por HTTP, con el {@code SecurityInterceptor}
 * de verdad (el que aplica {@code @RequirePermission}) y los DOS controladores
 * de {@code /api/v1/perfiles} montados a la vez: asi se ve que el literal
 * {@code /publicos} no lo captura {@code /{usuario}} (que con «publicos»
 * responderia 404) y que el perfil propio sigue en su sitio.
 */
@DisplayName("GET /api/v1/perfiles/publicos: sesion, forma de la respuesta y enrutado")
class PerfilesPublicosControllerTest {

    private static final String RUTA = "/api/v1/perfiles/publicos";
    private static final UUID UID_LYRA = UUID.fromString("7b0c8f3e-6a1d-4c2b-9f4e-1a2b3c4d5e6f");

    private JwtService jwtService;
    private BusquedaDeJugadores busqueda;
    private PerfilUsuarioService perfilUsuarioService;
    private MockMvc mvc;

    @BeforeEach
    void montar() {
        jwtService = new JwtService(new ClavesDeFirma(""));
        ReflectionTestUtils.setField(jwtService, "horasExpiracion", 24);
        ReflectionTestUtils.setField(jwtService, "emisor", "ms-identidad");
        busqueda = mock(BusquedaDeJugadores.class);
        perfilUsuarioService = mock(PerfilUsuarioService.class);

        // Respaldo por cabecera APAGADO, como en cualquier entorno desplegado.
        SecurityInterceptor interceptor = new SecurityInterceptor(
                new RbacAuthorizationService(new RbacMatrixRepository()), null, jwtService, null, false);
        mvc = MockMvcBuilders
                .standaloneSetup(new PerfilController(perfilUsuarioService), new PerfilesPublicosController(busqueda))
                .addInterceptors(interceptor)
                .build();
    }

    private String token(String apodo, String rol) {
        return "Bearer " + jwtService.generarToken(apodo, rol, 0, UID_LYRA);
    }

    // ------------------------------------------------------------------ sesion

    @Test
    @DisplayName("sin token: 403 problem details del interceptor, y la busqueda ni se intenta")
    void sinToken() throws Exception {
        mvc.perform(get(RUTA).param("apodo", "ana"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://nexusbattles.upb.edu.co/errors/forbidden"))
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(busqueda);
    }

    @Test
    @DisplayName("un token invalido tampoco abre la busqueda")
    void tokenInvalido() throws Exception {
        mvc.perform(get(RUTA).param("apodo", "ana").header("Authorization", "Bearer no-es-un-jwt"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(busqueda);
    }

    @Test
    @DisplayName("la cabecera X-User-Role sin token no acredita sesion (403)")
    void cabeceraDeRolNoEsSesion() throws Exception {
        mvc.perform(get(RUTA).param("apodo", "ana")
                        .header("X-User-Name", "alguien")
                        .header("X-User-Role", "JUGADOR"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(busqueda);
    }

    @ParameterizedTest(name = "rol {0}")
    @ValueSource(strings = {"JUGADOR", "MODERADOR", "ADMINISTRADOR", "SUPER_ADMINISTRADOR"})
    @DisplayName("cualquier rol con sesion valida puede buscar a quien escribir")
    void cualquierRol(String rol) throws Exception {
        when(busqueda.buscar("ana")).thenReturn(List.of());

        mvc.perform(get(RUTA).param("apodo", "ana").header("Authorization", token("lyra", rol)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ------------------------------------------------------ forma de la respuesta

    @Test
    @DisplayName("200: arreglo de {uid, apodo, avatar} y nada mas (ni correo, ni nombres, ni estado)")
    void soloDatosPublicos() throws Exception {
        UUID uidAna = UUID.randomUUID();
        UUID uidAnibal = UUID.randomUUID();
        when(busqueda.buscar("ana")).thenReturn(List.of(
                new PerfilPublicoResponse(uidAna, "Ana", "/avatares-subidos/ana.png"),
                new PerfilPublicoResponse(uidAnibal, "anibal", null)));

        mvc.perform(get(RUTA).param("apodo", "ana").header("Authorization", token("lyra", "JUGADOR")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].uid").value(uidAna.toString()))
                .andExpect(jsonPath("$[0].apodo").value("Ana"))
                .andExpect(jsonPath("$[0].avatar").value("/avatares-subidos/ana.png"))
                .andExpect(jsonPath("$[1].uid").value(uidAnibal.toString()))
                .andExpect(jsonPath("$[1].avatar").doesNotExist())
                .andExpect(jsonPath("$[0].length()").value(3))
                .andExpect(content().string(not(containsString("email"))))
                .andExpect(content().string(not(containsString("nombres"))))
                .andExpect(content().string(not(containsString("estado"))));
    }

    @Test
    @DisplayName("400 problem details (datos-invalidos) cuando el servicio rechaza el texto")
    void textoRechazado() throws Exception {
        when(busqueda.buscar("ab")).thenThrow(new BusquedaInvalidaException("Escribe al menos 3 caracteres del apodo."));

        mvc.perform(get(RUTA).param("apodo", "ab").header("Authorization", token("lyra", "JUGADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://nexusbattles.upb.edu.co/errors/datos-invalidos"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Escribe al menos 3 caracteres del apodo."))
                .andExpect(jsonPath("$.instance").value(RUTA));
    }

    @Test
    @DisplayName("sin el parametro apodo, el servicio decide (400 del mismo tipo), no Spring con otro formato")
    void sinParametro() throws Exception {
        when(busqueda.buscar(null)).thenThrow(new BusquedaInvalidaException("Escribe al menos 3 caracteres del apodo."));

        mvc.perform(get(RUTA).header("Authorization", token("lyra", "JUGADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.upb.edu.co/errors/datos-invalidos"));
    }

    // ------------------------------------------------------------------ enrutado

    @Test
    @DisplayName("el literal /publicos gana a /{usuario}: no se intenta leer un perfil llamado «publicos»")
    void elLiteralGana() throws Exception {
        when(busqueda.buscar("ana")).thenReturn(List.of());

        mvc.perform(get(RUTA).param("apodo", "ana").header("Authorization", token("lyra", "JUGADOR")))
                .andExpect(status().isOk());

        verify(busqueda).buscar("ana");
        verifyNoInteractions(perfilUsuarioService);
    }

    @Test
    @DisplayName("y /{usuario} sigue sirviendo el perfil propio por el uid del token")
    void elPerfilPropioSigueEnSuSitio() throws Exception {
        Usuario lyra = new Usuario();
        lyra.setId(7L);
        lyra.setApodo("lyra");
        lyra.setPublicId(UID_LYRA);
        PerfilUsuario perfil = new PerfilUsuario();
        perfil.setUsuario(lyra);
        perfil.setNombres("Lyra");
        perfil.setApellidos("Belacqua");
        when(perfilUsuarioService.obtenerPorIdentificadorPublico(UID_LYRA)).thenReturn(perfil);

        mvc.perform(get("/api/v1/perfiles/" + UID_LYRA).header("Authorization", token("lyra", "JUGADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apodo").value("lyra"))
                .andExpect(jsonPath("$.nombres").value("Lyra"));
        verify(busqueda, never()).buscar(any());
    }
}
