package com.nexusbattles.ms_identidad.admin.controller;

import com.nexusbattles.ms_identidad.admin.dto.BanearCuentaRequest;
import com.nexusbattles.ms_identidad.admin.dto.SuspenderCuentaRequest;
import com.nexusbattles.ms_identidad.admin.service.AdminGestionUsuarioService;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.perfiles.dto.ActualizarPerfilRequest;
import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import com.nexusbattles.ms_identidad.sanciones.SancionRechazadaException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminGestionUsuarioControllerTest {

    private static final String IP_ORIGEN = "127.0.0.1";
    private static final String TOKEN = "Bearer token-del-panel";

    @Mock
    private AdminGestionUsuarioService adminGestionUsuarioService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private PerfilUsuario perfilUsuario;

    private AdminGestionUsuarioController controller;

    @BeforeEach
    void configurarRequest() {
        // La identidad ahora la deja SecurityInterceptor en el request
        // attribute "usuarioActual" (JWT/RBAC), ya no en el header X-User-Name.
        when(request.getAttribute("usuarioActual")).thenReturn("admin");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn(TOKEN);
        when(request.getRemoteAddr()).thenReturn(IP_ORIGEN);
        when(request.getRequestURI()).thenReturn("/api/v1/admin/usuarios/1/banear");
        controller = new AdminGestionUsuarioController(adminGestionUsuarioService);
    }

    @Test
    void debeEditarPerfil() {
        ActualizarPerfilRequest datos = new ActualizarPerfilRequest();
        datos.setNombres("Santiago");
        datos.setApellidos("Sanabria");
        datos.setPreferencias("preferencias");
        datos.setApodo("Santi");
        Usuario usuario = new Usuario();
        usuario.setApodo("Santi");
        when(perfilUsuario.getUsuario()).thenReturn(usuario);
        when(adminGestionUsuarioService.editarPerfilDeUsuario(1L, "Santiago", "Sanabria", null, "preferencias",
            "Santi", "admin", IP_ORIGEN)).thenReturn(perfilUsuario);

        ResponseEntity<?> response = controller.editarPerfil(1L, datos, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void editarPerfilDebeRetornarBadRequestYNotFound() {
        ActualizarPerfilRequest datos = new ActualizarPerfilRequest();
        when(adminGestionUsuarioService.editarPerfilDeUsuario(anyLong(), any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(new IllegalArgumentException("Datos inválidos"))
            .thenThrow(new IllegalStateException("Usuario no encontrado"));

        ResponseEntity<?> invalido = controller.editarPerfil(1L, datos, request);
        assertEquals(HttpStatus.BAD_REQUEST, invalido.getStatusCode());
        assertEquals("Datos inválidos", invalido.getBody());

        ResponseEntity<?> noEncontrado = controller.editarPerfil(1L, datos, request);
        assertEquals(HttpStatus.NOT_FOUND, noEncontrado.getStatusCode());
    }

    @Test
    void debeSuspenderCuentaConElTokenYElMotivo() {
        SuspenderCuentaRequest datos = new SuspenderCuentaRequest();
        LocalDateTime fecha = LocalDateTime.of(2026, 10, 10, 12, 0);
        datos.setSuspendidoHasta(fecha);
        datos.setMotivo("Spam");

        ResponseEntity<?> response = controller.suspender(1L, datos, request);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(adminGestionUsuarioService).suspenderCuenta(1L, fecha, "Spam", TOKEN, "admin", IP_ORIGEN);
    }

    @Test
    void suspenderTraduceLosRechazos() {
        SuspenderCuentaRequest datos = new SuspenderCuentaRequest();
        doThrow(new IllegalArgumentException("Fecha inválida"))
            .doThrow(new IllegalStateException("Usuario no encontrado"))
            .doThrow(new SancionRechazadaException(409, "El usuario ya está baneado."))
            .when(adminGestionUsuarioService).suspenderCuenta(1L, null, null, TOKEN, "admin", IP_ORIGEN);

        assertEquals(HttpStatus.BAD_REQUEST, controller.suspender(1L, datos, request).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, controller.suspender(1L, datos, request).getStatusCode());
        ResponseEntity<?> conflicto = controller.suspender(1L, datos, request);
        assertEquals(HttpStatus.CONFLICT, conflicto.getStatusCode());
        assertEquals("El usuario ya está baneado.", conflicto.getBody());
    }

    @Test
    void debeBanearCuentaConOSinMotivo() {
        assertEquals(HttpStatus.NO_CONTENT, controller.banear(1L, null, request).getStatusCode());
        verify(adminGestionUsuarioService).banearCuenta(1L, null, TOKEN, "admin", IP_ORIGEN);

        controller.banear(1L, new BanearCuentaRequest("Fraude en subastas"), request);
        verify(adminGestionUsuarioService).banearCuenta(1L, "Fraude en subastas", TOKEN, "admin", IP_ORIGEN);
    }

    @Test
    void banearTraduceLosRechazos() {
        doThrow(new IllegalArgumentException("No se puede banear"))
            .doThrow(new IllegalStateException("Usuario no encontrado"))
            .doThrow(new SancionRechazadaException(403, "Un moderador no puede banear."))
            .doThrow(new SancionRechazadaException(404, "No existe."))
            .doThrow(new SancionRechazadaException(422, "Otra cosa."))
            .when(adminGestionUsuarioService).banearCuenta(1L, null, TOKEN, "admin", IP_ORIGEN);

        assertEquals(HttpStatus.BAD_REQUEST, controller.banear(1L, null, request).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, controller.banear(1L, null, request).getStatusCode());
        ResponseEntity<?> prohibido = controller.banear(1L, null, request);
        assertEquals(HttpStatus.FORBIDDEN, prohibido.getStatusCode());
        ProblemDetail problema = (ProblemDetail) prohibido.getBody();
        assertEquals("https://nexusbattles.upb.edu.co/errors/forbidden", problema.getType().toString());
        assertEquals("Un moderador no puede banear.", problema.getDetail());
        assertEquals(HttpStatus.NOT_FOUND, controller.banear(1L, null, request).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, controller.banear(1L, null, request).getStatusCode());
    }

    @Test
    void debeReactivarCuenta() {
        assertEquals(HttpStatus.NO_CONTENT, controller.reactivar(1L, request).getStatusCode());
        verify(adminGestionUsuarioService).reactivarCuenta(1L, TOKEN, "admin", IP_ORIGEN);
    }

    @Test
    void reactivarTraduceLosRechazos() {
        doThrow(new IllegalArgumentException("Cuenta baneada"))
            .doThrow(new IllegalStateException("Usuario no encontrado"))
            .doThrow(new SancionRechazadaException(401, "Token no valido para moderacion."))
            .when(adminGestionUsuarioService).reactivarCuenta(1L, TOKEN, "admin", IP_ORIGEN);

        ResponseEntity<?> baneada = controller.reactivar(1L, request);
        assertEquals(HttpStatus.BAD_REQUEST, baneada.getStatusCode());
        assertEquals("Cuenta baneada", baneada.getBody());
        assertEquals(HttpStatus.NOT_FOUND, controller.reactivar(1L, request).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller.reactivar(1L, request).getStatusCode());
    }

    @Test
    void restablecerPassword() {
        assertEquals(HttpStatus.NO_CONTENT, controller.restablecerPassword(1L, request).getStatusCode());
        verify(adminGestionUsuarioService).restablecerPassword(1L, "admin", IP_ORIGEN);

        doThrow(new IllegalStateException("Usuario no encontrado"))
            .when(adminGestionUsuarioService).restablecerPassword(2L, "admin", IP_ORIGEN);
        assertEquals(HttpStatus.NOT_FOUND, controller.restablecerPassword(2L, request).getStatusCode());
    }

    @Test
    void laIpRealEsElPrimerXForwardedFor() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5, 10.0.0.1");
        controller.reactivar(1L, request);
        verify(adminGestionUsuarioService).reactivarCuenta(1L, TOKEN, "admin", "203.0.113.5");
    }
}
