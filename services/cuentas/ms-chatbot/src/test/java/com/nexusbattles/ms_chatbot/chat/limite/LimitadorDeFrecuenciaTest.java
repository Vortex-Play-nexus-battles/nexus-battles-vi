package com.nexusbattles.ms_chatbot.chat.limite;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// B11 — 7.4.8: limite de tasa de consultas.
class LimitadorDeFrecuenciaTest {

    private final AtomicReference<Instant> ahora = new AtomicReference<>(Instant.parse("2026-10-01T10:00:00Z"));
    private final Clock reloj = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora.get();
        }
    };

    @Test
    void dejaPasarHastaElLimiteYElSiguienteRecibeCuantoEsperar() {
        LimitadorDeFrecuencia limitador = new LimitadorDeFrecuencia(3, 2, 100, reloj);
        for (int i = 0; i < 3; i++) {
            limitador.exigir(LimitadorDeFrecuencia.Regla.MENSAJES, "usuario:a");
        }
        ahora.set(ahora.get().plusSeconds(20));
        assertThatThrownBy(() -> limitador.exigir(LimitadorDeFrecuencia.Regla.MENSAJES, "usuario:a"))
            .isInstanceOfSatisfying(LimiteDeFrecuenciaExcedido.class,
                ex -> assertThat(ex.segundosParaReintentar()).isEqualTo(40));
    }

    @Test
    void cadaClaveYCadaReglaLlevaSuPropiaCuenta() {
        LimitadorDeFrecuencia limitador = new LimitadorDeFrecuencia(1, 1, 100, reloj);
        limitador.exigir(LimitadorDeFrecuencia.Regla.MENSAJES, "usuario:a");
        assertThatCode(() -> limitador.exigir(LimitadorDeFrecuencia.Regla.MENSAJES, "usuario:b")).doesNotThrowAnyException();
        assertThatCode(() -> limitador.exigir(LimitadorDeFrecuencia.Regla.SESIONES_POR_ORIGEN, "usuario:a"))
            .doesNotThrowAnyException();
        assertThatThrownBy(() -> limitador.exigir(LimitadorDeFrecuencia.Regla.MENSAJES, "usuario:a"))
            .isInstanceOf(LimiteDeFrecuenciaExcedido.class);
    }

    @Test
    void laVentanaSeReiniciaAlMinutoYElBarridoQuitaLasVencidas() {
        LimitadorDeFrecuencia limitador = new LimitadorDeFrecuencia(1, 1, 100, reloj);
        limitador.exigir(LimitadorDeFrecuencia.Regla.MENSAJES, "usuario:a");
        limitador.exigir(LimitadorDeFrecuencia.Regla.MENSAJES, "usuario:b");
        assertThat(limitador.clavesVivas()).isEqualTo(2);

        ahora.set(ahora.get().plusSeconds(61));
        assertThatCode(() -> limitador.exigir(LimitadorDeFrecuencia.Regla.MENSAJES, "usuario:a")).doesNotThrowAnyException();
        limitador.barrer();
        assertThat(limitador.clavesVivas()).as("solo queda la ventana recien abierta").isEqualTo(1);
    }

    @Test
    void elMinimoParaReintentarEsUnSegundo() {
        assertThat(new LimiteDeFrecuenciaExcedido("x", 0).segundosParaReintentar()).isEqualTo(1);
    }
}
