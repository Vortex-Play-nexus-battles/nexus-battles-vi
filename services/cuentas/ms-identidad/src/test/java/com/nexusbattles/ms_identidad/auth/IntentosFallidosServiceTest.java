package com.nexusbattles.ms_identidad.auth;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.IntentosFallidosService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IntentosFallidosServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private IntentosFallidosService intentosFallidosService;

    private static final int UMBRAL = 5;
    private static final int MINUTOS_BLOQUEO = 15;

    @BeforeEach
    void setUp() {
        // @Value no se procesa fuera de un contexto real de Spring: se
        // inyectan a mano los mismos valores por defecto configurados en
        // application-dev.properties, para que la prueba refleje el
        // comportamiento real.
        ReflectionTestUtils.setField(intentosFallidosService, "umbralIntentosFallidos", UMBRAL);
        ReflectionTestUtils.setField(intentosFallidosService, "minutosBloqueo", MINUTOS_BLOQUEO);
    }

    private Usuario usuarioConIntentos(int intentosFallidos) {
        Usuario usuario = new Usuario();
        usuario.setIntentosFallidos(intentosFallidos);
        return usuario;
    }

    @Test
    void debeIncrementarIntentosSinBloquearAntesDelUmbral() {

        Usuario usuario = usuarioConIntentos(2);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        intentosFallidosService.registrarIntentoFallido(1L);

        assertEquals(3, usuario.getIntentosFallidos());
        assertNull(usuario.getBloqueadoHasta());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void debeBloquearCuentaAlAlcanzarElUmbral() {

        Usuario usuario = usuarioConIntentos(UMBRAL - 1);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        LocalDateTime antes = LocalDateTime.now();
        intentosFallidosService.registrarIntentoFallido(1L);
        LocalDateTime despues = LocalDateTime.now();

        assertEquals(UMBRAL, usuario.getIntentosFallidos());
        assertNotNull(usuario.getBloqueadoHasta());
        assertTrue(!usuario.getBloqueadoHasta().isBefore(antes.plusMinutes(MINUTOS_BLOQUEO)));
        assertTrue(!usuario.getBloqueadoHasta().isAfter(despues.plusMinutes(MINUTOS_BLOQUEO)));
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void debeLanzarExcepcionSiUsuarioNoExiste() {

        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> intentosFallidosService.registrarIntentoFallido(99L)
        );

        assertEquals("Usuario no encontrado: 99", exception.getMessage());
        verify(usuarioRepository, never()).save(any());
    }
}
