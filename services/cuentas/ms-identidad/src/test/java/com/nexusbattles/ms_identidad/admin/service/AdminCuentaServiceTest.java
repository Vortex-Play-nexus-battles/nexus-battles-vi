package com.nexusbattles.ms_identidad.admin.service;

import com.nexusbattles.ms_identidad.admin.dto.CrearCuentaAdminRequest;
import com.nexusbattles.ms_identidad.auditoria.client.AuditoriaClient;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.service.AuthAdminService;
import com.nexusbattles.ms_identidad.perfiles.service.PerfilUsuarioService;
import com.nexusbattles.ms_identidad.rbac.model.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminCuentaServiceTest {

    private static final String IP_ORIGEN = "127.0.0.1";

    @Mock
    private AuthAdminService authAdminService;

    @Mock
    private PerfilUsuarioService perfilUsuarioService;

    @Mock
    private AuditoriaClient auditoriaClient;

    @InjectMocks
    private AdminCuentaService service;

    @Test
    void debeCrearCuentaConRolModerador() {
        CrearCuentaAdminRequest datos = crearDatos("MODERADOR");

        Usuario usuarioCreado = new Usuario();
        usuarioCreado.setApodo("Santi");

        when(authAdminService.crearCuentaConRol(
            "Santiago",
            "Sanabria",
            "santiago@test.com",
            "Santi",
            "avatar.png",
            Role.MODERADOR
        )).thenReturn(usuarioCreado);

        Usuario resultado = service.crearCuentaAdministrativa(datos, "admin", IP_ORIGEN);

        assertEquals(usuarioCreado, resultado);

        verify(authAdminService).crearCuentaConRol(
            "Santiago",
            "Sanabria",
            "santiago@test.com",
            "Santi",
            "avatar.png",
            Role.MODERADOR
        );

        verify(perfilUsuarioService).crearPerfil(
            usuarioCreado,
            "Santiago",
            "Sanabria",
            "avatar.png"
        );

        verify(auditoriaClient).registrar(
            "CREACION",
            "admin",
            "Santi",
            null,
            "rol=MODERADOR, estado=INACTIVO",
            "Creación de cuenta administrativa",
            IP_ORIGEN
        );
    }

    @Test
    void debeCrearCuentaConRolAdministrador() {
        CrearCuentaAdminRequest datos = crearDatos("ADMINISTRADOR");

        Usuario usuarioCreado = new Usuario();
        usuarioCreado.setApodo("Santi");

        when(authAdminService.crearCuentaConRol(
            "Santiago",
            "Sanabria",
            "santiago@test.com",
            "Santi",
            "avatar.png",
            Role.ADMINISTRADOR
        )).thenReturn(usuarioCreado);

        Usuario resultado = service.crearCuentaAdministrativa(datos, "admin", IP_ORIGEN);

        assertEquals(usuarioCreado, resultado);

        verify(authAdminService).crearCuentaConRol(
            "Santiago",
            "Sanabria",
            "santiago@test.com",
            "Santi",
            "avatar.png",
            Role.ADMINISTRADOR
        );

        verify(perfilUsuarioService).crearPerfil(
            usuarioCreado,
            "Santiago",
            "Sanabria",
            "avatar.png"
        );

        verify(auditoriaClient).registrar(
            "CREACION",
            "admin",
            "Santi",
            null,
            "rol=ADMINISTRADOR, estado=INACTIVO",
            "Creación de cuenta administrativa",
            IP_ORIGEN
        );
    }

    @Test
    void noDebeCrearCuentaConRolNoPermitido() {
        CrearCuentaAdminRequest datos = crearDatos("JUGADOR");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> service.crearCuentaAdministrativa(datos, "admin", IP_ORIGEN)
        );

        assertEquals(
            "Solo se pueden crear cuentas con rol MODERADOR o ADMINISTRADOR desde este endpoint.",
            exception.getMessage()
        );

        verifyNoInteractions(authAdminService);
        verifyNoInteractions(perfilUsuarioService);
        verifyNoInteractions(auditoriaClient);
    }

    @Test
    void debeAceptarRolConEspaciosYMinusculas() {
        CrearCuentaAdminRequest datos = crearDatos("  moderador  ");

        Usuario usuarioCreado = new Usuario();
        usuarioCreado.setApodo("Santi");

        when(authAdminService.crearCuentaConRol(
            "Santiago",
            "Sanabria",
            "santiago@test.com",
            "Santi",
            "avatar.png",
            Role.MODERADOR
        )).thenReturn(usuarioCreado);

        Usuario resultado = service.crearCuentaAdministrativa(datos, "admin", IP_ORIGEN);

        assertEquals(usuarioCreado, resultado);

        verify(authAdminService).crearCuentaConRol(
            "Santiago",
            "Sanabria",
            "santiago@test.com",
            "Santi",
            "avatar.png",
            Role.MODERADOR
        );
    }

    private CrearCuentaAdminRequest crearDatos(String rol) {
        CrearCuentaAdminRequest datos = new CrearCuentaAdminRequest();

        datos.setNombres("Santiago");
        datos.setApellidos("Sanabria");
        datos.setEmail("santiago@test.com");
        datos.setPassword("password123");
        datos.setApodo("Santi");
        datos.setAvatar("avatar.png");
        datos.setRolNombre(rol);

        return datos;
    }
}
