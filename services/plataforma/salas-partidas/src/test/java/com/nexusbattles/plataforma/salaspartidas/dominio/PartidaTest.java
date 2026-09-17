package com.nexusbattles.plataforma.salaspartidas.dominio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El combate — RF-JUE-017, HU-SAL-004.
 *
 * <p>Lo que se prueba es el ciclo de vida y el orden de los turnos, que es lo
 * unico que este servicio decide. Nada de dano ni de efectos: eso es del motor
 * de combate y no tiene pruebas aqui porque no tiene codigo aqui.
 */
@DisplayName("Partida · ciclo de vida del combate (RF-JUE-017)")
class PartidaTest {

    private static final UUID ANFITRION = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SEGUNDO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TERCERO = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant AHORA = Instant.parse("2026-09-17T20:00:00Z");

    private static Sala salaCon(boolean conIA, int recompensa, UUID... invitados) {
        Sala sala = Sala.crear(
                new ParametrosDeSala(6, Modalidad.HASTA_SEIS, recompensa, conIA, false, null),
                ANFITRION);
        for (UUID invitado : invitados) {
            sala.unirse(invitado);
        }
        return sala;
    }

    @Nested
    @DisplayName("al iniciar")
    class AlIniciar {

        @Test
        @DisplayName("el combate arranca en curso, con el turno del anfitrion")
        void arrancaConElAnfitrion() {
            Partida partida = Partida.iniciar(salaCon(false, 0, SEGUNDO), AHORA);

            assertAll(
                    () -> assertEquals(EstadoPartida.EN_CURSO, partida.estado()),
                    () -> assertEquals(ANFITRION, partida.turnoActual().idJugador()),
                    () -> assertEquals(1, partida.turnoActual().numeroTurno()),
                    () -> assertEquals(AHORA, partida.iniciadaEn()));
        }

        @Test
        @DisplayName("los participantes van en el orden de los turnos: el anfitrion primero")
        void elAnfitrionVaPrimero() {
            Partida partida = Partida.iniciar(salaCon(false, 0, SEGUNDO, TERCERO), AHORA);

            List<UUID> orden = partida.participantes().stream()
                    .map(ParticipanteDePartida::idJugador)
                    .toList();

            assertEquals(List.of(ANFITRION, SEGUNDO, TERCERO), orden);
        }

        @Test
        @DisplayName("la sala con heroe de la IA anade un participante controlado por ella")
        void anadeElHeroeDeLaIA() {
            Partida partida = Partida.iniciar(salaCon(true, 0), AHORA);

            assertAll(
                    () -> assertEquals(2, partida.participantes().size()),
                    () -> assertFalse(partida.participantes().get(0).esIA()),
                    () -> assertTrue(partida.participantes().get(1).esIA()));
        }

        @Test
        @DisplayName("la recompensa de la sala queda en juego y apostada por cada humano")
        void laRecompensaQuedaEnJuego() {
            Partida partida = Partida.iniciar(salaCon(false, 320, SEGUNDO), AHORA);

            assertAll(
                    () -> assertEquals(320, partida.recompensaEnJuego()),
                    () -> assertEquals(320, partida.participantes().get(0).creditosApostados()),
                    () -> assertEquals(320, partida.participantes().get(1).creditosApostados()));
        }

        @Test
        @DisplayName("nadie entra al combate con heroe: el inventario todavia no se consulta al ingresar")
        void sinHeroeConocido() {
            Partida partida = Partida.iniciar(salaCon(false, 0, SEGUNDO), AHORA);

            assertTrue(partida.participantes().stream().allMatch(p -> p.heroe() == null));
        }
    }

    @Nested
    @DisplayName("al avanzar el turno")
    class AlAvanzarTurno {

        @Test
        @DisplayName("pasa al siguiente participante y sube el numero")
        void pasaAlSiguiente() {
            Partida partida = Partida.iniciar(salaCon(false, 0, SEGUNDO), AHORA);

            partida.avanzarTurno();

            assertAll(
                    () -> assertEquals(SEGUNDO, partida.turnoActual().idJugador()),
                    () -> assertEquals(2, partida.turnoActual().numeroTurno()));
        }

        @Test
        @DisplayName("tras el ultimo vuelve al primero, pero el numero sigue subiendo")
        void rotaCircularmente() {
            Partida partida = Partida.iniciar(salaCon(false, 0, SEGUNDO), AHORA);

            partida.avanzarTurno();
            partida.avanzarTurno();

            assertAll(
                    () -> assertEquals(ANFITRION, partida.turnoActual().idJugador()),
                    () -> assertEquals(3, partida.turnoActual().numeroTurno()));
        }

        @Test
        @DisplayName("una partida terminada no avanza mas")
        void terminadaNoAvanza() {
            Partida partida = Partida.iniciar(salaCon(false, 0, SEGUNDO), AHORA);
            partida.terminar();

            assertThrows(PartidaYaTerminada.class, partida::avanzarTurno);
        }
    }

    @Test
    @DisplayName("terminar dos veces no es un error: el motor puede reintentar el aviso")
    void terminarEsIdempotente() {
        Partida partida = Partida.iniciar(salaCon(false, 0, SEGUNDO), AHORA);

        partida.terminar();
        partida.terminar();

        assertEquals(EstadoPartida.FINALIZADA, partida.estado());
    }

    @Test
    @DisplayName("los participantes no se pueden alterar desde fuera")
    void participantesInmutables() {
        Partida partida = Partida.iniciar(salaCon(false, 0, SEGUNDO), AHORA);

        assertThrows(UnsupportedOperationException.class,
                () -> partida.participantes().clear());
    }
}
