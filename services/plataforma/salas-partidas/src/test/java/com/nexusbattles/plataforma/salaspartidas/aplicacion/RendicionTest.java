package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.AccionResuelta;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.PartidaAjena;
import com.nexusbattles.plataforma.salaspartidas.dominio.PartidaNoEncontrada;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepartoDeCreditos;
import com.nexusbattles.plataforma.salaspartidas.dominio.ResultadoDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rendirse — salas-partidas.yaml 1.10.0, {@code POST /partidas/{id}/rendicion}
 * (revision del modo jugador del 6-oct, punto 19: «Salir» con aviso de que
 * abandonar cuenta como derrota).
 *
 * <p>Lo que se fija aqui es que el servidor manda: quien se rinde sale de la
 * llamada (el token, en el controlador), el ganador lo decide la regla de
 * siempre —quien queda en pie— y la apuesta se liquida con la misma pieza que
 * cuando cae el ultimo golpe. Ningun dato del cliente entra en esa decision.
 */
@DisplayName("Rendicion · salir del combate cuenta como derrota (1.10.0)")
class RendicionTest {

    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRUNO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CARLA = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID DARIO = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant AHORA = Instant.parse("2026-10-06T15:00:00Z");

    private final RepositorioDePartidasEnMemoria partidas = new RepositorioDePartidasEnMemoria();
    private final RepositorioDeSalasEnMemoria salas = new RepositorioDeSalasEnMemoria();
    private final CanalDePartidaEspia canal = new CanalDePartidaEspia();
    private final MotorDeCombateSimulado motor = new MotorDeCombateSimulado();

    private static HeroeDeCombate heroe(String nombre, int vida) {
        return new HeroeDeCombate("h-" + nombre, nombre, "Guerrero Tanque", null, 1, vida, vida, 11);
    }

    private EjecutarAccion casoDeUso() {
        return new EjecutarAccion(partidas, salas, canal, motor, LiquidacionSinApuesta.nueva(),
                RecompensaSinLibro.nueva(), null, Clock.fixed(AHORA, ZoneOffset.UTC), () -> null);
    }

    /** Todos contra todos, en orden de entrada: abre el primero. */
    private Partida partidaDe(UUID... jugadores) {
        Sala sala = Sala.crear(new ParametrosDeSala(6, Modalidad.HASTA_SEIS, 0, false, false, null), jugadores[0],
                new FichaDeParticipante("Ana", heroe("Arquero", 100)));
        for (int i = 1; i < jugadores.length; i++) {
            sala.unirse(jugadores[i], new FichaDeParticipante("J" + i, heroe("Centinela" + i, 100)), null);
        }
        sala.iniciarPartida(jugadores[0]);
        salas.guardar(sala);
        return partidas.guardar(Partida.iniciar(sala, AHORA));
    }

    private Partida enBase(Partida partida) {
        return partidas.buscarPorId(partida.id()).orElseThrow();
    }

    private static int vidaDe(Partida partida, UUID jugador) {
        return partida.participante(jugador).orElseThrow().heroe().vidaActual();
    }

    @Test
    @DisplayName("1 vs 1 en su turno: termina, gana el rival, y el aviso de rendicion va antes del fin")
    void unoContraUnoEnSuTurno() {
        Partida partida = partidaDe(ANA, BRUNO);

        Partida despues = casoDeUso().rendirse(partida.id(), ANA);

        assertAll(
                () -> assertEquals(EstadoPartida.FINALIZADA, despues.estado()),
                () -> assertEquals(BRUNO, despues.ganador().orElseThrow().idJugador(), "gana quien sigue en pie"),
                () -> assertEquals(ResultadoDePartida.GANADOR, despues.resultado().orElseThrow()),
                () -> assertEquals(0, vidaDe(despues, ANA), "quien se rinde queda fuera de combate"),
                () -> assertEquals(100, vidaDe(despues, BRUNO), "al rival no se le toca"),
                () -> assertEquals(AHORA, despues.finalizadaEn()),
                () -> assertEquals(EstadoSala.FINALIZADA, salas.buscarPorId(despues.idSala()).orElseThrow().estado()),
                () -> assertEquals(List.of("rendido", "fin"), canal.tipos()),
                () -> assertEquals(List.of(ANA), canal.rendidos),
                () -> assertTrue(motor.acciones.isEmpty(), "rendirse no es una accion: no hay golpe inventado"),
                () -> assertEquals(EstadoPartida.FINALIZADA, enBase(partida).estado(), "quedo guardada"));
    }

