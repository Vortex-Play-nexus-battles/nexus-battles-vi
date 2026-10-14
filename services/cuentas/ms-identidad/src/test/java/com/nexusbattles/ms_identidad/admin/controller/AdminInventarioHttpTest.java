package com.nexusbattles.ms_identidad.admin.controller;

import com.nexusbattles.ms_identidad.admin.inventario.EntregaAdministrativaService;
import com.nexusbattles.ms_identidad.admin.inventario.EntregaRechazadaException;
import com.nexusbattles.ms_identidad.admin.inventario.InventarioDeLaCuentaClient;
import com.nexusbattles.ms_identidad.admin.inventario.InventarioDeLaCuentaClient.ElementoEntregado;
import com.nexusbattles.ms_identidad.admin.inventario.InventarioDeLaCuentaClient.Entrega;
import com.nexusbattles.ms_identidad.admin.inventario.InventarioNoDisponibleException;
import com.nexusbattles.ms_identidad.auditoria.client.AuditoriaClient;
import com.nexusbattles.ms_identidad.auditoria.client.AuditoriaNoDisponibleException;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.ClavesDeFirma;
import com.nexusbattles.ms_identidad.auth.service.JwtService;
import com.nexusbattles.ms_identidad.config.AdministracionAdvice;
import com.nexusbattles.ms_identidad.notificaciones.client.NotificacionClient;
import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import com.nexusbattles.ms_identidad.rbac.repository.RbacMatrixRepository;
import com.nexusbattles.ms_identidad.rbac.security.SecurityInterceptor;
import com.nexusbattles.ms_identidad.rbac.service.RbacAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La entrega administrativa por HTTP — SA-10, ms-identidad-admin.yaml 1.10.0.
 *
 * <p>Con el {@code SecurityInterceptor} de verdad y tokens firmados por el
 * mismo emisor, el servicio de verdad y solo los clientes de otros servicios
 * falsos: se ve el permiso de la Tabla 24 (Administrador y Super
 * Administrador), la jerarquia de 7.3.1, que cada rechazo es problem details
 * con su {@code type} y que la auditoria caida es 503 con {@code Retry-After}.
 */
@DisplayName("Entrega administrativa al inventario por HTTP (SA-10, §7.3.4)")
class AdminInventarioHttpTest {

    private static final String RUTA = "/api/v1/admin/usuarios/{id}/inventario/entregas";
    private static final String ERRORES = "https://nexusbattles.upb.edu.co/errors/";
    private static final UUID UID = UUID.fromString("d1d1d1d1-1111-4222-8333-444444444444");
    private static final String CLAVE = "c0ffee00-1111-4222-8333-444444444444";
    private static final String CUERPO =
            "{\"productoId\":\"p-hacha\",\"cantidad\":2,\"motivo\":\"Compensación por la caída\"}";

    private JwtService jwtService;
    private UsuarioRepository usuarios;
    private InventarioDeLaCuentaClient inventario;
    private AuditoriaClient auditoria;
    private MockMvc mvc;

    @BeforeEach
    void montar() {
        jwtService = new JwtService(new ClavesDeFirma(""));
        ReflectionTestUtils.setField(jwtService, "horasExpiracion", 24);
        ReflectionTestUtils.setField(jwtService, "emisor", "ms-identidad");
        usuarios = mock(UsuarioRepository.class);
        inventario = mock(InventarioDeLaCuentaClient.class);
        auditoria = mock(AuditoriaClient.class);

        SecurityInterceptor interceptor = new SecurityInterceptor(
                new RbacAuthorizationService(new RbacMatrixRepository()), null, jwtService, null, false);
        AdminInventarioController controlador = new AdminInventarioController(
                new EntregaAdministrativaService(usuarios, inventario, auditoria, mock(NotificacionClient.class)));
        mvc = MockMvcBuilders.standaloneSetup(controlador)
                .setControllerAdvice(new AdministracionAdvice())
                .addInterceptors(interceptor)
                .build();

        Usuario jugadora = new Usuario();
        jugadora.setId(7L);
        jugadora.setApodo("jugadora");
        jugadora.setRol(new RolEntity("JUGADOR", "Jugador"));
        jugadora.setPublicId(UID);
        when(usuarios.findById(7L)).thenReturn(Optional.of(jugadora));
        Usuario colega = new Usuario();
        colega.setId(8L);
        colega.setApodo("colega");
        colega.setRol(new RolEntity("ADMINISTRADOR", "Administrador"));
        colega.setPublicId(UUID.randomUUID());
        when(usuarios.findById(8L)).thenReturn(Optional.of(colega));
    }

    private String token(String rol) {
        return "Bearer " + jwtService.generarToken("operadora", rol, 0, UUID.randomUUID());
    }

    private ResultActions entregar(long cuenta, String rol, String cuerpo, String clave) throws Exception {
        var peticion = post(RUTA, cuenta).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        if (rol != null) {
            peticion.header("Authorization", token(rol));
        }
        if (clave != null) {
            peticion.header("Idempotency-Key", clave);
        }
        return mvc.perform(peticion);
    }

    private static Entrega hecha(boolean nueva) {
        return new Entrega("e-1", UID, "ADMINISTRACION", "admin-entrega:" + CLAVE,
                List.of(new ElementoEntregado("el-0", "p-hacha", "ARMA", "Hacha de Obsidiana"),
                        new ElementoEntregado("el-1", "p-hacha", "ARMA", "Hacha de Obsidiana")),
                OffsetDateTime.parse("2026-10-14T12:00:00Z"), nueva);
    }

