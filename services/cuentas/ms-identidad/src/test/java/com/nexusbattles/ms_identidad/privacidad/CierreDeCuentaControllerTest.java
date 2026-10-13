package com.nexusbattles.ms_identidad.privacidad;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.ClavesDeFirma;
import com.nexusbattles.ms_identidad.auth.service.JwtService;
import com.nexusbattles.ms_identidad.privacidad.CierreRechazadoException.Motivo;
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

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/v1/perfiles/{usuario}/cierre} con el interceptor de seguridad de
 * verdad y tokens firmados de verdad (ms-identidad-perfiles.yaml 1.4.0).
 */
@DisplayName("/api/v1/perfiles/{usuario}/cierre (HU-PRV-005)")
class CierreDeCuentaControllerTest {

    private static final String TIPOS = "https://nexusbattles.upb.edu.co/errors/";
    private static final UUID UID = UUID.fromString("eeeeeeee-1111-4222-8333-444444444444");
    private static final UUID OTRO = UUID.fromString("ffffffff-1111-4222-8333-444444444444");
    private static final String RUTA = "/api/v1/perfiles/" + UID + "/cierre";
    private static final EstadoDelCierre PROGRAMADO = new EstadoDelCierre(EstadoDelCierre.PROGRAMADO, 30,
            OffsetDateTime.parse("2026-10-05T14:30:00-05:00"), OffsetDateTime.parse("2026-11-04T14:30:00-05:00"));

    private CierreDeCuentaService servicio;
    private JwtService jwt;
    private MockMvc mvc;
    private Usuario ada;