    @Test
    @DisplayName("1 vs 1 fuera de su turno: tambien termina, y gana quien tenia el turno")
    void unoContraUnoFueraDeSuTurno() {
        Partida partida = partidaDe(ANA, BRUNO);

        Partida despues = casoDeUso().rendirse(partida.id(), BRUNO);

        assertAll(
                () -> assertEquals(EstadoPartida.FINALIZADA, despues.estado()),
                () -> assertEquals(ANA, despues.ganador().orElseThrow().idJugador()),
                () -> assertEquals(List.of("rendido", "fin"), canal.tipos()));
    }

    @Test
    @DisplayName("es idempotente: rendirse otra vez devuelve la partida tal cual y no anuncia nada")
    void idempotente() {
        Partida partida = partidaDe(ANA, BRUNO);
        EjecutarAccion casoDeUso = casoDeUso();
        casoDeUso.rendirse(partida.id(), ANA);
        int anunciosAntes = canal.anuncios.size();

        Partida otraVez = casoDeUso.rendirse(partida.id(), ANA);

        assertAll(
                () -> assertEquals(EstadoPartida.FINALIZADA, otraVez.estado()),
                () -> assertEquals(BRUNO, otraVez.ganador().orElseThrow().idJugador(), "el ganador no cambia"),
                () -> assertEquals(anunciosAntes, canal.anuncios.size(), "ni un aviso mas"));
    }

    @Test
    @DisplayName("el ganador tampoco puede «rendirse» despues: la partida terminada no cambia")
    void elGanadorNoCambiaElResultado() {
        Partida partida = partidaDe(ANA, BRUNO);
        EjecutarAccion casoDeUso = casoDeUso();
        casoDeUso.rendirse(partida.id(), ANA);

        Partida despues = casoDeUso.rendirse(partida.id(), BRUNO);

        assertAll(
                () -> assertEquals(BRUNO, despues.ganador().orElseThrow().idJugador()),
                () -> assertEquals(100, vidaDe(despues, BRUNO)));
    }

    @Test
    @DisplayName("quien no juega la partida no puede rendirse en ella: partida ajena, y nada cambia")
    void soloQuienJuega() {
        Partida partida = partidaDe(ANA, BRUNO);

        assertThrows(PartidaAjena.class, () -> casoDeUso().rendirse(partida.id(), CARLA));

        assertAll(
                () -> assertEquals(EstadoPartida.EN_CURSO, enBase(partida).estado()),
                () -> assertTrue(canal.anuncios.isEmpty()));
    }

    @Test
    @DisplayName("una partida que no existe es 404")
    void partidaInexistente() {
        assertThrows(PartidaNoEncontrada.class, () -> casoDeUso().rendirse(UUID.randomUUID(), ANA));
    }

    @Test
    @DisplayName("todos contra todos: en su turno, el combate sigue sin el y el turno pasa al siguiente")
    void variosEnSuTurno() {
        Partida partida = partidaDe(ANA, BRUNO, CARLA);

        Partida despues = casoDeUso().rendirse(partida.id(), ANA);

        assertAll(
                () -> assertEquals(EstadoPartida.EN_CURSO, despues.estado()),
                () -> assertEquals(0, vidaDe(despues, ANA)),
                () -> assertEquals(BRUNO, despues.turnoActual().idJugador()),
                () -> assertEquals(List.of("rendido", "turno"), canal.tipos()),
                () -> assertEquals(EjecutarAccion.POR_RENDICION, canal.motivos.get(0),
                        "el turno dice por que paso: no hubo accion"));
    }

    @Test
    @DisplayName("todos contra todos: fuera de su turno, solo se anuncia la rendicion y el turno no se mueve")
    void variosFueraDeSuTurno() {
        Partida partida = partidaDe(ANA, BRUNO, CARLA);

        Partida despues = casoDeUso().rendirse(partida.id(), CARLA);

        assertAll(
                () -> assertEquals(EstadoPartida.EN_CURSO, despues.estado()),
                () -> assertEquals(ANA, despues.turnoActual().idJugador()),
                () -> assertEquals(List.of("rendido"), canal.tipos()),
                () -> assertEquals(0, vidaDe(despues, CARLA)));
    }