    @ParameterizedTest(name = "{0} entrega: 201 con la entrega y queda auditada")
    @ValueSource(strings = {"ADMINISTRADOR", "SUPER_ADMINISTRADOR"})
    void administracionEntrega(String rol) throws Exception {
        when(inventario.entregar(anyString(), eq(CLAVE), eq(UID), eq("p-hacha"), eq(2))).thenReturn(hecha(true));

        entregar(7, rol, CUERPO, CLAVE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entregaId").value("e-1"))
                .andExpect(jsonPath("$.cantidad").value(2))
                .andExpect(jsonPath("$.nueva").value(true))
                .andExpect(jsonPath("$.elementos[0].nombrePropio").value("Hacha de Obsidiana"));
        verify(auditoria).registrar(eq("ACTUALIZACION"), anyString(), eq(UID.toString()), any(),
                eq("Inventario: 2 × Hacha de Obsidiana (entrega e-1)"), eq("Compensación por la caída"),
                anyString(), eq("ms-identidad:entrega-administrativa:" + CLAVE));
    }

    @Test
    @DisplayName("la misma clave otra vez: 200 con la entrega original")
    void repeticion() throws Exception {
        when(inventario.entregar(anyString(), eq(CLAVE), eq(UID), eq("p-hacha"), eq(2))).thenReturn(hecha(false));

        entregar(7, "ADMINISTRADOR", CUERPO, CLAVE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nueva").value(false));
    }

    @ParameterizedTest(name = "{0} no entrega: 403 y el inventario ni se entera")
    @ValueSource(strings = {"JUGADOR", "MODERADOR"})
    void sinPermiso(String rol) throws Exception {
        entregar(7, rol, CUERPO, CLAVE).andExpect(status().isForbidden());
        verifyNoInteractions(inventario, auditoria);
    }

    @Test
    @DisplayName("sin token: rechazado antes de llegar al servicio")
    void sinToken() throws Exception {
        entregar(7, null, CUERPO, CLAVE).andExpect(status().is4xxClientError());
        verifyNoInteractions(inventario, auditoria);
    }

    @Test
    @DisplayName("7.3.1: el inventario de otro Administrador es jerarquia-insuficiente")
    void jerarquia() throws Exception {
        entregar(8, "ADMINISTRADOR", CUERPO, CLAVE)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(ERRORES + "jerarquia-insuficiente"));
        verifyNoInteractions(inventario, auditoria);
    }

    @Test
    @DisplayName("cuerpo o clave invalidos: 400 datos-invalidos, sin tocar el inventario")
    void invalidos() throws Exception {
        entregar(7, "ADMINISTRADOR", "{\"productoId\":\"p\",\"cantidad\":0,\"motivo\":\"Compensación\"}", CLAVE)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(ERRORES + "datos-invalidos"));
        entregar(7, "ADMINISTRADOR", "{\"productoId\":\"p\",\"cantidad\":21,\"motivo\":\"Compensación\"}", CLAVE)
                .andExpect(status().isBadRequest());
        entregar(7, "ADMINISTRADOR", "{\"productoId\":\"p\",\"cantidad\":1,\"motivo\":\"no\"}", CLAVE)
                .andExpect(status().isBadRequest());
        entregar(7, "ADMINISTRADOR", "{\"productoId\":\" \",\"cantidad\":1,\"motivo\":\"Compensación\"}", CLAVE)
                .andExpect(status().isBadRequest());
        entregar(7, "ADMINISTRADOR", "no es json", CLAVE).andExpect(status().isBadRequest());
        entregar(7, "ADMINISTRADOR", CUERPO, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(ERRORES + "datos-invalidos"));
        verifyNoInteractions(inventario, auditoria);
    }

    @Test
    @DisplayName("una cuenta que no existe: 404 cuenta-no-encontrada")
    void cuentaInexistente() throws Exception {
        when(usuarios.findById(404L)).thenReturn(Optional.empty());

        entregar(404, "ADMINISTRADOR", CUERPO, CLAVE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(ERRORES + "cuenta-no-encontrada"));
    }

    @Test
    @DisplayName("lo que el inventario rechaza sale con su estado y su detalle; si no responde, 503 con Retry-After")
    void rechazosDelInventario() throws Exception {
        when(inventario.entregar(anyString(), anyString(), any(), anyString(), anyInt()))
                .thenThrow(new EntregaRechazadaException(409, "El producto esta suspendido."))
                .thenThrow(new EntregaRechazadaException(422, "El producto no existe en el catalogo."))
                .thenThrow(new InventarioNoDisponibleException("El inventario no responde.", null));

        entregar(7, "ADMINISTRADOR", CUERPO, CLAVE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(ERRORES + "entrega-rechazada"))
                .andExpect(jsonPath("$.detail").value("El producto esta suspendido."));
        entregar(7, "ADMINISTRADOR", CUERPO, CLAVE)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.detail").value("El producto no existe en el catalogo."));
        entregar(7, "ADMINISTRADOR", CUERPO, CLAVE)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value(ERRORES + "inventario-no-disponible"))
                .andExpect(header().string("Retry-After", "30"));
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("§7.3.5: sin bitacora despues de entregar, 503 auditoria-no-disponible con Retry-After")
    void auditoriaCaida() throws Exception {
        when(inventario.entregar(anyString(), eq(CLAVE), eq(UID), eq("p-hacha"), eq(2))).thenReturn(hecha(true));
        doThrow(new AuditoriaNoDisponibleException("No se pudo completar la operación."))
                .when(auditoria).registrar(anyString(), anyString(), anyString(), any(), anyString(), anyString(),
                        anyString(), anyString());

        entregar(7, "ADMINISTRADOR", CUERPO, CLAVE)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value(ERRORES + "auditoria-no-disponible"))
                .andExpect(header().exists("Retry-After"));
    }
}
