package com.nexusbattles.ms_chatbot.chat.identidad;

import com.nexusbattles.ms_chatbot.chat.limite.LimitadorDeFrecuencia;
import com.nexusbattles.ms_chatbot.chat.limite.LimiteDeFrecuenciaExcedido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// B11: sesiones de visitante emitidas por el servidor (ms-chatbot.yaml 2.0.0).
@ExtendWith(MockitoExtension.class)
class SesionesAnonimasTest {

    private static final Instant AHORA = Instant.parse("2026-10-01T10:00:00Z");

    @Mock
    private SesionAnonimaRepository repositorio;
    @Mock
    private LimitadorDeFrecuencia limitador;

    private SesionesAnonimas sesiones;

    @BeforeEach
    void preparar() {
        sesiones = new SesionesAnonimas(repositorio, limitador, Clock.fixed(AHORA, ZoneOffset.UTC), 24);
    }

    @Test
    void emite256BitsAleatoriosYGuardaSoloLaHuella() {
        when(repositorio.save(any(SesionAnonima.class))).thenAnswer(inv -> inv.getArgument(0));

        SesionesAnonimas.Emitida una = sesiones.emitir("203.0.113.7");
        SesionesAnonimas.Emitida otra = sesiones.emitir("203.0.113.7");

        assertThat(una.identificador()).matches("^anon_[A-Za-z0-9_-]{43}$").isNotEqualTo(otra.identificador());
        assertThat(una.sesion().getHuella()).isEqualTo(SesionesAnonimas.huella(una.identificador()))
            .hasSize(64).doesNotContain(una.identificador());
        assertThat(una.sesion().getExpiraEn()).isEqualTo(AHORA.plus(Duration.ofHours(24)));
        assertThat(una.sesion().claveDeConversacion()).isEqualTo("anonimo:" + una.sesion().getId());
        verify(limitador, org.mockito.Mockito.times(2))
            .exigir(LimitadorDeFrecuencia.Regla.SESIONES_POR_ORIGEN, "203.0.113.7");
    }

    @Test
    void porEncimaDelLimiteNoEmiteNada() {
        doThrow(new LimiteDeFrecuenciaExcedido("Demasiadas", 10))
            .when(limitador).exigir(LimitadorDeFrecuencia.Regla.SESIONES_POR_ORIGEN, "203.0.113.7");

        assertThatThrownBy(() -> sesiones.emitir("203.0.113.7")).isInstanceOf(LimiteDeFrecuenciaExcedido.class);
        verify(repositorio, never()).save(any());
    }

    // El ataque de la auditoria: un uid (o cualquier cosa que el servidor no
    // emitio) no es una sesion; ni siquiera se consulta la base.
    @Test
    void unUidOUnValorInventadoNoSonSesiones() {
        assertThat(sesiones.validar(UUID.randomUUID().toString())).isEmpty();
        assertThat(sesiones.validar("visitante-" + UUID.randomUUID())).isEmpty();
        assertThat(sesiones.validar(null)).isEmpty();
        assertThat(sesiones.validar("anon_corto")).isEmpty();
        verify(repositorio, never()).findByHuella(any());
    }

    @Test
    void validaPorHuellaYRechazaLasVencidas() {
        String identificador = "anon_" + "Z".repeat(43);
        SesionAnonima vigente = new SesionAnonima(SesionesAnonimas.huella(identificador), AHORA.minusSeconds(60),
            Duration.ofHours(24));
        when(repositorio.findByHuella(SesionesAnonimas.huella(identificador))).thenReturn(Optional.of(vigente));
        assertThat(sesiones.validar(identificador)).contains(vigente);

        SesionAnonima vencida = new SesionAnonima("h", AHORA.minus(Duration.ofHours(30)), Duration.ofHours(24));
        when(repositorio.findByHuella(SesionesAnonimas.huella(identificador))).thenReturn(Optional.of(vencida));
        assertThat(sesiones.validar(identificador)).isEmpty();

        when(repositorio.findByHuella(SesionesAnonimas.huella(identificador))).thenReturn(Optional.empty());
        assertThat(sesiones.validar(identificador)).isEmpty();
    }

    @Test
    void renovarAlargaLaSesion() {
        SesionAnonima sesion = new SesionAnonima("h", AHORA.minus(Duration.ofHours(10)), Duration.ofHours(24));
        when(repositorio.findById(sesion.getId())).thenReturn(Optional.of(sesion));

        sesiones.renovar(sesion);

        assertThat(sesion.getExpiraEn()).isEqualTo(AHORA.plus(Duration.ofHours(24)));
        assertThat(sesion.getUltimaActividad()).isEqualTo(AHORA);
        verify(repositorio).save(sesion);
    }
}