    @Test
    @DisplayName("quien se rindio ya no juega: el turno lo salta como a cualquier caido")
    void elRendidoNoVuelveAJugar() {
        Partida partida = partidaDe(ANA, BRUNO, CARLA);
        EjecutarAccion casoDeUso = casoDeUso();
        casoDeUso.rendirse(partida.id(), BRUNO);

        Partida despues = casoDeUso.ejecutar(partida.id(), ANA, CARLA, null);

        assertEquals(CARLA, despues.turnoActual().idJugador(), "de Ana pasa a Carla: Bruno se rindio");
    }

    @Nested
    @DisplayName("contra la maquina")
    class ContraLaMaquina {

        private Partida soloContraLaMaquina() {
            Sala sala = Sala.crear(new ParametrosDeSala(2, Modalidad.CONTRA_IA, 0, true, false, null), ANA,
                    new FichaDeParticipante("Ana", heroe("Arquero", 100)));
            sala.iniciarPartida(ANA);
            salas.guardar(sala);
            return partidas.guardar(Partida.iniciar(sala, AHORA));
        }

        private UUID maquinaDe(Partida partida) {
            return partida.participantes().stream().filter(ParticipanteDePartida::esIA)
                    .findFirst().orElseThrow().idJugador();
        }

        @Test
        @DisplayName("rendirse contra la IA es perder contra la IA")
        void ganaLaMaquina() {
            Partida partida = soloContraLaMaquina();
            UUID maquina = maquinaDe(partida);

            Partida despues = casoDeUso().rendirse(partida.id(), ANA);

            assertAll(
                    () -> assertEquals(EstadoPartida.FINALIZADA, despues.estado()),
                    () -> assertEquals(maquina, despues.ganador().orElseThrow().idJugador()),
                    () -> assertEquals(List.of("rendido", "fin"), canal.tipos()));
        }

        @Test
        @DisplayName("la maquina no se rinde: su identificador no es de un jugador de la partida")
        void laMaquinaNoSeRinde() {
            Partida partida = soloContraLaMaquina();

            assertThrows(PartidaAjena.class, () -> casoDeUso().rendirse(partida.id(), maquinaDe(partida)));
            assertEquals(EstadoPartida.EN_CURSO, enBase(partida).estado());
        }
    }

    @Test
    @DisplayName("si tras la rendicion le toca a la maquina, juega ella y el turno vuelve a un humano")
    void despuesJuegaLaMaquina() {
        Sala sala = Sala.crear(new ParametrosDeSala(6, Modalidad.HASTA_SEIS, 0, 1, false, null), ANA,
                new FichaDeParticipante("Ana", heroe("Arquero", 100)));
        sala.unirse(BRUNO, new FichaDeParticipante("Bruno", heroe("Centinela", 100)), null);
        salas.guardar(sala);
        Partida partida = partidas.guardar(Partida.iniciar(sala, AHORA));
        UUID maquina = partida.participantes().stream().filter(ParticipanteDePartida::esIA)
                .findFirst().orElseThrow().idJugador();
        EjecutarAccion casoDeUso = casoDeUso();
        casoDeUso.ejecutar(partida.id(), ANA, maquina, null);
        assertEquals(BRUNO, enBase(partida).turnoActual().idJugador(), "precondicion: le toca a Bruno");
        canal.anuncios.clear();
        canal.acciones.clear();

        Partida despues = casoDeUso.rendirse(partida.id(), BRUNO);

        AccionResuelta deLaMaquina = canal.acciones.get(0);
        assertAll(
                () -> assertEquals(EstadoPartida.EN_CURSO, despues.estado()),
                () -> assertEquals("rendido", canal.tipos().get(0)),
                () -> assertEquals(maquina, deLaMaquina.idEjecutor(), "juega la maquina, sin esperar a nadie"),
                () -> assertEquals(ANA, despues.turnoActual().idJugador(), "y vuelve a Ana"));
    }

    @Nested
    @DisplayName("en equipos")
    class EnEquipos {

        private Partida dosContraDos() {
            Sala sala = Sala.crear(new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, 0, false, 2), ANA,
                    new FichaDeParticipante("Ana", heroe("Arquero", 100)));
            sala.unirse(BRUNO, new FichaDeParticipante("Bruno", heroe("Centinela", 100)), null);
            sala.unirse(CARLA, new FichaDeParticipante("Carla", heroe("Maga", 100)), null);
            sala.unirse(DARIO, new FichaDeParticipante("Dario", heroe("Guerrero", 100)), null);
            salas.guardar(sala);
            return partidas.guardar(Partida.iniciar(sala, AHORA));
        }

