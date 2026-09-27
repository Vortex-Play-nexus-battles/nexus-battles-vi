package com.nexusbattles.ms_identidad.auth.recuperacion;

import com.nexusbattles.ms_identidad.auth.codigos.CodigoInvalidoException;
import com.nexusbattles.ms_identidad.auth.codigos.DemasiadosIntentosException;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.recuperacion.RecuperacionRechazadaException.Motivo;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.ClavesDeFirma;
import com.nexusbattles.ms_identidad.auth.service.JwtService;
import com.nexusbattles.ms_identidad.rbac.repository.RbacMatrixRepository;
import com.nexusbattles.ms_identidad.rbac.security.AuditoriaEventClient;
import com.nexusbattles.ms_identidad.rbac.security.SecurityInterceptor;
import com.nexusbattles.ms_identidad.rbac.service.RbacAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("/api/v1/auth/restablecer/* y /api/v1/auth/preguntas-seguridad (B1)")
class RecuperacionYPreguntasControllerTest {

    private static final String TIPOS = "https://nexusbattles.upb.edu.co/errors/";
    private static final UUID UID = UUID.fromString("abababab-1111-4222-8333-444444444444");

    private RecuperacionService recuperacion;
    private PreguntasDeSeguridadService preguntas;
    private UsuarioRepository usuarios;
    private JwtService jwt;
    private MockMvc mvc;
    private Usuario ada;

    @BeforeEach
    void preparar() {
        recuperacion = mock(RecuperacionService.class);
        preguntas = mock(PreguntasDeSeguridadService.class);
        usuarios = mock(UsuarioRepository.class);
        jwt = new JwtService(new ClavesDeFirma(""));
        ReflectionTestUtils.setField(jwt, "horasExpiracion", 24);
        ReflectionTestUtils.setField(jwt, "emisor", "ms-identidad");
        ada = new Usuario();
        ada.setId(8L);
        ada.setApodo("ada");
        ada.setPublicId(UID);
        when(usuarios.findByApodo("ada")).thenReturn(Optional.of(ada));
        when(usuarios.findByPublicId(UID)).thenReturn(Optional.of(ada));
        SecurityInterceptor interceptor = new SecurityInterceptor(
                new RbacAuthorizationService(new RbacMatrixRepository()),
                new AuditoriaEventClient("http://localhost:1/auditoria", null), jwt, usuarios, false);
        mvc = MockMvcBuilders.standaloneSetup(new RecuperacionController(recuperacion),
                        new PreguntasDeSeguridadController(preguntas, usuarios))
                .addInterceptors(interceptor).build();
    }

    // ------------------------------------------------------------- restablecer

