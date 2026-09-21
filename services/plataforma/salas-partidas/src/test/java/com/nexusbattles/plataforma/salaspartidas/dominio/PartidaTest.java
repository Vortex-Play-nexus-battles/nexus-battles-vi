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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        @DisplayName("sin ficha en la sala el heroe queda nulo, no inventado")
        void sinHeroeConocido() {
            // Sala anterior a la migracion V7, o participante que entro antes de
            // que la puerta guardara su heroe. Null dice la verdad.
            Partida partida = Partida.iniciar(salaCon(false, 0, SEGUNDO), AHORA);

            assertTrue(partida.participantes().stream().allMatch(p -> p.heroe() == null));
        }

        @Test
        @DisplayName("cada participante entra al combate con SU heroe y SU vida (P2.4)")
        void cadaUnoConSuHeroe() {
            HeroeDeCombate deAna = new HeroeDeCombate("h-ana", "Arquero del Norte", null, 5, 120, 120);
            HeroeDeCombate deBruno = new HeroeDeCombate("h-bruno", "Centinela", null, 3, 90, 90);
            Sala sala = Sala.crear(
                    new ParametrosDeSala(6, Modalidad.HASTA_SEIS, 0, false, false, null),
                    ANFITRION, new FichaDeParticipante("Ana", deAna));
            sala.unirse(SEGUNDO, new FichaDeParticipante("Bruno", deBruno), null);

            Partida partida = Partida.iniciar(sala, AHORA);

            assertAll(
                    () -> assertEquals(deAna, partida.participantes().get(0).heroe()),
                    () -> assertEquals(deBruno, partida.participantes().get(1).heroe()),
                    // Vidas distintas: si salieran iguales, alguien esta copiando
                    // la ficha del primero en vez de leer la de cada uno.
                    () -> assertEquals(120, partida.participantes().get(0).heroe().vidaMaxima()),
                    () -> assertEquals(90, partida.participantes().get(1).heroe().vidaMaxima()));
        }

        @Test
        @DisplayName("la IA combate con el heroe del anfitrion, a plena vida (HU-SAL-004)")
        void laIaTraeElHeroeDelAnfitrion() {
            Sala conIa = Sala.crear(
                    new ParametrosDeSala(2, Modalidad.CONTRA_IA, 0, true, false, null),
                    ANFITRION,
                    new FichaDeParticipante("Ana",
                            new HeroeDeCombate("h-ana", "Arquero del Norte", null, 5, 120, 120)));

            Partida partida = Partida.iniciar(conIa, AHORA);

            // No se le inventa un heroe: se le da el unico que la partida
            // conoce, y de paso la pelea queda pareja.
            assertAll(
                    () -> assertNotNull(partida.participantes().get(0).heroe()),
                    () -> assertNotNull(partida.participantes().get(1).heroe()),
                    () -> assertEquals("Arquero del Norte",
                            partida.participantes().get(1).heroe().nombre()),
                    () -> assertEquals(120, partida.participantes().get(1).heroe().vidaActual()));
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

    /**
     * RF-JUE-004 — HU-SAL-004: «hasta seis participantes en las que cualquiera
     * pueda ser controlado por la IA, con equipos de maximo tres integrantes en
     * el modo cooperativo».
     */
    @Nested
    @DisplayName("hasta seis: varias maquinas y equipos (HU-SAL-004)")
    class HastaSeis {

        private static final UUID CUARTO = UUID.fromString("44444444-4444-4444-4444-444444444444");

        private HeroeDeCombate heroe(String id, int vida) {
            return new HeroeDeCombate(id, "Heroe " + id, null, 3, vida, vida);
        }

        private Sala sala(int maximo, int heroesIA, Integer tamanoEquipo, UUID... invitados) {
            Sala sala = Sala.crear(
                    new ParametrosDeSala(maximo, Modalidad.HASTA_SEIS, 0, heroesIA, false, tamanoEquipo),
                    ANFITRION, new FichaDeParticipante("Ana", heroe("h-ana", 100)));
            for (UUID invitado : invitados) {
                sala.unirse(invitado, new FichaDeParticipante("J-" + invitado, heroe("h-" + invitado, 100)), null);
            }
            return sala;
        }

        @Test
        @DisplayName("cada cupo de la IA es un participante mas, y todos van detras de los humanos")
        void unaMaquinaPorCupo() {
            Partida partida = Partida.iniciar(sala(6, 3, null, SEGUNDO), AHORA);

            List<Boolean> maquinas = partida.participantes().stream()
                    .map(ParticipanteDePartida::esIA).toList();
            assertAll(
                    () -> assertEquals(5, partida.participantes().size(), "2 humanos + 3 maquinas"),
                    () -> assertEquals(List.of(false, false, true, true, true), maquinas),
                    () -> assertEquals(3, partida.participantes().stream()
                            .map(ParticipanteDePartida::idJugador).distinct().count() - 2,
                            "cada maquina con identificador propio"));
        }

        @Test
        @DisplayName("sin equipos declarados nadie tiene equipo: todos contra todos")
        void sinEquiposEsTodosContraTodos() {
            Partida partida = Partida.iniciar(sala(4, 1, null, SEGUNDO, TERCERO), AHORA);

            assertTrue(partida.participantes().stream().allMatch(p -> p.equipo() == null));
        }

        @Test
        @DisplayName("con equipos de dos, se llenan en orden de entrada: anfitrion y segundo, tercero y cuarto, maquinas al final")
        void equiposEnOrdenDeEntrada() {
            Partida partida = Partida.iniciar(sala(6, 2, 2, SEGUNDO, TERCERO, CUARTO), AHORA);

            List<Integer> equipos = partida.participantes().stream()
                    .map(ParticipanteDePartida::equipo).toList();
            assertEquals(List.of(1, 1, 2, 2, 3, 3), equipos);
        }

        @Test
        @DisplayName("un equipo nunca pasa de su tamano: con tres de a tres son dos equipos")
        void nuncaMasDelTamano() {
            Partida partida = Partida.iniciar(sala(6, 2, 3, SEGUNDO, TERCERO, CUARTO), AHORA);

            List<Integer> equipos = partida.participantes().stream()
                    .map(ParticipanteDePartida::equipo).toList();
            assertEquals(List.of(1, 1, 1, 2, 2, 2), equipos);
        }

        @Test
        @DisplayName("el combate no termina mientras queden dos equipos en pie, aunque solo quede uno por equipo")
        void sigueMientrasHayaDosEquipos() {
            Partida partida = Partida.iniciar(sala(4, 0, 2, SEGUNDO, TERCERO, CUARTO), AHORA);
            // Caen el segundo (equipo 1) y el cuarto (equipo 2): queda uno por equipo.
            partida.aplicarDano(SEGUNDO, 100);
            partida.aplicarDano(CUARTO, 100);

            assertAll(
                    () -> assertFalse(partida.terminarSiSoloQuedaUno()),
                    () -> assertEquals(EstadoPartida.EN_CURSO, partida.estado()));
        }

        @Test
        @DisplayName("termina cuando solo queda un equipo en pie, y ganan todos sus integrantes")
        void terminaAlQuedarUnEquipo() {
            Partida partida = Partida.iniciar(sala(4, 0, 2, SEGUNDO, TERCERO, CUARTO), AHORA);
            partida.aplicarDano(TERCERO, 100);
            partida.aplicarDano(CUARTO, 100);

            boolean termino = partida.terminarSiSoloQuedaUno();

            assertAll(
                    () -> assertTrue(termino),
                    () -> assertEquals(EstadoPartida.FINALIZADA, partida.estado()),
                    () -> assertEquals(java.util.Optional.of(1), partida.equipoGanador()),
                    () -> assertEquals(List.of(ANFITRION, SEGUNDO), partida.ganadores().stream()
                            .map(ParticipanteDePartida::idJugador).toList()),
                    // Con dos en pie no hay UN ganador: eso es del reparto por
                    // equipos, que el PO no ha definido.
                    () -> assertTrue(partida.ganador().isEmpty()));
        }

        @Test
        @DisplayName("por equipos nunca hay UN ganador, ni con un solo superviviente: la apuesta se devuelve (D-12)")
        void porEquiposNoHayGanadorUnico() {
            Partida partida = Partida.iniciar(sala(4, 0, 2, SEGUNDO, TERCERO, CUARTO), AHORA);
            // Cae todo el equipo 2 y tambien el companero del anfitrion.
            partida.aplicarDano(TERCERO, 100);
            partida.aplicarDano(CUARTO, 100);
            partida.aplicarDano(SEGUNDO, 100);
            partida.terminarSiSoloQuedaUno();

            assertAll(
                    () -> assertTrue(partida.ganador().isEmpty(),
                            "si fuera ganador, se llevaria lo apostado por su propio companero"),
                    () -> assertEquals(java.util.Optional.of(1), partida.equipoGanador()),
                    () -> assertEquals(List.of(ANFITRION), partida.ganadores().stream()
                            .map(ParticipanteDePartida::idJugador).toList()));
        }

        @Test
        @DisplayName("sin equipos, ganadores es el unico en pie, igual que ganador")
        void sinEquiposGanadorUnico() {
            Partida partida = Partida.iniciar(sala(3, 0, null, SEGUNDO, TERCERO), AHORA);
            partida.aplicarDano(SEGUNDO, 100);
            partida.aplicarDano(TERCERO, 100);
            partida.terminarSiSoloQuedaUno();

            assertAll(
                    () -> assertEquals(ANFITRION, partida.ganador().orElseThrow().idJugador()),
                    () -> assertEquals(1, partida.ganadores().size()),
                    () -> assertTrue(partida.equipoGanador().isEmpty()));
        }

        @Test
        @DisplayName("el turno salta a quien ya cayo: un heroe derrotado no juega")
        void elTurnoSaltaALosCaidos() {
            Partida partida = Partida.iniciar(sala(4, 0, null, SEGUNDO, TERCERO, CUARTO), AHORA);
            partida.aplicarDano(SEGUNDO, 100);
            partida.aplicarDano(TERCERO, 100);

            partida.avanzarTurno();

            assertAll(
                    () -> assertEquals(CUARTO, partida.turnoActual().idJugador()),
                    () -> assertEquals(2, partida.turnoActual().numeroTurno(),
                            "los saltados no gastan numero de turno"));
        }

        @Test
        @DisplayName("quienes comparten equipo se reconocen como companeros")
        void companeros() {
            Partida partida = Partida.iniciar(sala(4, 0, 2, SEGUNDO, TERCERO, CUARTO), AHORA);

            assertAll(
                    () -> assertTrue(partida.sonDelMismoEquipo(ANFITRION, SEGUNDO)),
                    () -> assertFalse(partida.sonDelMismoEquipo(ANFITRION, TERCERO)),
                    () -> assertFalse(Partida.iniciar(sala(3, 0, null, SEGUNDO, TERCERO), AHORA)
                            .sonDelMismoEquipo(ANFITRION, SEGUNDO), "sin equipos nadie es companero"));
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