        @Test
        @DisplayName("si se rinde uno, su companero sigue y el combate tambien")
        void elCompaneroSigue() {
            Partida partida = dosContraDos();

            Partida despues = casoDeUso().rendirse(partida.id(), ANA);

            assertAll(
                    () -> assertEquals(EstadoPartida.EN_CURSO, despues.estado()),
                    () -> assertEquals(100, vidaDe(despues, BRUNO)));
        }

        @Test
        @DisplayName("si se rinde el equipo entero, gana el otro equipo")
        void ganaElOtroEquipo() {
            Partida partida = dosContraDos();
            EjecutarAccion casoDeUso = casoDeUso();
            casoDeUso.rendirse(partida.id(), ANA);

            Partida despues = casoDeUso.rendirse(partida.id(), BRUNO);

            assertAll(
                    () -> assertEquals(EstadoPartida.FINALIZADA, despues.estado()),
                    () -> assertEquals(List.of(CARLA, DARIO),
                            despues.ganadores().stream().map(ParticipanteDePartida::idJugador).toList()));
        }
    }

    /** «se liquidará la apuesta según las reglas de la partida»: la misma liquidacion que el ultimo golpe. */
    @Nested
    @DisplayName("con apuesta")
    class ConApuesta {

        private final CreditosEnMemoria libro = new CreditosEnMemoria().conSaldo(ANA, 1_000).conSaldo(BRUNO, 1_000);

        @Test
        @DisplayName("el que se rinde pierde lo apostado y el rival lo cobra, con el reparto en el aviso de fin")
        void seLiquidaComoUnaDerrota() {
            Sala sala = Sala.crear(new ParametrosDeSala(6, Modalidad.HASTA_SEIS, 100, false, false, null), ANA,
                    new FichaDeParticipante("Ana", heroe("Arquero", 100)));
            sala = sala.conReserva(libro.reservar(ANA, 100, sala.id(), 0).id());
            sala.unirse(BRUNO, new FichaDeParticipante("Bruno", heroe("Centinela", 100),
                    libro.reservar(BRUNO, 100, sala.id(), 1).id()), null);
            salas.guardar(sala);
            Partida partida = partidas.guardar(Partida.iniciar(sala, AHORA));
            LiquidarApuesta liquidar = new LiquidarApuesta(salas, new RepositorioDeLiquidacionesEnMemoria(), libro,
                    Clock.fixed(AHORA, ZoneOffset.UTC), LiquidarApuesta.SiGanaLaMaquina.LIBERAR);
            AcreditarRecompensa recompensa = new AcreditarRecompensa(salas, new RepositorioDeRecompensasEnMemoria(),
                    new AcreditadorEnMemoria(), new SancionesEnMemoria(), Clock.fixed(AHORA, ZoneOffset.UTC));
            EjecutarAccion casoDeUso = new EjecutarAccion(partidas, salas, canal, motor, liquidar, recompensa, null,
                    Clock.fixed(AHORA, ZoneOffset.UTC), () -> null);

            Partida despues = casoDeUso.rendirse(partida.id(), BRUNO);

            assertAll(
                    () -> assertEquals(ANA, despues.ganador().orElseThrow().idJugador()),
                    () -> assertEquals(List.of(new RepartoDeCreditos(ANA, 100), new RepartoDeCreditos(BRUNO, -100)),
                            canal.repartos.get(0)),
                    () -> assertEquals(1_100, libro.saldoDe(ANA)),
                    () -> assertEquals(900, libro.saldoDe(BRUNO)));
        }
    }

    @Test
    @DisplayName("la partida devuelta es la guardada: lo que ve el jugador es lo que quedo en el servidor")
    void devuelveLaGuardada() {
        Partida partida = partidaDe(ANA, BRUNO, CARLA);

        Partida despues = casoDeUso().rendirse(partida.id(), CARLA);

        assertAll(
                () -> assertEquals(0, vidaDe(enBase(partida), CARLA)),
                () -> assertEquals(vidaDe(enBase(partida), CARLA), vidaDe(despues, CARLA)),
                () -> assertEquals(enBase(partida).turnoActual().idJugador(), despues.turnoActual().idJugador()));
    }
}
