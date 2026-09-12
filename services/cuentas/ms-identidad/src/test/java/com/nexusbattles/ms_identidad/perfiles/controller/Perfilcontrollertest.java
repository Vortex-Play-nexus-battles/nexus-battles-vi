package com.nexusbattles.ms_identidad.perfiles.controller;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.perfiles.dto.ActualizarPerfilRequest;
import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import com.nexusbattles.ms_identidad.perfiles.service.PerfilUsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PerfilControllerTest {

    @Mock
    private PerfilUsuarioService perfilUsuarioService;

    @Mock
    private HttpServletRequest request;

    private PerfilUsuario perfilDe(String apodo) {
        Usuario usuario = new Usuario();
        usuario.setApodo(apodo);
        PerfilUsuario perfil = new PerfilUsuario();
        perfil.setUsuario(usuario);
        perfil.setNombres("Santiago");
        perfil.setApellidos("Sanabria");
        return perfil;
    }

    // ---------- GET obtenerMiPerfil ----------

    @Test
    void obtenerMiPerfil_devuelveOkCuandoEsElDueno() {
        PerfilController controller = new PerfilController(perfilUsuarioService);
        when(perfilUsuarioService.obtenerPorUsuarioId(1L)).thenReturn(perfilDe("Santi"));
        when(request.getAttribute("usuarioActual")).thenReturn("Santi");

        ResponseEntity<?> response = controller.obtenerMiPerfil(1L, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void obtenerMiPerfil_aceptaDuenoSinImportarMayusculas() {
        PerfilController controller = new PerfilController(perfilUsuarioService);
        when(perfilUsuarioService.obtenerPorUsuarioId(1L)).thenReturn(perfilDe("Santi"));
        when(request.getAttribute("usuarioActual")).thenReturn("SANTI");

        ResponseEntity<?> response = controller.obtenerMiPerfil(1L, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void obtenerMiPerfil_lanza404CuandoNoExiste() {
        PerfilController controller = new PerfilController(perfilUsuarioService);
        when(perfilUsuarioService.obtenerPorUsuarioId(1L))
                .thenThrow(new IllegalStateException("No existe perfil"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.obtenerMiPerfil(1L, request));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void obtenerMiPerfil_lanza403CuandoNoEsElDueno() {
        PerfilController controller = new PerfilController(perfilUsuarioService);
        when(perfilUsuarioService.obtenerPorUsuarioId(1L)).thenReturn(perfilDe("Santi"));
        when(request.getAttribute("usuarioActual")).thenReturn("Otro");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.obtenerMiPerfil(1L, request));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void obtenerMiPerfil_lanza403CuandoNoHaySolicitante() {
        PerfilController controller = new PerfilController(perfilUsuarioService);
        when(perfilUsuarioService.obtenerPorUsuarioId(1L)).thenReturn(perfilDe("Santi"));
        when(request.getAttribute("usuarioActual")).thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.obtenerMiPerfil(1L, request));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    // ---------- PUT actualizarMiPerfil ----------

    @Test
    void actualizarMiPerfil_devuelveOkCuandoTodoValido() {
        PerfilController controller = new PerfilController(perfilUsuarioService);
        when(perfilUsuarioService.obtenerPorUsuarioId(1L)).thenReturn(perfilDe("Santi"));
        when(request.getAttribute("usuarioActual")).thenReturn("Santi");

        ActualizarPerfilRequest datos = new ActualizarPerfilRequest();
        datos.setNombres("Santiago");
        datos.setApellidos("Sanabria");
        datos.setPreferencias("prefs");

        when(perfilUsuarioService.actualizarPerfilPropio(
                1L, "Santiago", "Sanabria", null, "prefs", null))
                .thenReturn(perfilDe("Santi"));

        ResponseEntity<?> response = controller.actualizarMiPerfil(1L, datos, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void actualizarMiPerfil_devuelveBadRequestCuandoApodoInvalido() {
        PerfilController controller = new PerfilController(perfilUsuarioService);
        when(perfilUsuarioService.obtenerPorUsuarioId(1L)).thenReturn(perfilDe("Santi"));
        when(request.getAttribute("usuarioActual")).thenReturn("Santi");

        ActualizarPerfilRequest datos = new ActualizarPerfilRequest();
        datos.setNombres("Santiago");
        datos.setApellidos("Sanabria");

        when(perfilUsuarioService.actualizarPerfilPropio(
                anyLong(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("El apodo ya está en uso."));

        ResponseEntity<?> response = controller.actualizarMiPerfil(1L, datos, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("El apodo ya está en uso.", response.getBody());
    }

    @Test
    void actualizarMiPerfil_lanza403CuandoNoEsElDueno() {
        PerfilController controller = new PerfilController(perfilUsuarioService);
        when(perfilUsuarioService.obtenerPorUsuarioId(1L)).thenReturn(perfilDe("Santi"));
        when(request.getAttribute("usuarioActual")).thenReturn("Intruso");

        ActualizarPerfilRequest datos = new ActualizarPerfilRequest();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.actualizarMiPerfil(1L, datos, request));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void actualizarMiPerfil_lanza404CuandoNoExiste() {
        PerfilController controller = new PerfilController(perfilUsuarioService);
        when(perfilUsuarioService.obtenerPorUsuarioId(1L))
                .thenThrow(new IllegalStateException("No existe perfil"));

        ActualizarPerfilRequest datos = new ActualizarPerfilRequest();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.actualizarMiPerfil(1L, datos, request));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
