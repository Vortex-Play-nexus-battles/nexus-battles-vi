package com.nexusbattles.ms_identidad.auth.segundofactor;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.segundofactor.SegundoFactorRechazadoException.Motivo;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.DesactivarSegundoFactorRequest;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.EnrolamientoResponse;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.EstadoSegundoFactorResponse;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/v1/auth/segundo-factor}: solo con sesion, solo la propia cuenta,
 * y cada rechazo como problem details con el {@code type} del contrato 2.2.0.
 */
@DisplayName("SegundoFactorController (cuenta propia, con sesion)")
class SegundoFactorControllerTest {

    private static final String TIPOS = "https://nexusbattles.upb.edu.co/errors/";
    private static final UUID UID = UUID.fromString("6f1c2a7e-3d6b-4b9a-8f0e-1c2d3e4f5a6b");

    private MockMvc mockMvc;
    private SegundoFactorService servicio;
    private UsuarioRepository usuarios;
    private JwtService jwt;
    private Usuario ana;

    @BeforeEach
    void preparar() {
        servicio = mock(SegundoFactorService.class);
        usuarios = mock(UsuarioRepository.class);
        jwt = new JwtService(new ClavesDeFirma(""));
        ReflectionTestUtils.setField(jwt, "horasExpiracion", 24);
        ReflectionTestUtils.setField(jwt, "emisor", "ms-identidad");

        ana = new Usuario();
        ana.setId(7L);
        ana.setPublicId(UID);
        ana.setApodo("ana");
        when(usuarios.findByPublicId(UID)).thenReturn(Optional.of(ana));

        SecurityInterceptor interceptor = new SecurityInterceptor(
                new RbacAuthorizationService(new RbacMatrixRepository()),
                new AuditoriaEventClient("http://localhost:8091/api/v1/admin/auditoria/eventos", null),
                jwt, usuarios, false);
        mockMvc = MockMvcBuilders.standaloneSetup(new SegundoFactorController(servicio, usuarios))
                .addInterceptors(interceptor).build();
    }

    private String token(String rol) {
        return "Bearer " + jwt.generarToken("ana", rol, 0, UID);
    }

