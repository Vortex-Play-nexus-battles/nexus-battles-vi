package com.nexusbattles.ms_identidad.auth;

import com.nexusbattles.ms_identidad.auth.controller.AuthController;
import com.nexusbattles.ms_identidad.auth.dto.LoginResponse;
import com.nexusbattles.ms_identidad.auth.exception.CredencialesInvalidasException;
import com.nexusbattles.ms_identidad.auth.exception.CuentaBaneadaException;
import com.nexusbattles.ms_identidad.auth.exception.CuentaBloqueadaException;
import com.nexusbattles.ms_identidad.auth.exception.CuentaInactivaException;
import com.nexusbattles.ms_identidad.auth.exception.CuentaSuspendidaException;
import com.nexusbattles.ms_identidad.auth.exception.RegistroRechazadoException;
import com.nexusbattles.ms_identidad.auth.exception.RegistroRechazadoException.Motivo;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.ClavesDeFirma;
import com.nexusbattles.ms_identidad.auth.service.JwtService;
import com.nexusbattles.ms_identidad.auth.service.LoginService;
import com.nexusbattles.ms_identidad.auth.service.RegistroService;
import com.nexusbattles.ms_identidad.auth.service.TokenCredencialService;
import com.nexusbattles.ms_identidad.onboarding.auditoria.AuditoriaDeCuenta;
import com.nexusbattles.ms_identidad.rbac.repository.RbacMatrixRepository;
import com.nexusbattles.ms_identidad.rbac.security.AuditoriaEventClient;
import com.nexusbattles.ms_identidad.rbac.security.SecurityInterceptor;
import com.nexusbattles.ms_identidad.rbac.service.RbacAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R17 en {@code AuthController}: los rechazos del registro y del login en el
 * formato que pida el cliente (texto de siempre o problem details), la
 * carrera de dos altas iguales, y el cierre de sesion.
 */
@DisplayName("AuthController: registro, login y cierre de sesion (R17)")
class AuthControllerRegistroYSesionTest {

    private static final String TIPOS = "https://nexusbattles.upb.edu.co/errors/";
    private static final MediaType PROBLEMA = MediaType.APPLICATION_PROBLEM_JSON;

    private MockMvc mockMvc;
    private RegistroService registroService;
    private LoginService loginService;
    private UsuarioRepository usuarioRepository;
    private AuditoriaDeCuenta auditoria;
    private JwtService jwtService;
    private final UUID uid = UUID.randomUUID();

    @BeforeEach
    void preparar() {
        registroService = mock(RegistroService.class);
        loginService = mock(LoginService.class);
        usuarioRepository = mock(UsuarioRepository.class);
        auditoria = mock(AuditoriaDeCuenta.class);

        AuthController controlador = new AuthController(registroService, loginService,
                mock(TokenCredencialService.class), usuarioRepository, auditoria);

        jwtService = new JwtService(new ClavesDeFirma(""));
        ReflectionTestUtils.setField(jwtService, "horasExpiracion", 24);
        ReflectionTestUtils.setField(jwtService, "emisor", "ms-identidad");
        Usuario profe = new Usuario();
        profe.setApodo("profe");
        when(usuarioRepository.findByApodo("profe")).thenReturn(Optional.of(profe));
        SecurityInterceptor interceptor = new SecurityInterceptor(
                new RbacAuthorizationService(new RbacMatrixRepository()),
                new AuditoriaEventClient("http://localhost:8091/api/v1/admin/auditoria/eventos", null),
                jwtService, usuarioRepository, false);

        mockMvc = MockMvcBuilders.standaloneSetup(controlador).addInterceptors(interceptor).build();
    }

    private static MockMultipartHttpServletRequestBuilder registro() {
        MockMultipartHttpServletRequestBuilder peticion = multipart("/api/v1/auth/registro");
        peticion.param("nombres", "Ada").param("apellidos", "Lovelace").param("email", "ada@upb.edu.co")
                .param("password", "Segura-123!").param("apodo", "ada");
        return peticion;
    }

    private void registroRechazado(Supplier<RuntimeException> fallo) {
        when(registroService.registrarUsuario(any(), any(), any())).thenThrow(fallo.get());
    }

    @Test
    @DisplayName("alta correcta: 201, con la traza y la IP de la peticion llegando al servicio")
    void altaCorrecta() throws Exception {
        Usuario creado = new Usuario();
        creado.setApodo("ada");
        when(registroService.registrarUsuario(any(), eq("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"),
                eq("203.0.113.5"))).thenReturn(creado);

        mockMvc.perform(registro()
                        .header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
                        .header("X-Forwarded-For", "203.0.113.5"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apodo").value("ada"));
    }

    @Test
    @DisplayName("correo repetido: el cliente de siempre recibe el mismo 400 de texto (contrato 1.0.0)")
    void rechazoEnTextoPlano() throws Exception {
        registroRechazado(() -> new RegistroRechazadoException(Motivo.CORREO_EN_USO, "El correo electrónico ya está registrado."));

        mockMvc.perform(registro())
                .andExpect(status().isBadRequest())
                .andExpect(content().string("El correo electrónico ya está registrado."));
    }

    @Test
    @DisplayName("correo repetido con Accept problem+json: type estable y el campo a marcar")
    void rechazoEnProblemDetails() throws Exception {
        registroRechazado(() -> new RegistroRechazadoException(Motivo.CORREO_EN_USO, "El correo electrónico ya está registrado."));

        mockMvc.perform(registro().accept(PROBLEMA))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEMA))
                .andExpect(jsonPath("$.type").value(TIPOS + "correo-en-uso"))
                .andExpect(jsonPath("$.title").value("El correo ya está registrado"))
                .andExpect(jsonPath("$.detail").value("El correo electrónico ya está registrado."))
                .andExpect(jsonPath("$.campo").value("email"))
                .andExpect(jsonPath("$.instance").value("/api/v1/auth/registro"));
    }