    @BeforeEach
    void preparar() {
        servicio = mock(CierreDeCuentaService.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        jwt = new JwtService(new ClavesDeFirma(""));
        ReflectionTestUtils.setField(jwt, "horasExpiracion", 24);
        ReflectionTestUtils.setField(jwt, "emisor", "ms-identidad");
        ada = new Usuario();
        ada.setId(8L);
        ada.setApodo("ada");
        ada.setPublicId(UID);
        Usuario otra = new Usuario();
        otra.setId(9L);
        otra.setApodo("grace");
        otra.setPublicId(OTRO);
        when(usuarios.findByApodo("ada")).thenReturn(Optional.of(ada));
        when(usuarios.findByPublicId(UID)).thenReturn(Optional.of(ada));
        when(usuarios.findByApodo("grace")).thenReturn(Optional.of(otra));
        when(usuarios.findByPublicId(OTRO)).thenReturn(Optional.of(otra));
        SecurityInterceptor interceptor = new SecurityInterceptor(
                new RbacAuthorizationService(new RbacMatrixRepository()),
                new AuditoriaEventClient("http://localhost:1/auditoria", null), jwt, usuarios, false);
        mvc = MockMvcBuilders.standaloneSetup(new CierreDeCuentaController(servicio, usuarios))
                .addInterceptors(interceptor).build();
    }

    private String tokenDeAda() {
        return "Bearer " + jwt.generarToken("ada", "JUGADOR", 0, UID);
    }

    @Test
    @DisplayName("GET: el estado de la propia cuenta, sin cache")
    void consultar() throws Exception {
        when(servicio.consultar(ada)).thenReturn(EstadoDelCierre.sinSolicitud());

        mvc.perform(get(RUTA).header("Authorization", tokenDeAda()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.estado").value("SIN_SOLICITUD"))
                .andExpect(jsonPath("$.plazoDias").value(30));
    }

    @Test
    @DisplayName("sin token: 403 del interceptor, sin llegar al servicio")
    void sinSesion() throws Exception {
        mvc.perform(get(RUTA)).andExpect(status().isForbidden());
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("{\"passwordActual\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(RUTA)).andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("la cuenta de otra persona: 403, nunca se consulta ni se cierra")
    void otraCuenta() throws Exception {
        String rutaAjena = "/api/v1/perfiles/" + OTRO + "/cierre";

        mvc.perform(get(rutaAjena).header("Authorization", tokenDeAda()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(TIPOS + "forbidden"));
        mvc.perform(post(rutaAjena).header("Authorization", tokenDeAda())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"passwordActual\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(rutaAjena).header("Authorization", tokenDeAda()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("un segmento que no es un UUID: 404")
    void noEsUnUuid() throws Exception {
        mvc.perform(get("/api/v1/perfiles/8/cierre").header("Authorization", tokenDeAda()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(TIPOS + "cuenta-no-encontrada"));
        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("POST: 201 si se programa ahora, con la contrasena, el token y la IP del borde")
    void solicitarNuevo() throws Exception {
        String token = tokenDeAda();
        when(servicio.solicitar(ada, "Clave.Actual-1", token, "203.0.113.9"))
                .thenReturn(new CierreDeCuentaService.Solicitud(PROGRAMADO, true));

        mvc.perform(post(RUTA).header("Authorization", token).header("X-Forwarded-For", "203.0.113.9")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"passwordActual\":\"Clave.Actual-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.estado").value("PROGRAMADO"))
                .andExpect(jsonPath("$.plazoDias").value(30))
                .andExpect(jsonPath("$.programadoPara").value(startsWith("2026-11-04T14:30")))
                .andExpect(jsonPath("$.solicitadoEn").value(startsWith("2026-10-05T14:30")));
    }

    @Test
    @DisplayName("POST: 200 si ya estaba programado")
    void solicitarRepetido() throws Exception {
        when(servicio.solicitar(eq(ada), anyString(), anyString(), anyString()))
                .thenReturn(new CierreDeCuentaService.Solicitud(PROGRAMADO, false));

        mvc.perform(post(RUTA).header("Authorization", tokenDeAda())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"passwordActual\":\"Clave.Actual-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PROGRAMADO"));
    }

    @Test
    @DisplayName("POST sin contrasena o ilegible: 400 datos-invalidos")
    void solicitudMalFormada() throws Exception {
        mvc.perform(post(RUTA).header("Authorization", tokenDeAda())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(TIPOS + "datos-invalidos"));
        mvc.perform(post(RUTA).header("Authorization", tokenDeAda())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"passwordActual\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(RUTA).header("Authorization", tokenDeAda())
                        .contentType(MediaType.APPLICATION_JSON).content("no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(TIPOS + "datos-invalidos"));
        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("POST: cada rechazo con su type y su estado; 409 dice cuantas, 503 con Retry-After")
    void rechazos() throws Exception {
        when(servicio.solicitar(any(), anyString(), anyString(), anyString()))
                .thenThrow(new CierreRechazadoException(Motivo.ACTUAL_INCORRECTA, "La contraseña actual es incorrecta."))
                .thenThrow(new CierreRechazadoException(Motivo.CUENTA_BLOQUEADA, "Bloqueada."))
                .thenThrow(new CierreRechazadoException(Motivo.OPERACIONES_PENDIENTES, "Abiertas.", 2, 1))
                .thenThrow(new CierreRechazadoException(Motivo.SUBASTAS_NO_DISPONIBLES, "Sin respuesta."));
        String cuerpo = "{\"passwordActual\":\"Clave.Actual-1\"}";

        mvc.perform(post(RUTA).header("Authorization", tokenDeAda())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(TIPOS + "contrasena-actual-incorrecta"))
                .andExpect(jsonPath("$.detail").value("La contraseña actual es incorrecta."));
        mvc.perform(post(RUTA).header("Authorization", tokenDeAda())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.type").value(TIPOS + "cuenta-bloqueada"));
        mvc.perform(post(RUTA).header("Authorization", tokenDeAda())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(TIPOS + "cierre-con-operaciones-pendientes"))
                .andExpect(jsonPath("$.subastasActivas").value(2))
                .andExpect(jsonPath("$.pujasVigentes").value(1));
        mvc.perform(post(RUTA).header("Authorization", tokenDeAda())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.type").value(TIPOS + "subastas-no-disponibles"));
    }

    @Test
    @DisplayName("DELETE: cancela y responde SIN_SOLICITUD")
    void cancelar() throws Exception {
        when(servicio.cancelar(eq(ada), anyString())).thenReturn(EstadoDelCierre.sinSolicitud());

        mvc.perform(delete(RUTA).header("Authorization", tokenDeAda()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SIN_SOLICITUD"));
        verify(servicio).cancelar(eq(ada), anyString());
    }

    @Test
    @DisplayName("un token sin uid (anterior a ADR-002) solo sirve para la cuenta de su apodo")
    void tokenSinUid() throws Exception {
        String sinUid = "Bearer " + jwt.generarToken("ada", "JUGADOR", 0, null);
        when(servicio.consultar(ada)).thenReturn(EstadoDelCierre.sinSolicitud());

        mvc.perform(get(RUTA).header("Authorization", sinUid)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/perfiles/" + OTRO + "/cierre").header("Authorization", sinUid))
                .andExpect(status().isForbidden());
    }
}
