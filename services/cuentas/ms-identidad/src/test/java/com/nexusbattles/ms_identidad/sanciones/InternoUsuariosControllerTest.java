package com.nexusbattles.ms_identidad.sanciones;

import com.nexusbattles.ms_identidad.auth.model.EstadoCuenta;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.ClavesDeFirma;
import com.nexusbattles.ms_identidad.auth.service.JwtService;
import com.nexusbattles.ms_identidad.auth.servicio.EmisorDeTokensDeServicio;
import com.nexusbattles.ms_identidad.rbac.security.AuditoriaEventClient;
import com.nexusbattles.ms_identidad.rbac.security.InterceptorDeServicio;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("/api/v1/internal/usuarios (B2): solo credenciales de servicio firmadas por este emisor")
class InternoUsuariosControllerTest {

    private static final String TIPOS = "https://nexusbattles.upb.edu.co/errors/";
    private static final UUID UID = UUID.fromString("f4f4f4f4-1111-4222-8333-444444444444");
    private static final UUID SANCION = UUID.fromString("a5a5a5a5-1111-4222-8333-444444444444");
    private static final String CUERPO = "{\"estado\":\"SUSPENDIDO\",\"hasta\":\"2026-09-26T12:00:00Z\","
            + "\"sancionId\":\"" + SANCION + "\",\"motivo\":\"Lenguaje ofensivo\"}";

    private ClavesDeFirma claves;
    private JwtService jwt;
    private EmisorDeTokensDeServicio emisor;
    private ProyeccionDeSancionService proyecciones;
    private UsuarioRepository usuarios;
    private AuditoriaEventClient auditoria;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        claves = new ClavesDeFirma("");
        jwt = new JwtService(claves);
        ReflectionTestUtils.setField(jwt, "horasExpiracion", 24);
        ReflectionTestUtils.setField(jwt, "emisor", "ms-identidad");
        emisor = new EmisorDeTokensDeServicio(claves, "ms-identidad", 15);
        proyecciones = mock(ProyeccionDeSancionService.class);
        usuarios = mock(UsuarioRepository.class);
        auditoria = mock(AuditoriaEventClient.class);
        mvc = MockMvcBuilders.standaloneSetup(new InternoUsuariosController(proyecciones, usuarios))
                .addMappedInterceptors(new String[] {"/api/v1/internal/**"},
                        new InterceptorDeServicio(jwt, auditoria, "ms-identidad"))
                .build();
    }

    private String servicio(String clientId) {
        return "Bearer " + emisor.emitir(clientId).valor();
    }

    @Test
    @DisplayName("moderacion-sanciones proyecta: 200 con el estado resultante")
    void proyecta() throws Exception {
        when(proyecciones.proyectar(eq(UID), any())).thenReturn(
                new EstadoDeCuentaResponse(UID, "SUSPENDIDO", OffsetDateTime.parse("2026-09-26T12:00:00Z"), 5));

        mvc.perform(put("/api/v1/internal/usuarios/" + UID + "/estado-sancion")
                        .header("Authorization", servicio("moderacion-sanciones"))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uid").value(UID.toString()))
                .andExpect(jsonPath("$.estado").value("SUSPENDIDO"))
                .andExpect(jsonPath("$.versionToken").value(5));
        verify(proyecciones).proyectar(UID, new ProyeccionDeSancionRequest("SUSPENDIDO",
                OffsetDateTime.parse("2026-09-26T12:00:00Z"), SANCION, "Lenguaje ofensivo"));
    }

    @Test
    @DisplayName("403 fail-closed: sin token, token de persona (aunque sea Super Administrador), otro servicio o firma ajena")
    void soloModeracion() throws Exception {
        String superAdmin = "Bearer " + jwt.generarToken("root", "SUPER_ADMINISTRADOR", 0, UUID.randomUUID());
        String ajeno = "Bearer " + Jwts.builder().issuer("ms-identidad").subject("moderacion-sanciones")
                .claim("azp", "moderacion-sanciones").claim("rol", "SERVICIO")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(new ClavesDeFirma("").privada(), Jwts.SIG.RS256).compact();
        String otroEmisor = "Bearer " + new EmisorDeTokensDeServicio(claves, "keycloak", 15)
                .emitir("moderacion-sanciones").valor();

        for (String credencial : new String[] {null, "Basic abc", superAdmin, servicio("salas-partidas"), ajeno,
                otroEmisor, "Bearer basura"}) {
            var peticion = put("/api/v1/internal/usuarios/" + UID + "/estado-sancion")
                    .contentType(MediaType.APPLICATION_JSON).content(CUERPO);
            if (credencial != null) {
                peticion.header("Authorization", credencial);
            }
            mvc.perform(peticion)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.type").value(TIPOS + "forbidden"));
        }
        verifyNoInteractions(proyecciones);
        verify(auditoria, org.mockito.Mockito.atLeast(7))
                .registrarBypassAsync(any(), eq("SERVICIO"), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("contacto: cualquier servicio con credencial; el estado con el nombre del contrato")
    void contacto() throws Exception {
        Usuario ada = new Usuario();
        ada.setPublicId(UID);
        ada.setEmail("ada@upb.edu.co");
        ada.setApodo("ada");
        ada.setEstado("BANEADA");
        when(usuarios.findByPublicId(UID)).thenReturn(Optional.of(ada));

        mvc.perform(get("/api/v1/internal/usuarios/" + UID + "/contacto")
                        .header("Authorization", servicio("ms-ecommerce")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ada@upb.edu.co"))
                .andExpect(jsonPath("$.apodo").value("ada"))
                .andExpect(jsonPath("$.estado").value(EstadoCuenta.BANEADO));

        mvc.perform(get("/api/v1/internal/usuarios/" + UID + "/contacto")
                        .header("Authorization", "Bearer " + jwt.generarToken("ada", "JUGADOR", 0, UID)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("404 cuenta-no-encontrada: uid inexistente o mal formado; 400: proyeccion invalida o ilegible")
    void errores() throws Exception {
        when(usuarios.findByPublicId(any())).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/internal/usuarios/" + UUID.randomUUID() + "/contacto")
                        .header("Authorization", servicio("ms-subastas")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(TIPOS + "cuenta-no-encontrada"));
        mvc.perform(get("/api/v1/internal/usuarios/no-es-un-uuid/contacto")
                        .header("Authorization", servicio("ms-subastas")))
                .andExpect(status().isNotFound());

        when(proyecciones.proyectar(eq(UID), any())).thenThrow(new ProyeccionInvalidaException("Falta sancionId."));
        mvc.perform(put("/api/v1/internal/usuarios/" + UID + "/estado-sancion")
                        .header("Authorization", servicio("moderacion-sanciones"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"BANEADO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(TIPOS + "datos-invalidos"));
        mvc.perform(put("/api/v1/internal/usuarios/" + UID + "/estado-sancion")
                        .header("Authorization", servicio("moderacion-sanciones"))
                        .contentType(MediaType.APPLICATION_JSON).content("{roto"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("las comprobaciones previas del navegador (OPTIONS) no piden credencial")
    void preflight() throws Exception {
        mvc.perform(options("/api/v1/internal/usuarios/" + UID + "/contacto")).andExpect(status().isOk());
        verifyNoInteractions(auditoria);
    }
}
