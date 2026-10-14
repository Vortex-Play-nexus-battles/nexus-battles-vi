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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
    void elFalloQueAlcanzaElUmbralDiceQueAcabaDeBloquearUnaSolaVez() {
        Usuario usuario = usuarioConIntentos(UMBRAL - 1);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        assertTrue(intentosFallidosService.registrarIntentoFallido(1L), "el que bloquea lo dice");
        // Otro fallo con la cuenta aun bloqueada no "vuelve a bloquear".
        assertFalse(intentosFallidosService.registrarIntentoFallido(1L));
    }

    @Test
    void unBloqueoVencidoEmpiezaLaCuentaDeCeroEnVezDeBloquearConUnSoloFallo() {
        // 7.3.9 — antes, tras vencer el bloqueo, el contador seguia en el
        // umbral y un unico fallo bloqueaba la cuenta otros quince minutos.
        Clock reloj = Clock.fixed(Instant.parse("2026-10-13T20:00:00Z"), ZoneId.of("UTC"));
        intentosFallidosService.configurar(usuarioRepository, UMBRAL, MINUTOS_BLOQUEO, reloj);
        Usuario usuario = usuarioConIntentos(UMBRAL);
        usuario.setBloqueadoHasta(LocalDateTime.now(reloj).minusMinutes(1));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        boolean bloqueo = intentosFallidosService.registrarIntentoFallido(1L);

        assertFalse(bloqueo);
        assertEquals(1, usuario.getIntentosFallidos());
        assertNull(usuario.getBloqueadoHasta());
    }

    @Test
    void conElBloqueoVigenteElFalloSigueContandoSinAlargarlo() {
        Clock reloj = Clock.fixed(Instant.parse("2026-10-13T20:00:00Z"), ZoneId.of("UTC"));
        intentosFallidosService.configurar(usuarioRepository, UMBRAL, MINUTOS_BLOQUEO, reloj);
        Usuario usuario = usuarioConIntentos(UMBRAL);
        LocalDateTime fin = LocalDateTime.now(reloj).plusMinutes(10);
        usuario.setBloqueadoHasta(fin);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        intentosFallidosService.registrarIntentoFallido(1L);

        assertEquals(fin, usuario.getBloqueadoHasta(), "el fin del bloqueo no se mueve");
        assertEquals(UMBRAL + 1, usuario.getIntentosFallidos());
    }

    @Test
    void alBloquearQuedaLaMarcaDelBloqueoParaLaAlertaDeLaConsola() {
        // SA-20 (§7.3.4, D-45 señal 1): la marca no se borra al volver a
        // entrar; es lo que deja ver los bloqueos de un periodo.
        Clock reloj = Clock.fixed(Instant.parse("2026-10-13T20:00:00Z"), ZoneId.of("UTC"));
        intentosFallidosService.configurar(usuarioRepository, UMBRAL, MINUTOS_BLOQUEO, reloj);
        Usuario usuario = usuarioConIntentos(UMBRAL - 1);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        intentosFallidosService.registrarIntentoFallido(1L);

        assertEquals(LocalDateTime.now(reloj), usuario.getUltimoBloqueoEn());
    }

    @Test
    void sinBloquearNoHayMarcaYUnFalloConElBloqueoVigenteNoLaMueve() {
        Clock reloj = Clock.fixed(Instant.parse("2026-10-13T20:00:00Z"), ZoneId.of("UTC"));
        intentosFallidosService.configurar(usuarioRepository, UMBRAL, MINUTOS_BLOQUEO, reloj);
        Usuario sinBloquear = usuarioConIntentos(1);
        Usuario bloqueada = usuarioConIntentos(UMBRAL);
        LocalDateTime inicio = LocalDateTime.now(reloj).minusMinutes(5);
        bloqueada.setUltimoBloqueoEn(inicio);
        bloqueada.setBloqueadoHasta(inicio.plusMinutes(MINUTOS_BLOQUEO));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(sinBloquear));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(bloqueada));

        intentosFallidosService.registrarIntentoFallido(1L);
        intentosFallidosService.registrarIntentoFallido(2L);

        assertNull(sinBloquear.getUltimoBloqueoEn());
        assertEquals(inicio, bloqueada.getUltimoBloqueoEn(), "el bloqueo vigente no vuelve a empezar");
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
