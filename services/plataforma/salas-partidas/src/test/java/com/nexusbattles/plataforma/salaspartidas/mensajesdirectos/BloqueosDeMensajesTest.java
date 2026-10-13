package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bloquear a un jugador en los mensajes privados — D-40, auditoria de DEV del
 * 30-sep: «Bloquear jugador» se ofrecia y el servicio no lo tenia.
 */
@DisplayName("BloqueosDeMensajes · bloquear y desbloquear a un jugador (D-40)")
class BloqueosDeMensajesTest {

    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRUNO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CARLA = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant AHORA = Instant.parse("2026-10-01T15:00:00Z");

    private final RepositorioDeBloqueosEnMemoria repositorio = new RepositorioDeBloqueosEnMemoria();
    private final BloqueosDeMensajes bloqueos =
            new BloqueosDeMensajes(repositorio, Clock.fixed(AHORA, ZoneOffset.UTC));

    @Test
    @DisplayName("sin bloqueos la conversacion esta ACTIVA para los dos")
    void sinBloqueos() {
        assertAll(
                () -> assertEquals(EstadoDeConversacion.ACTIVA, bloqueos.estado(ANA, BRUNO)),
                () -> assertEquals(EstadoDeConversacion.ACTIVA, bloqueos.estado(BRUNO, ANA)));
    }

    @Test
    @DisplayName("quien bloquea la ve BLOQUEADA; el bloqueado, NO_ADMITE, sin que se le diga por que")
    void unoBloqueaAlOtro() {
        EstadoDeConversacion devuelto = bloqueos.bloquear(ANA, BRUNO);

        assertAll(
                () -> assertEquals(EstadoDeConversacion.BLOQUEADA, devuelto),
                () -> assertEquals(EstadoDeConversacion.BLOQUEADA, bloqueos.estado(ANA, BRUNO)),
                () -> assertEquals(EstadoDeConversacion.NO_ADMITE, bloqueos.estado(BRUNO, ANA)),
                () -> assertEquals(AHORA, repositorio.bloqueos.get(List.of(ANA, BRUNO)), "con la hora del reloj"),
                () -> assertEquals(EstadoDeConversacion.ACTIVA, bloqueos.estado(ANA, CARLA),
                        "bloquear a uno no toca las demas conversaciones"));
    }

    @Test
    @DisplayName("bloquear dos veces es lo mismo que una; desbloquear lo deshace del todo")
    void idempotenteYReversible() {
        bloqueos.bloquear(ANA, BRUNO);
        bloqueos.bloquear(ANA, BRUNO);
        assertEquals(1, repositorio.bloqueos.size());

        EstadoDeConversacion trasDesbloquear = bloqueos.desbloquear(ANA, BRUNO);
        EstadoDeConversacion otraVez = bloqueos.desbloquear(ANA, BRUNO);

        assertAll(
                () -> assertEquals(EstadoDeConversacion.ACTIVA, trasDesbloquear),
                () -> assertEquals(EstadoDeConversacion.ACTIVA, otraVez, "desbloquear sin bloqueo no falla"),
                () -> assertEquals(EstadoDeConversacion.ACTIVA, bloqueos.estado(BRUNO, ANA)),
                () -> assertTrue(repositorio.bloqueos.isEmpty()));
    }

    @Test
    @DisplayName("si se bloquearon los dos, quien desbloquea sigue sin poder escribir: NO_ADMITE")
    void bloqueoMutuo() {
        bloqueos.bloquear(ANA, BRUNO);
        bloqueos.bloquear(BRUNO, ANA);

        assertAll(
                () -> assertEquals(EstadoDeConversacion.BLOQUEADA, bloqueos.estado(ANA, BRUNO),
                        "lo que uno hizo pesa mas: puede deshacerlo"),
                () -> assertEquals(EstadoDeConversacion.NO_ADMITE, bloqueos.desbloquear(ANA, BRUNO)),
                () -> assertEquals(EstadoDeConversacion.BLOQUEADA, bloqueos.estado(BRUNO, ANA)));
    }

    @Test
    @DisplayName("nadie se bloquea a si mismo: DESTINATARIO_PROPIO, y no se guarda nada")
    void aSiMismo() {
        MensajeDirectoRechazado bloquear = assertThrows(MensajeDirectoRechazado.class,
                () -> bloqueos.bloquear(ANA, ANA));
        MensajeDirectoRechazado desbloquear = assertThrows(MensajeDirectoRechazado.class,
                () -> bloqueos.desbloquear(ANA, ANA));

        assertAll(
                () -> assertEquals(MotivoDeRechazo.DESTINATARIO_PROPIO, bloquear.motivo()),
                () -> assertEquals(MotivoDeRechazo.DESTINATARIO_PROPIO, desbloquear.motivo()),
                () -> assertTrue(repositorio.bloqueos.isEmpty()));
    }

    @Test
    @DisplayName("sin quien o sin a quien no hay bloqueo posible")
    void faltanDatos() {
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> bloqueos.bloquear(null, BRUNO)),
                () -> assertThrows(NullPointerException.class, () -> bloqueos.bloquear(ANA, null)),
                () -> assertThrows(NullPointerException.class, () -> bloqueos.estado(ANA, null)),
                () -> assertThrows(NullPointerException.class, () -> bloqueos.vistaDe(null)));
    }

    @Test
    @DisplayName("la vista de un jugador da el estado de todas sus conversaciones en dos consultas")
    void vistaDeUnJugador() {
        bloqueos.bloquear(ANA, BRUNO);
        bloqueos.bloquear(CARLA, ANA);
        repositorio.consultas = 0;

        BloqueosDeMensajes.Vista vista = bloqueos.vistaDe(ANA);
        UUID otro = UUID.randomUUID();

        assertAll(
                () -> assertEquals(2, repositorio.consultas, "no una consulta por conversacion"),
                () -> assertEquals(Set.of(BRUNO), vista.bloqueados()),
                () -> assertEquals(Set.of(CARLA), vista.leBloquearon()),
                () -> assertEquals(EstadoDeConversacion.BLOQUEADA, vista.con(BRUNO)),
                () -> assertEquals(EstadoDeConversacion.NO_ADMITE, vista.con(CARLA)),
                () -> assertEquals(EstadoDeConversacion.ACTIVA, vista.con(otro)));
    }

    @Test
    @DisplayName("sin almacen nadie bloquea a nadie, y bloquear no finge que funciono")
    void sinAlmacen() {
        BloqueosDeMensajes ninguno = BloqueosDeMensajes.sinBloqueos();

        assertAll(
                () -> assertEquals(EstadoDeConversacion.ACTIVA, ninguno.estado(ANA, BRUNO)),
                () -> assertEquals(EstadoDeConversacion.ACTIVA, ninguno.vistaDe(ANA).con(BRUNO)),
                () -> assertEquals(EstadoDeConversacion.ACTIVA, ninguno.desbloquear(ANA, BRUNO)),
                () -> assertThrows(UnsupportedOperationException.class, () -> ninguno.bloquear(ANA, BRUNO)));
    }
}
