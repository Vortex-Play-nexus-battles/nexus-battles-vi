package com.nexusbattles.ms_identidad.perfiles.service;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.AvatarStorageService;
import com.nexusbattles.ms_identidad.auth.validation.ApodoBlacklistValidator;
import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import com.nexusbattles.ms_identidad.perfiles.repository.PerfilUsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PerfilUsuarioServiceTest {

    @Mock
    private PerfilUsuarioRepository perfilUsuarioRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ApodoBlacklistValidator apodoBlacklistValidator;

    @Mock
    private AvatarStorageService avatarStorageService;

    @Mock
    private MultipartFile archivoAvatar;

    @InjectMocks
    private PerfilUsuarioService service;

    private PerfilUsuario perfilConApodo(String apodo) {
        Usuario usuario = new Usuario();
        usuario.setApodo(apodo);
        PerfilUsuario perfil = new PerfilUsuario();
        perfil.setUsuario(usuario);
        perfil.setNombres("Nombre");
        perfil.setApellidos("Apellido");
        return perfil;
    }

    // ---------- crearPerfil ----------

    @Test
    void crearPerfil_guardaConLosDatosDados() {
        Usuario usuario = new Usuario();
        usuario.setApodo("Santi");
        PerfilUsuario guardado = perfilConApodo("Santi");
        when(perfilUsuarioRepository.save(any(PerfilUsuario.class))).thenReturn(guardado);

        PerfilUsuario resultado = service.crearPerfil(usuario, "Santiago", "Sanabria", "/avatar.png");

        assertEquals(guardado, resultado);
        verify(perfilUsuarioRepository).save(any(PerfilUsuario.class));
    }

    // ---------- obtenerPorUsuarioId ----------

    @Test
    void obtenerPorUsuarioId_devuelvePerfilCuandoExiste() {
        PerfilUsuario perfil = perfilConApodo("Santi");
        when(perfilUsuarioRepository.findByIdConUsuario(1L)).thenReturn(Optional.of(perfil));

        PerfilUsuario resultado = service.obtenerPorUsuarioId(1L);

        assertEquals(perfil, resultado);
    }

    @Test
    void obtenerPorUsuarioId_lanzaExcepcionCuandoNoExiste() {
        when(perfilUsuarioRepository.findByIdConUsuario(1L)).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.obtenerPorUsuarioId(1L));
        assertTrue(ex.getMessage().contains("No existe perfil"));
    }

    // ---------- actualizarPerfilPropio ----------

    @Test
    void actualizar_soloCamposBasicos_sinAvatarSinApodo() {
        PerfilUsuario perfil = perfilConApodo("Santi");
        when(perfilUsuarioRepository.findByIdConUsuario(1L)).thenReturn(Optional.of(perfil));

        PerfilUsuario resultado = service.actualizarPerfilPropio(
                1L, "NuevoNombre", "NuevoApellido", null, "prefs", null);

        assertEquals("NuevoNombre", resultado.getNombres());
        assertEquals("NuevoApellido", resultado.getApellidos());
        assertEquals("prefs", resultado.getPreferencias());
        verify(avatarStorageService, never()).guardarAvatar(any());
        verify(perfilUsuarioRepository).save(perfil);
    }

    @Test
    void actualizar_conAvatarNuevo_loGuardaYActualizaUrl() {
        PerfilUsuario perfil = perfilConApodo("Santi");
        when(perfilUsuarioRepository.findByIdConUsuario(1L)).thenReturn(Optional.of(perfil));
        when(archivoAvatar.isEmpty()).thenReturn(false);
        when(avatarStorageService.guardarAvatar(archivoAvatar)).thenReturn("/nuevo-avatar.png");

        PerfilUsuario resultado = service.actualizarPerfilPropio(
                1L, "Nombre", "Apellido", archivoAvatar, "prefs", null);

        assertEquals("/nuevo-avatar.png", resultado.getAvatar());
        verify(avatarStorageService).guardarAvatar(archivoAvatar);
    }

    @Test
    void actualizar_conAvatarVacio_noLoGuarda() {
        PerfilUsuario perfil = perfilConApodo("Santi");
        when(perfilUsuarioRepository.findByIdConUsuario(1L)).thenReturn(Optional.of(perfil));
        when(archivoAvatar.isEmpty()).thenReturn(true);

        service.actualizarPerfilPropio(1L, "Nombre", "Apellido", archivoAvatar, "prefs", null);

        verify(avatarStorageService, never()).guardarAvatar(any());
    }

    @Test
    void actualizar_conApodoNuevoValido_loCambia() {
        PerfilUsuario perfil = perfilConApodo("Santi");
        when(perfilUsuarioRepository.findByIdConUsuario(1L)).thenReturn(Optional.of(perfil));
        when(usuarioRepository.findByApodo("NuevoApodo")).thenReturn(Optional.empty());

        service.actualizarPerfilPropio(1L, "Nombre", "Apellido", null, "prefs", "NuevoApodo");

        assertEquals("NuevoApodo", perfil.getUsuario().getApodo());
        verify(apodoBlacklistValidator).validar("NuevoApodo");
        verify(usuarioRepository).save(perfil.getUsuario());
    }

    @Test
    void actualizar_conMismoApodo_noRevalida() {
        PerfilUsuario perfil = perfilConApodo("Santi");
        when(perfilUsuarioRepository.findByIdConUsuario(1L)).thenReturn(Optional.of(perfil));

        service.actualizarPerfilPropio(1L, "Nombre", "Apellido", null, "prefs", "Santi");

        verify(apodoBlacklistValidator, never()).validar(any());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void actualizar_conApodoYaEnUso_lanzaExcepcion() {
        PerfilUsuario perfil = perfilConApodo("Santi");
        when(perfilUsuarioRepository.findByIdConUsuario(1L)).thenReturn(Optional.of(perfil));
        when(usuarioRepository.findByApodo("Ocupado")).thenReturn(Optional.of(new Usuario()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.actualizarPerfilPropio(1L, "Nombre", "Apellido", null, "prefs", "Ocupado"));
        assertEquals("El apodo ya está en uso.", ex.getMessage());
    }

    @Test
    void actualizar_conApodoEnBlacklist_propagaExcepcion() {
        PerfilUsuario perfil = perfilConApodo("Santi");
        when(perfilUsuarioRepository.findByIdConUsuario(1L)).thenReturn(Optional.of(perfil));
        doThrow(new IllegalArgumentException("Apodo prohibido"))
                .when(apodoBlacklistValidator).validar("Prohibido");

        assertThrows(IllegalArgumentException.class,
                () -> service.actualizarPerfilPropio(1L, "Nombre", "Apellido", null, "prefs", "Prohibido"));
    }

    @Test
    void actualizar_conApodoEnBlanco_noIntentaCambiarlo() {
        PerfilUsuario perfil = perfilConApodo("Santi");
        when(perfilUsuarioRepository.findByIdConUsuario(1L)).thenReturn(Optional.of(perfil));

        service.actualizarPerfilPropio(1L, "Nombre", "Apellido", null, "prefs", "   ");

        verify(apodoBlacklistValidator, never()).validar(any());
    }
}
