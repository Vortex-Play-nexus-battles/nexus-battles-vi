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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// B11: sesiones de visitante (ms-chatbot.yaml 1.2.0), las que emite el
// servidor y las que declara el navegador (el asistente de la interfaz, #708).
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

    // El ataque de la auditoria: un uid (o cualquier cosa sin una de las dos
    // formas) no es una sesion; ni siquiera se consulta la base.
    @Test
    void unUidOUnValorSinFormaNoSonSesiones() {
        assertThat(sesiones.validar(UUID.randomUUID().toString())).isEmpty();
        assertThat(sesiones.validar(null)).isEmpty();
        assertThat(sesiones.validar("anon_corto")).isEmpty();
        assertThat(sesiones.validar("visitante-1")).isEmpty();
        assertThat(sesiones.validar("visitante-" + "x".repeat(65))).isEmpty();
        assertThat(sesiones.validar("visitante-<script>alert(1)</script>")).isEmpty();
        verify(repositorio, never()).findByHuella(any());
    }

    // Las dos formas: la emitida (256 bits) y la que declara el asistente de la
    // interfaz (#708): 'visitante-' + crypto.randomUUID(), o en un contexto sin
    // HTTPS su respaldo de tiempo y aleatorio en base 36.
    @Test
    void reconoceLasDosFormasYNadaMas() {
        assertThat(SesionesAnonimas.esEmitida("anon_" + "Z".repeat(43))).isTrue();
        assertThat(SesionesAnonimas.esDeclarada("visitante-" + UUID.randomUUID())).isTrue();
        assertThat(SesionesAnonimas.esDeclarada("visitante-mg1k2x3a-4k2j9d8s")).isTrue();
        assertThat(SesionesAnonimas.esDeclarada("visitante-mg1k2x3a-4")).isTrue();

        assertThat(SesionesAnonimas.esDeclarada("anon_" + "Z".repeat(43))).isFalse();
        assertThat(SesionesAnonimas.esEmitida("visitante-" + UUID.randomUUID())).isFalse();
        assertThat(SesionesAnonimas.esDeclarada(UUID.randomUUID().toString())).isFalse();
        assertThat(SesionesAnonimas.esDeclarada("visitante-corto")).isFalse();
        assertThat(SesionesAnonimas.esDeclarada(null)).isFalse();
        assertThat(SesionesAnonimas.esEmitida(null)).isFalse();
    }

    // #708: la sesion que declara el navegador se valida por su huella, como
    // una emitida, y su conversacion vive en el espacio de visitante.
    @Test
    void unaSesionDeclaradaYRegistradaSeValidaPorSuHuella() {
        String declarada = "visitante-" + UUID.randomUUID();
        SesionAnonima registrada = new SesionAnonima(SesionesAnonimas.huella(declarada), AHORA.minusSeconds(60),
            Duration.ofHours(24));
        when(repositorio.findByHuella(SesionesAnonimas.huella(declarada))).thenReturn(Optional.of(registrada));

        assertThat(sesiones.validar(declarada)).contains(registrada);
        assertThat(registrada.claveDeConversacion()).startsWith("anonimo:").doesNotContain(declarada);
    }

    @Test
    void declararRegistraLaSesionDelNavegadorSinCarreraYCuentaEnElLimite() {
        String declarada = "visitante-" + UUID.randomUUID();
        String huella = SesionesAnonimas.huella(declarada);
        SesionAnonima registrada = new SesionAnonima(huella, AHORA, Duration.ofHours(24));
        when(repositorio.findByHuella(huella)).thenReturn(Optional.empty(), Optional.of(registrada));

        assertThat(sesiones.declarar(declarada, "203.0.113.7")).isSameAs(registrada);

        verify(limitador).exigir(LimitadorDeFrecuencia.Regla.SESIONES_POR_ORIGEN, "203.0.113.7");
        verify(limitador).exigir(LimitadorDeFrecuencia.Regla.SESIONES_GLOBAL, "*");
        verify(repositorio).insertarSiNoExiste(any(UUID.class), eq(huella), eq(AHORA),
            eq(AHORA.plus(Duration.ofHours(24))));
        verify(repositorio, never()).save(any());
    }

    // Una declarada que ya esta registrada y vigente no cuenta como sesion
    // nueva: el limite de sesiones es para las nuevas.
    @Test
    void declararUnaVigenteLaDevuelveSinContarNiInsertar() {
        String declarada = "visitante-" + UUID.randomUUID();
        String huella = SesionesAnonimas.huella(declarada);
        SesionAnonima vigente = new SesionAnonima(huella, AHORA.minusSeconds(60), Duration.ofHours(24));
        when(repositorio.findByHuella(huella)).thenReturn(Optional.of(vigente));

        assertThat(sesiones.declarar(declarada, "203.0.113.7")).isSameAs(vigente);

        verify(limitador, never()).exigir(any(), any());
        verify(repositorio, never()).insertarSiNoExiste(any(), any(), any(), any());
    }

    // Vencida: se aparta y abre otra conversacion, igual que una emitida que
    // vence (el navegador conserva su identificador, pero no su historial).
    @Test
    void declararUnaVencidaLaReiniciaConOtraConversacion() {
        String declarada = "visitante-" + UUID.randomUUID();
        String huella = SesionesAnonimas.huella(declarada);
        SesionAnonima vencida = new SesionAnonima(huella, AHORA.minus(Duration.ofHours(30)), Duration.ofHours(24));
        SesionAnonima nueva = new SesionAnonima(huella, AHORA, Duration.ofHours(24));
        when(repositorio.findByHuella(huella)).thenReturn(Optional.of(vencida), Optional.of(nueva));

        SesionAnonima resultado = sesiones.declarar(declarada, "203.0.113.7");

        assertThat(resultado).isSameAs(nueva);
        assertThat(resultado.claveDeConversacion()).isNotEqualTo(vencida.claveDeConversacion());
        verify(repositorio).borrarVencida(huella, AHORA);
        verify(repositorio).insertarSiNoExiste(any(UUID.class), eq(huella), eq(AHORA),
            eq(AHORA.plus(Duration.ofHours(24))));
    }

    @Test
    void declararPorEncimaDelLimiteNoRegistraNada() {
        String declarada = "visitante-" + UUID.randomUUID();
        when(repositorio.findByHuella(SesionesAnonimas.huella(declarada))).thenReturn(Optional.empty());
        doThrow(new LimiteDeFrecuenciaExcedido("Demasiadas", 10))
            .when(limitador).exigir(LimitadorDeFrecuencia.Regla.SESIONES_POR_ORIGEN, "203.0.113.7");

        assertThatThrownBy(() -> sesiones.declarar(declarada, "203.0.113.7"))
            .isInstanceOf(LimiteDeFrecuenciaExcedido.class);
        verify(repositorio, never()).insertarSiNoExiste(any(), any(), any(), any());
        verify(repositorio, never()).borrarVencida(any(), any());
    }

    // Solo se declara lo que tiene la forma: ni un uid ni una emitida.
    @Test
    void soloSeDeclaraLoQueTieneLaForma() {
        String uid = UUID.randomUUID().toString();
        String emitida = "anon_" + "Z".repeat(43);

        assertThatThrownBy(() -> sesiones.declarar(uid, "203.0.113.7")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sesiones.declarar(emitida, "203.0.113.7"))
            .isInstanceOf(IllegalArgumentException.class);
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
