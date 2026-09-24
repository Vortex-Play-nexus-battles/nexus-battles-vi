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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PerfilControllerTest {

    private static final UUID UID_SANTI = UUID.fromString("7b0c8f3e-6a1d-4c2b-9f4e-1a2b3c4d5e6f");
    private static final UUID UID_OTRO = UUID.fromString("0f9e8d7c-6b5a-4c3d-8e2f-a1b2c3d4e5f6");

    @Mock
    private PerfilUsuarioService perfilUsuarioService;

    @Mock
    private HttpServletRequest request;

    private PerfilUsuario perfilDe(String apodo) {
        return perfilDe(apodo, null);
    }

    private PerfilUsuario perfilDe(String apodo, UUID uid) {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setApodo(apodo);
        usuario.setPublicId(uid);
        PerfilUsuario perfil = new PerfilUsuario();
        perfil.setUsuario(usuario);
        perfil.setNombres("Santiago");
        perfil.setApellidos("Sanabria");
        return perfil;
    }

    // ---------- GET obtenerMiPerfil por el uid del token (lo que manda cuenta.js) ----------

    /**
     * El defecto de «Mi cuenta» en AWS: el navegador solo conoce el uid y lo
     * pone en la ruta. Antes el parámetro era {@code Long} y la conversión
     * fallaba antes de llegar aquí; ningún jugador podía ver su perfil.
     */
    @Test
    void obtenerMiPerfil_aceptaElUidDelTokenEnLaRuta() {
        PerfilController controller = new PerfilController(perfilUsuarioService);
        when(perfilUsuarioService.obtenerPorIdentificadorPublico(UID_SANTI))
                .thenReturn(perfilDe("Santi", UID_SANTI));
        when(request.getAttribute("uidActual")).thenReturn(UID_SANTI.toString());

        ResponseEntity<?> response = controller.obtenerMiPerfil(UID_SANTI.toString(), request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(perfilUsuarioService, never()).obtenerPorUsuarioId(anyLong());
    }

    @Test
    void obtenerMiPerfil_uidDeOtroUsuarioEs403() {
        PerfilController controller = new PerfilController(perfilUsuarioService);
        when(perfilUsuarioService.obtenerPorIdentificadorPublico(UID_SANTI))
                .thenReturn(perfilDe("Santi", UID_SANTI));
        when(request.getAttribute("uidActual")).thenReturn(UID_OTRO.toString());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.obtenerMiPerfil(UID_SANTI.toString(), request));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    /**
     * Cambiar el apodo desde este mismo formulario deja el token con el apodo
     * viejo hasta volver a entrar. Con la comparación por apodo, la siguiente
     * lectura del propio perfil daba 403. Por uid sigue siendo el dueño.
     */
    @Test
    void obtenerMiPerfil_siguesSiendoDuenoTrasCambiarDeApodo() {
        PerfilController controller = new PerfilController(perfilUsuarioService);
        when(perfilUsuarioService.obtenerPorIdentificadorPublico(UID_SANTI))
                .thenReturn(perfilDe("SantiNuevo", UID_SANTI));
        when(request.getAttribute("uidActual")).thenReturn(UID_SANTI.toString());
        lenient().when(request.getAttribute("usuarioActual")).thenReturn("SantiViejo");

        ResponseEntity<?> response = controller.obtenerMiPerfil(UID_SANTI.toString(), request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void obtenerMiPerfil_uidSinPerfilEs404() {
        PerfilController controller = new PerfilController(perfilUsuarioService);
        when(perfilUsuarioService.obtenerPorIdentificadorPublico(UID_SANTI))
                .thenThrow(new IllegalStateException("No existe perfil"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.obtenerMiPerfil(UID_SANTI.toString(), request));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void obtenerMiPerfil_segmentoQueNoEsNiUuidNiNumeroEs404() {
        PerfilController controller = new PerfilController(perfilUsuarioService);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.obtenerMiPerfil("undefined", request));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verifyNoInteractions(perfilUsuarioService);
    }

    // ---------- GET por id numérico (compatibilidad) y respaldo por apodo ----------

    @Test
    void obtenerMiPerfil_devuelveOkCuandoEsElDueno() {
        PerfilController controller = new PerfilController(perfilUsuarioService);
        when(perfilUsuarioService.obtenerPorUsuarioId(1L)).thenReturn(perfilDe("Santi"));
        when(request.getAttribute("usuarioActual")).thenReturn("Santi");

        ResponseEntity<?> response = controller.obtenerMiPerfil("1", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void obtenerMiPerfil_aceptaDuenoSinImportarMayusculas() {
        PerfilController controller = new PerfilController(perfilUsuarioService);
        when(perfilUsuarioService.obtenerPorUsuarioId(1L)).thenReturn(perfilDe("Santi"));
        when(request.getAttribute("usuarioActual")).thenReturn("SANTI");

        ResponseEntity<?> response = controller.obtenerMiPerfil("1", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void obtenerMiPerfil_lanza404CuandoNoExiste() {
        PerfilController controller = new PerfilController(perfilUsuarioService);
        when(perfilUsuarioService.obtenerPorUsuarioId(1L))
                .thenThrow(new IllegalStateException("No existe perfil"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.obtenerMiPerfil("1", request));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void obtenerMiPerfil_lanza403CuandoNoEsElDueno() {
        PerfilController controller = new PerfilController(perfilUsuarioService);
        when(perfilUsuarioService.obtenerPorUsuarioId(1L)).thenReturn(perfilDe("Santi"));
        when(request.getAttribute("usuarioActual")).thenReturn("Otro");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.obtenerMiPerfil("1", request));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void obtenerMiPerfil_lanza403CuandoNoHaySolicitante() {
        PerfilController controller = new PerfilController(perfilUsuarioService);
        when(perfilUsuarioService.obtenerPorUsuarioId(1L)).thenReturn(perfilDe("Santi"));
        when(request.getAttribute("usuarioActual")).thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.obtenerMiPerfil("1", request));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    // ---------- PUT actualizarMiPerfil ----------

    @Test
    void actualizarMiPerfil_porUidActualizaAlUsuarioDelPerfil() {
        PerfilController controller = new PerfilController(perfilUsuarioService);
        when(perfilUsuarioService.obtenerPorIdentificadorPublico(UID_SANTI))
                .thenReturn(perfilDe("Santi", UID_SANTI));
        when(request.getAttribute("uidActual")).thenReturn(UID_SANTI.toString());

        ActualizarPerfilRequest datos = new ActualizarPerfilRequest();
        datos.setNombres("Santiago");
        datos.setApellidos("Sanabria");
        when(perfilUsuarioService.actualizarPerfilPropio(1L, "Santiago", "Sanabria", null, null, null))
                .thenReturn(perfilDe("Santi", UID_SANTI));

        ResponseEntity<?> response = controller.actualizarMiPerfil(UID_SANTI.toString(), datos, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

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

        ResponseEntity<?> response = controller.actualizarMiPerfil("1", datos, request);

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

        ResponseEntity<?> response = controller.actualizarMiPerfil("1", datos, request);

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
                () -> controller.actualizarMiPerfil("1", datos, request));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void actualizarMiPerfil_lanza404CuandoNoExiste() {
        PerfilController controller = new PerfilController(perfilUsuarioService);
        when(perfilUsuarioService.obtenerPorUsuarioId(1L))
                .thenThrow(new IllegalStateException("No existe perfil"));

        ActualizarPerfilRequest datos = new ActualizarPerfilRequest();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.actualizarMiPerfil("1", datos, request));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
