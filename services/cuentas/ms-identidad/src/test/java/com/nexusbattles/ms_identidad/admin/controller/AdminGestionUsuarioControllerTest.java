package com.nexusbattles.ms_identidad.admin.controller;

import com.nexusbattles.ms_identidad.admin.dto.SuspenderCuentaRequest;
import com.nexusbattles.ms_identidad.admin.service.AdminGestionUsuarioService;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.perfiles.dto.ActualizarPerfilRequest;
import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminGestionUsuarioControllerTest {

    @Mock
    private AdminGestionUsuarioService adminGestionUsuarioService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private PerfilUsuario perfilUsuario;

    @Test
    void debeEditarPerfil() {
        AdminGestionUsuarioController controller =
            new AdminGestionUsuarioController(adminGestionUsuarioService);

        ActualizarPerfilRequest datos = new ActualizarPerfilRequest();
        datos.setNombres("Santiago");
        datos.setApellidos("Sanabria");
        datos.setAvatar("avatar.png");
        datos.setBiografia("Biografía");
        datos.setPreferencias("preferencias");
        datos.setApodo("Santi");

        Usuario usuario = new Usuario();
        usuario.setApodo("Santi");

        when(perfilUsuario.getUsuario())
            .thenReturn(usuario);

        when(request.getHeader("X-User-Name"))
            .thenReturn("admin");

        when(adminGestionUsuarioService.editarPerfilDeUsuario(
            1L,
            "Santiago",
            "Sanabria",
            "avatar.png",
            "Biografía",
            "preferencias",
            "Santi",
            "admin"
        )).thenReturn(perfilUsuario);

        ResponseEntity<?> response = controller.editarPerfil(
            1L,
            datos,
            request
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());

        verify(adminGestionUsuarioService).editarPerfilDeUsuario(
            1L,
            "Santiago",
            "Sanabria",
            "avatar.png",
            "Biografía",
            "preferencias",
            "Santi",
            "admin"
        );
    }

    @Test
    void editarPerfilDebeRetornarBadRequest() {
        AdminGestionUsuarioController controller =
            new AdminGestionUsuarioController(adminGestionUsuarioService);

        ActualizarPerfilRequest datos = new ActualizarPerfilRequest();

        when(request.getHeader("X-User-Name"))
            .thenReturn("admin");

        when(adminGestionUsuarioService.editarPerfilDeUsuario(
            anyLong(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        )).thenThrow(new IllegalArgumentException("Datos inválidos"));

        ResponseEntity<?> response = controller.editarPerfil(
            1L,
            datos,
            request
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Datos inválidos", response.getBody());
    }

    @Test
    void editarPerfilDebeRetornarNotFound() {
        AdminGestionUsuarioController controller =
            new AdminGestionUsuarioController(adminGestionUsuarioService);

        ActualizarPerfilRequest datos = new ActualizarPerfilRequest();

        when(request.getHeader("X-User-Name"))
            .thenReturn("admin");

        when(adminGestionUsuarioService.editarPerfilDeUsuario(
            anyLong(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        )).thenThrow(new IllegalStateException("Usuario no encontrado"));

        ResponseEntity<?> response = controller.editarPerfil(
            1L,
            datos,
            request
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Usuario no encontrado", response.getBody());
    }

    @Test
    void debeSuspenderCuenta() {
        AdminGestionUsuarioController controller =
            new AdminGestionUsuarioController(adminGestionUsuarioService);

        SuspenderCuentaRequest datos = new SuspenderCuentaRequest();

        LocalDateTime fecha = LocalDateTime.of(2026, 9, 10, 12, 0);
        datos.setSuspendidoHasta(fecha);

        when(request.getHeader("X-User-Name"))
            .thenReturn("admin");

        ResponseEntity<?> response = controller.suspender(
            1L,
            datos,
            request
        );

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        verify(adminGestionUsuarioService).suspenderCuenta(
            1L,
            fecha,
            "admin"
        );
    }

    @Test
    void suspenderDebeRetornarBadRequest() {
        AdminGestionUsuarioController controller =
            new AdminGestionUsuarioController(adminGestionUsuarioService);

        SuspenderCuentaRequest datos = new SuspenderCuentaRequest();

        when(request.getHeader("X-User-Name"))
            .thenReturn("admin");

        doThrow(new IllegalArgumentException("Fecha inválida"))
            .when(adminGestionUsuarioService)
            .suspenderCuenta(1L, null, "admin");

        ResponseEntity<?> response = controller.suspender(
            1L,
            datos,
            request
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Fecha inválida", response.getBody());
    }

    @Test
    void suspenderDebeRetornarNotFound() {
        AdminGestionUsuarioController controller =
            new AdminGestionUsuarioController(adminGestionUsuarioService);

        SuspenderCuentaRequest datos = new SuspenderCuentaRequest();

        when(request.getHeader("X-User-Name"))
            .thenReturn("admin");

        doThrow(new IllegalStateException("Usuario no encontrado"))
            .when(adminGestionUsuarioService)
            .suspenderCuenta(1L, null, "admin");

        ResponseEntity<?> response = controller.suspender(
            1L,
            datos,
            request
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Usuario no encontrado", response.getBody());
    }

    @Test
    void debeBanearCuenta() {
        AdminGestionUsuarioController controller =
            new AdminGestionUsuarioController(adminGestionUsuarioService);

        when(request.getHeader("X-User-Name"))
            .thenReturn("admin");

        ResponseEntity<?> response = controller.banear(
            1L,
            request
        );

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        verify(adminGestionUsuarioService)
            .banearCuenta(1L, "admin");
    }

    @Test
    void banearDebeRetornarBadRequest() {
        AdminGestionUsuarioController controller =
            new AdminGestionUsuarioController(adminGestionUsuarioService);

        when(request.getHeader("X-User-Name"))
            .thenReturn("admin");

        doThrow(new IllegalArgumentException("No se puede banear"))
            .when(adminGestionUsuarioService)
            .banearCuenta(1L, "admin");

        ResponseEntity<?> response = controller.banear(
            1L,
            request
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("No se puede banear", response.getBody());
    }

    @Test
    void banearDebeRetornarNotFound() {
        AdminGestionUsuarioController controller =
            new AdminGestionUsuarioController(adminGestionUsuarioService);

        when(request.getHeader("X-User-Name"))
            .thenReturn("admin");

        doThrow(new IllegalStateException("Usuario no encontrado"))
            .when(adminGestionUsuarioService)
            .banearCuenta(1L, "admin");

        ResponseEntity<?> response = controller.banear(
            1L,
            request
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Usuario no encontrado", response.getBody());
    }

    @Test
    void debeReactivarCuenta() {
        AdminGestionUsuarioController controller =
            new AdminGestionUsuarioController(adminGestionUsuarioService);

        when(request.getHeader("X-User-Name"))
            .thenReturn("admin");

        ResponseEntity<?> response = controller.reactivar(
            1L,
            request
        );

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        verify(adminGestionUsuarioService)
            .reactivarCuenta(1L, "admin");
    }

    @Test
    void reactivarDebeRetornarBadRequest() {
        AdminGestionUsuarioController controller =
            new AdminGestionUsuarioController(adminGestionUsuarioService);

        when(request.getHeader("X-User-Name"))
            .thenReturn("admin");

        doThrow(new IllegalArgumentException("Cuenta baneada"))
            .when(adminGestionUsuarioService)
            .reactivarCuenta(1L, "admin");

        ResponseEntity<?> response = controller.reactivar(
            1L,
            request
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Cuenta baneada", response.getBody());
    }

    @Test
    void reactivarDebeRetornarNotFound() {
        AdminGestionUsuarioController controller =
            new AdminGestionUsuarioController(adminGestionUsuarioService);

        when(request.getHeader("X-User-Name"))
            .thenReturn("admin");

        doThrow(new IllegalStateException("Usuario no encontrado"))
            .when(adminGestionUsuarioService)
            .reactivarCuenta(1L, "admin");

        ResponseEntity<?> response = controller.reactivar(
            1L,
            request
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Usuario no encontrado", response.getBody());
    }

    @Test
    void debeRestablecerPassword() {
        AdminGestionUsuarioController controller =
            new AdminGestionUsuarioController(adminGestionUsuarioService);

        when(request.getHeader("X-User-Name"))
            .thenReturn("admin");

        ResponseEntity<?> response = controller.restablecerPassword(
            1L,
            request
        );

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        verify(adminGestionUsuarioService)
            .restablecerPassword(1L, "admin");
    }

    @Test
    void restablecerPasswordDebeRetornarNotFound() {
        AdminGestionUsuarioController controller =
            new AdminGestionUsuarioController(adminGestionUsuarioService);

        when(request.getHeader("X-User-Name"))
            .thenReturn("admin");

        doThrow(new IllegalStateException("Usuario no encontrado"))
            .when(adminGestionUsuarioService)
            .restablecerPassword(1L, "admin");

        ResponseEntity<?> response = controller.restablecerPassword(
            1L,
            request
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Usuario no encontrado", response.getBody());
    }
}
