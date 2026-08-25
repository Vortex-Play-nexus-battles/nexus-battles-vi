package com.nexusbattles.ms_identidad.rbac;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.rbac.model.Role;
import com.nexusbattles.ms_identidad.rbac.service.RoleAssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleAssignmentServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    private RoleAssignmentService roleAssignmentService;

    @BeforeEach
    void setUp() {
        roleAssignmentService = new RoleAssignmentService(usuarioRepository);
    }

    @Test
    void debeRechazarCambioCuandoSolicitanteNoEsSuperAdministrador() {

        Usuario solicitante = crearUsuario(
                1L,
                "Administrador");

        Usuario usuarioObjetivo = crearUsuario(
                2L,
                "Jugador");

        when(usuarioRepository.findById(1L))
                .thenReturn(Optional.of(solicitante));

        when(usuarioRepository.findById(2L))
                .thenReturn(Optional.of(usuarioObjetivo));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> roleAssignmentService.cambiarRol(
                        1L,
                        2L,
                        Role.MODERADOR)
        );

        assertEquals(
                "Solo un Super Administrador puede modificar roles.",
                exception.getMessage()
        );

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void debeImpedirDegradarAlUltimoSuperAdministrador() {

        Usuario superAdministrador = crearUsuario(
                1L,
                Role.SUPER_ADMINISTRADOR.getDisplayName());

        when(usuarioRepository.findById(1L))
                .thenReturn(Optional.of(superAdministrador));

        when(usuarioRepository.countByRol(
                Role.SUPER_ADMINISTRADOR.getDisplayName()))
                .thenReturn(1L);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> roleAssignmentService.cambiarRol(
                        1L,
                        1L,
                        Role.ADMINISTRADOR)
        );

        assertEquals(
                "No se puede modificar el rol del último Super Administrador.",
                exception.getMessage()
        );

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void debeCambiarRolCorrectamenteCuandoSolicitanteEsSuperAdministrador() {

        Usuario solicitante = crearUsuario(
                1L,
                Role.SUPER_ADMINISTRADOR.getDisplayName());

        Usuario usuarioObjetivo = crearUsuario(
                2L,
                Role.JUGADOR.getDisplayName());

        when(usuarioRepository.findById(1L))
                .thenReturn(Optional.of(solicitante));

        when(usuarioRepository.findById(2L))
                .thenReturn(Optional.of(usuarioObjetivo));

        when(usuarioRepository.countByRol(
                Role.SUPER_ADMINISTRADOR.getDisplayName()))
                .thenReturn(1L);

        when(usuarioRepository.save(usuarioObjetivo))
                .thenReturn(usuarioObjetivo);

        Usuario resultado = roleAssignmentService.cambiarRol(
                1L,
                2L,
                Role.MODERADOR);

        assertEquals(
                Role.MODERADOR.getDisplayName(),
                resultado.getRol());

        verify(usuarioRepository).save(usuarioObjetivo);
    }

    private Usuario crearUsuario(Long id, String rol) {

        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setRol(rol);

        return usuario;
    }
}