    @Test
    @DisplayName("sin sesion: 403 del interceptor y el servicio ni se entera")
    void sinSesion() throws Exception {
        mockMvc.perform(get("/api/v1/auth/segundo-factor")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/segundo-factor/enrolamiento")).andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("estado de la propia cuenta, sin cache; cualquier rol (tambien el administrativo) puede consultarlo")
    void estado() throws Exception {
        when(servicio.estado(ana)).thenReturn(
                new EstadoSegundoFactorResponse(true, true, true, false, "2026-10-05T14:30:00Z", 9L));

        for (String rol : new String[] {"JUGADOR", "MODERADOR", "ADMINISTRADOR", "SUPER_ADMINISTRADOR"}) {
            mockMvc.perform(get("/api/v1/auth/segundo-factor").header("Authorization", token(rol)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.activo").value(true))
                    .andExpect(jsonPath("$.obligatorio").value(true))
                    .andExpect(jsonPath("$.disponible").value(true))
                    .andExpect(jsonPath("$.enrolamientoPendiente").value(false))
                    .andExpect(jsonPath("$.activadoEn").value("2026-10-05T14:30:00Z"))
                    .andExpect(jsonPath("$.codigosRecuperacionRestantes").value(9));
        }
    }

    @Test
    @DisplayName("enrolamiento: secreto en base32 y URI otpauth, sin cache")
    void enrolamiento() throws Exception {
        when(servicio.iniciarEnrolamiento(ana)).thenReturn(new EnrolamientoResponse("JBSWY3DPEHPK3PXP",
                "otpauth://totp/Nexus:ana?secret=JBSWY3DPEHPK3PXP", "Nexus", "ana@nexus.test", "SHA1", 6, 30));

        mockMvc.perform(post("/api/v1/auth/segundo-factor/enrolamiento").header("Authorization", token("JUGADOR")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.secreto").value("JBSWY3DPEHPK3PXP"))
                .andExpect(jsonPath("$.uriOtpauth").value("otpauth://totp/Nexus:ana?secret=JBSWY3DPEHPK3PXP"))
                .andExpect(jsonPath("$.digitos").value(6))
                .andExpect(jsonPath("$.periodoSegundos").value(30));
    }

    @Test
    @DisplayName("ya activo: 409 segundo-factor-ya-activo; sin clave: 503 segundo-factor-no-disponible")
    void enrolamientoRechazado() throws Exception {
        when(servicio.iniciarEnrolamiento(ana))
                .thenThrow(new SegundoFactorRechazadoException(Motivo.YA_ACTIVO, "Ya activo."))
                .thenThrow(new SegundoFactorRechazadoException(Motivo.NO_DISPONIBLE, "No disponible."));

        mockMvc.perform(post("/api/v1/auth/segundo-factor/enrolamiento").header("Authorization", token("JUGADOR")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(TIPOS + "segundo-factor-ya-activo"))
                .andExpect(jsonPath("$.instance").value("/api/v1/auth/segundo-factor/enrolamiento"));
        mockMvc.perform(post("/api/v1/auth/segundo-factor/enrolamiento").header("Authorization", token("JUGADOR")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value(TIPOS + "segundo-factor-no-disponible"));
    }

    @Test
    @DisplayName("activacion: los codigos de recuperacion en la respuesta, una vez, sin cache")
    void activacion() throws Exception {
        when(servicio.activar(eq(ana), eq("287082"), any())).thenReturn(List.of("K7QX2-M9PRT", "4HNZW-8CVBE"));

        mockMvc.perform(post("/api/v1/auth/segundo-factor/activacion").header("Authorization", token("ADMINISTRADOR"))
                        .header("X-Forwarded-For", "10.0.0.5")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"287082\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.codigosRecuperacion[0]").value("K7QX2-M9PRT"))
                .andExpect(jsonPath("$.codigosRecuperacion.length()").value(2));
        verify(servicio).activar(ana, "287082", "10.0.0.5");
    }

    @Test
    @DisplayName("activacion con un codigo que no coincide: 422, nunca 401/403 (no es una sesion caducada)")
    void activacionConCodigoIncorrecto() throws Exception {
        when(servicio.activar(eq(ana), eq("000000"), any()))
                .thenThrow(new SegundoFactorRechazadoException(Motivo.CODIGO_INVALIDO, "No coincide."));

        mockMvc.perform(post("/api/v1/auth/segundo-factor/activacion").header("Authorization", token("JUGADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"000000\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(TIPOS + "codigo-segundo-factor-invalido"))
                .andExpect(jsonPath("$.detail").value("No coincide."));
    }

    @Test
    @DisplayName("cuerpo sin codigo o ilegible: 400 datos-invalidos")
    void cuerpoMalFormado() throws Exception {
        mockMvc.perform(post("/api/v1/auth/segundo-factor/activacion").header("Authorization", token("JUGADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(TIPOS + "datos-invalidos"));
        mockMvc.perform(post("/api/v1/auth/segundo-factor/desactivacion").header("Authorization", token("JUGADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(TIPOS + "datos-invalidos"));
    }

    @Test
    @DisplayName("desactivacion: 204; contrasena mal 422; bloqueada 423")
    void desactivacion() throws Exception {
        String cuerpo = "{\"passwordActual\":\"Clave-Actual-2026!\",\"codigo\":\"287082\"}";

        mockMvc.perform(post("/api/v1/auth/segundo-factor/desactivacion").header("Authorization", token("JUGADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isNoContent());
        verify(servicio).desactivar(eq(ana), eq(new DesactivarSegundoFactorRequest("Clave-Actual-2026!", "287082",
                null)), any());

        doThrow(new SegundoFactorRechazadoException(Motivo.CONTRASENA_INCORRECTA, "Mal."))
                .doThrow(new SegundoFactorRechazadoException(Motivo.CUENTA_BLOQUEADA, "Bloqueada."))
                .when(servicio).desactivar(any(), any(), any());
        mockMvc.perform(post("/api/v1/auth/segundo-factor/desactivacion").header("Authorization", token("JUGADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(TIPOS + "contrasena-actual-incorrecta"));
        mockMvc.perform(post("/api/v1/auth/segundo-factor/desactivacion").header("Authorization", token("JUGADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.type").value(TIPOS + "cuenta-bloqueada"));
    }

    @Test
    @DisplayName("un token cuyo titular ya no existe: 403, como el resto de rutas propias")
    void titularDesaparecido() throws Exception {
        UUID otro = UUID.randomUUID();
        String huerfano = "Bearer " + jwt.generarToken("fantasma", "JUGADOR", 0, otro);

        mockMvc.perform(get("/api/v1/auth/segundo-factor").header("Authorization", huerfano))
                .andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }
}
