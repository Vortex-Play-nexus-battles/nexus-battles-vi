package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LimiteDeFrecuenciaEnMemoria · 5 cada 10 s por remitente (B6, provisional)")
class LimiteDeFrecuenciaEnMemoriaTest {

    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRUNO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    /** Reloj que se mueve a mano. */
    private static final class Reloj extends Clock {
        Instant ahora = Instant.parse("2026-09-25T18:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    private final Reloj reloj = new Reloj();
    private final LimiteDeFrecuenciaEnMemoria limite =
            new LimiteDeFrecuenciaEnMemoria(5, Duration.ofSeconds(10), reloj);

    @Test
    @DisplayName("caben cinco; el sexto espera a que salga el primero de la ventana")
    void cincoYElSextoEspera() {
        for (int i = 0; i < 5; i++) {
            assertTrue(limite.registrar(ANA).isEmpty(), "el envio " + (i + 1) + " cabe");
            reloj.ahora = reloj.ahora.plusSeconds(1);
        }

        Optional<Duration> espera = limite.registrar(ANA);

        assertEquals(Optional.of(Duration.ofSeconds(5)), espera);
    }

    @Test
    @DisplayName("pasada la ventana vuelve a caber, y un intento rechazado no alarga la espera")
    void laVentanaSeDesliza() {
        for (int i = 0; i < 5; i++) {
            limite.registrar(ANA);
        }
        assertTrue(limite.registrar(ANA).isPresent());
        assertTrue(limite.registrar(ANA).isPresent());

        reloj.ahora = reloj.ahora.plusSeconds(10);

        assertTrue(limite.registrar(ANA).isEmpty());
    }

    @Test
    @DisplayName("el limite es de cada remitente: lo de uno no frena al otro")
    void porRemitente() {
        for (int i = 0; i < 5; i++) {
            limite.registrar(ANA);
        }
        assertAll(
                () -> assertTrue(limite.registrar(ANA).isPresent()),
                () -> assertTrue(limite.registrar(BRUNO).isEmpty()));
    }

    @Test
    @DisplayName("quien lleva un rato callado se olvida solo")
    void barrido() {
        for (int i = 0; i < 1_500; i++) {
            limite.registrar(UUID.randomUUID());
        }
        reloj.ahora = reloj.ahora.plusSeconds(60);
        for (int i = 0; i < 1_000; i++) {
            limite.registrar(ANA);
            reloj.ahora = reloj.ahora.plusSeconds(3);
        }

        assertTrue(limite.remitentesEnMemoria() < 10, "quedan " + limite.remitentesEnMemoria());
    }

    @Test
    @DisplayName("un limite que no deja pasar nada, o una ventana sin duracion, no se pueden configurar")
    void configuracionInvalida() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new LimiteDeFrecuenciaEnMemoria(0, Duration.ofSeconds(10), reloj)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new LimiteDeFrecuenciaEnMemoria(5, Duration.ZERO, reloj)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new LimiteDeFrecuenciaEnMemoria(5, Duration.ofSeconds(-1), reloj)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new LimiteDeFrecuenciaEnMemoria(5, null, reloj)),
                () -> assertThrows(NullPointerException.class, () -> limite.registrar(null)));
    }

    @Test
    @DisplayName("el Retry-After redondea hacia arriba y nunca dice cero")
    void retryAfter() {
        assertAll(
                () -> assertEquals(Optional.of(1L), new MensajeDirectoRechazado(MotivoDeRechazo.DEMASIADO_RAPIDO,
                        null, Duration.ofMillis(1)).reintentarEnSegundos()),
                () -> assertEquals(Optional.of(1L), new MensajeDirectoRechazado(MotivoDeRechazo.DEMASIADO_RAPIDO,
                        null, Duration.ZERO).reintentarEnSegundos()),
                () -> assertEquals(Optional.of(2L), new MensajeDirectoRechazado(MotivoDeRechazo.DEMASIADO_RAPIDO,
                        null, Duration.ofMillis(1_001)).reintentarEnSegundos()),
                () -> assertTrue(new MensajeDirectoRechazado(MotivoDeRechazo.SANCIONADO, null)
                        .reintentarEnSegundos().isEmpty()));
    }
}