    @Test
    @DisplayName("solicitar: 200 y el mismo texto siempre")
    void solicitar() throws Exception {
        mvc.perform(post("/api/v1/auth/restablecer/solicitar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ada@upb.edu.co\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(RecuperacionService.MENSAJE_SOLICITUD));
        verify(recuperacion).solicitar("ada@upb.edu.co");
    }

    @Test
    @DisplayName("preguntas: 200 con las preguntas; codigo malo 400; agotado 429")
    void preguntasDelCodigo() throws Exception {
        UUID id = UUID.randomUUID();
        when(recuperacion.preguntas("ada@upb.edu.co", "K7QX2M9P")).thenReturn(new PreguntasDeRecuperacion(true,
                List.of(new PreguntasDeRecuperacion.Pregunta(id, "¿Ciudad?"))));
        when(recuperacion.preguntas("ada@upb.edu.co", "MALO1234")).thenThrow(new CodigoInvalidoException());
        when(recuperacion.preguntas("ada@upb.edu.co", "OTRO1234")).thenThrow(new DemasiadosIntentosException());

        mvc.perform(post("/api/v1/auth/restablecer/preguntas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ada@upb.edu.co\",\"codigo\":\"K7QX2M9P\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuradas").value(true))
                .andExpect(jsonPath("$.preguntas[0].id").value(id.toString()))
                .andExpect(jsonPath("$.preguntas[0].texto").value("¿Ciudad?"))
                .andExpect(jsonPath("$.preguntas[0].respuesta").doesNotExist());
        mvc.perform(post("/api/v1/auth/restablecer/preguntas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ada@upb.edu.co\",\"codigo\":\"MALO1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(TIPOS + "codigo-invalido"));
        mvc.perform(post("/api/v1/auth/restablecer/preguntas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ada@upb.edu.co\",\"codigo\":\"OTRO1234\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value(TIPOS + "demasiados-intentos"));
    }

    @Test
    @DisplayName("confirmar: 200 de texto; 422 respuestas-incorrectas y contrasena-no-cumple-politica; 400 sin email")
    void confirmar() throws Exception {
        String cuerpo = "{\"email\":\"ada@upb.edu.co\",\"codigo\":\"K7QX2M9P\",\"nuevaPassword\":\"Nueva.Clave-9\","
                + "\"respuestas\":[{\"preguntaId\":\"" + UUID.randomUUID() + "\",\"respuesta\":\"bogota\"}]}";
        mvc.perform(post("/api/v1/auth/restablecer/confirmar").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.9").content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(content().string(RecuperacionService.MENSAJE_CANJE));
        verify(recuperacion).confirmar(eq("ada@upb.edu.co"), eq("K7QX2M9P"), any(), eq("Nueva.Clave-9"),
                eq("203.0.113.9"));

        doThrow(new RecuperacionRechazadaException(Motivo.RESPUESTAS_INCORRECTAS, "No coinciden."))
                .when(recuperacion).confirmar(anyString(), eq("RESP1234"), any(), anyString(), any());
        mvc.perform(post("/api/v1/auth/restablecer/confirmar").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo.replace("K7QX2M9P", "RESP1234")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(TIPOS + "respuestas-incorrectas"))
                .andExpect(jsonPath("$.detail").value("No coinciden."));

        doThrow(new RecuperacionRechazadaException(Motivo.POLITICA, "La contraseña nueva debe incluir un número."))
                .when(recuperacion).confirmar(anyString(), eq("POLI1234"), any(), anyString(), any());
        mvc.perform(post("/api/v1/auth/restablecer/confirmar").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo.replace("K7QX2M9P", "POLI1234")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(TIPOS + "contrasena-no-cumple-politica"));

        mvc.perform(post("/api/v1/auth/restablecer/confirmar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"ABCDEFGHJK\",\"nuevaPassword\":\"Nueva.Clave-9\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(TIPOS + "datos-invalidos"));
    }

    // ------------------------------------------------------ preguntas-seguridad

    @Test
    @DisplayName("GET/PUT preguntas-seguridad exigen sesion: sin token, 403 del interceptor")
    void sinSesion() throws Exception {
        mvc.perform(get("/api/v1/auth/preguntas-seguridad")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/auth/preguntas-seguridad").contentType(MediaType.APPLICATION_JSON)
                .content("{\"passwordActual\":\"x\",\"preguntas\":[]}")).andExpect(status().isForbidden());
        verifyNoInteractions(preguntas);
    }

    @Test
    @DisplayName("con sesion: consulta y configura las de la cuenta del token, nunca otra")
    void conSesion() throws Exception {
        String token = jwt.generarToken("ada", "JUGADOR", 0, UID);
        when(preguntas.deLaCuenta(8L)).thenReturn(PreguntasDeRecuperacion.ninguna());
        when(preguntas.configurar(eq(8L), any(), eq("127.0.0.1"))).thenReturn(new PreguntasDeRecuperacion(true,
                List.of(new PreguntasDeRecuperacion.Pregunta(UUID.randomUUID(), "¿Ciudad?"))));

        mvc.perform(get("/api/v1/auth/preguntas-seguridad").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuradas").value(false))
                .andExpect(jsonPath("$.preguntas").isEmpty());

        mvc.perform(put("/api/v1/auth/preguntas-seguridad").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passwordActual\":\"Actual.Clave-1\",\"preguntas\":[{\"texto\":\"¿Ciudad?\",\"respuesta\":\"bogota\"},"
                                + "{\"texto\":\"¿Mascota?\",\"respuesta\":\"firulais\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuradas").value(true));
    }

    @Test
    @DisplayName("PUT: 422 y 423 con su type; sin passwordActual, 400")
    void rechazosDelPut() throws Exception {
        String token = jwt.generarToken("ada", "JUGADOR", 0, UID);
        when(preguntas.configurar(eq(8L), any(), any()))
                .thenThrow(new RecuperacionRechazadaException(Motivo.PREGUNTAS_INVALIDAS, "Configura entre 2 y 3 preguntas."))
                .thenThrow(new RecuperacionRechazadaException(Motivo.CUENTA_BLOQUEADA, "Bloqueada."));
        String cuerpo = "{\"passwordActual\":\"Actual.Clave-1\",\"preguntas\":[]}";

        mvc.perform(put("/api/v1/auth/preguntas-seguridad").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(TIPOS + "preguntas-invalidas"));
        mvc.perform(put("/api/v1/auth/preguntas-seguridad").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.type").value(TIPOS + "cuenta-bloqueada"));
        mvc.perform(put("/api/v1/auth/preguntas-seguridad").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"preguntas\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(TIPOS + "datos-invalidos"));
    }

    @Test
    @DisplayName("un token sin uid (anterior a ADR-002) identifica la cuenta por el apodo; si ya no existe, 403")
    void sinUid() throws Exception {
        String token = jwt.generarToken("ada", "JUGADOR", 0, null);
        when(preguntas.deLaCuenta(8L)).thenReturn(PreguntasDeRecuperacion.ninguna());
        mvc.perform(get("/api/v1/auth/preguntas-seguridad").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        UUID otro = UUID.randomUUID();
        String huerfano = jwt.generarToken("ada", "JUGADOR", 0, otro);
        when(usuarios.findByPublicId(otro)).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/auth/preguntas-seguridad").header("Authorization", "Bearer " + huerfano))
                .andExpect(status().isForbidden());
    }
}