    @Test
    @DisplayName("contrasena debil: mismo type que HU-AUT-006")
    void contrasenaDebil() throws Exception {
        registroRechazado(() -> new RegistroRechazadoException(Motivo.CONTRASENA_DEBIL, "Falta un simbolo."));

        mockMvc.perform(registro().accept(PROBLEMA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(TIPOS + "contrasena-no-cumple-politica"))
                .andExpect(jsonPath("$.campo").value("password"));
    }

    @Test
    @DisplayName("dos altas iguales a la vez: la segunda choca con UNIQUE y recibe el mismo rechazo, no el SQL")
    void carreraDeAltas() throws Exception {
        registroRechazado(() -> new DataIntegrityViolationException("ERROR: duplicate key value violates unique constraint uk_usuarios_email"));
        when(usuarioRepository.existsByEmailIgnoreCase("ada@upb.edu.co")).thenReturn(true);

        mockMvc.perform(registro().accept(PROBLEMA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(TIPOS + "correo-en-uso"))
                .andExpect(jsonPath("$.detail").value("El correo electrónico ya está registrado."));

        when(usuarioRepository.existsByEmailIgnoreCase("ada@upb.edu.co")).thenReturn(false);
        mockMvc.perform(registro())
                .andExpect(status().isBadRequest())
                .andExpect(content().string("El apodo ya está en uso."));
    }

    @Test
    @DisplayName("un IllegalArgumentException de otra capa sigue siendo un 400 con su mensaje")
    void argumentoInvalido() throws Exception {
        registroRechazado(() -> new IllegalArgumentException("password cannot be more than 72 bytes"));

        mockMvc.perform(registro().accept(PROBLEMA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(TIPOS + "datos-de-registro-invalidos"));
    }

    @Test
    @DisplayName("login: cada rechazo con su estado de siempre y, si se pide, su type")
    void loginRechazos() throws Exception {
        String cuerpo = "{\"email\":\"ada@upb.edu.co\",\"password\":\"x\"}";
        Object[][] casos = {
            {new CredencialesInvalidasException("Correo o contraseña incorrectos."), 401, "credenciales-invalidas"},
            {new CuentaBaneadaException("Baneada."), 403, "cuenta-baneada"},
            {new CuentaSuspendidaException("Suspendida."), 403, "cuenta-suspendida"},
            {new CuentaInactivaException("Inactiva."), 403, "cuenta-inactiva"},
            {new CuentaBloqueadaException("Bloqueada."), 423, "cuenta-bloqueada"},
        };
        for (Object[] caso : casos) {
            RuntimeException fallo = (RuntimeException) caso[0];
            doThrow(fallo).when(loginService).iniciarSesion(any(), any(), any());

            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().is((int) caso[1]))
                    .andExpect(content().string(fallo.getMessage()));

            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                            .accept(PROBLEMA))
                    .andExpect(status().is((int) caso[1]))
                    .andExpect(jsonPath("$.type").value(TIPOS + caso[2]))
                    .andExpect(jsonPath("$.detail").value(fallo.getMessage()));
        }
    }

    @Test
    @DisplayName("login correcto: devuelve uid y si el alta esta lista")
    void loginCorrecto() throws Exception {
        when(loginService.iniciarSesion(any(), any(), any())).thenReturn(
                new LoginResponse(1L, "ada", "ada@upb.edu.co", "JUGADOR", false, "t", uid.toString(), false));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ada@upb.edu.co\",\"password\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uid").value(uid.toString()))
                .andExpect(jsonPath("$.onboardingListo").value(false));
    }

    @Test
    @DisplayName("logout con sesion: 204 y el cierre queda auditado con el uid")
    void cierreDeSesion() throws Exception {
        String token = jwtService.generarToken("profe", "JUGADOR", 0, uid);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Forwarded-For", "198.51.100.7"))
                .andExpect(status().isNoContent());

        verify(auditoria).cierreDeSesion(uid.toString(), "198.51.100.7");
    }

    @Test
    @DisplayName("logout sin sesion: el interceptor no deja pasar y no se audita nada")
    void cierreSinSesion() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isForbidden());

        verify(auditoria, never()).cierreDeSesion(any(), isNull());
        verify(auditoria, never()).cierreDeSesion(any(), any());
    }
}
