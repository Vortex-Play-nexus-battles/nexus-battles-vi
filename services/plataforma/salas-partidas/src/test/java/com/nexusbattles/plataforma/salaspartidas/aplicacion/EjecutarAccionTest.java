package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.MotorDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.MotorNoDisponible;
import com.nexusbattles.plataforma.salaspartidas.dominio.NoEsTuTurno;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.PartidaYaTerminada;
import com.nexusbattles.plataforma.salaspartidas.dominio.ResolucionDelMotor;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.SinObjetivoPosible;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La accion se resuelve en el motor y mueve la vida — RF-JUE-006, RF-JUE-017.
 *
 * <p>Lo que se prueba es la coordinacion: a quien se pregunta, que se aplica,
 * que se anuncia y en que orden. Cuanto dano hace un golpe es del motor y no se
 * decide ni se prueba aqui: el doble devuelve lo que se le diga.
 */
@DisplayName("EjecutarAccion · el combate avanza de verdad (RF-JUE-006)")
class EjecutarAccionTest {

    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRUNO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CARLA = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant AHORA = Instant.parse("2026-09-18T15:00:00Z");

    /** Motor de mentira: devuelve lo que se le diga y anota a quien le preguntaron. */
    private static final class MotorDeMentira implements MotorDeCombate {

        private final int dano;
        private final RuntimeException fallo;
        private final List<String> consultas = new ArrayList<>();

        private MotorDeMentira(int dano, RuntimeException fallo) {
            this.dano = dano;
            this.fallo = fallo;
        }

        static MotorDeMentira queHace(int dano) {
            return new MotorDeMentira(dano, null);
        }

        static MotorDeMentira caido() {
            return new MotorDeMentira(0, new MotorNoDisponible("apagado"));
        }

        @Override
        public ResolucionDelMotor resolver(HeroeDeCombate atacante, HeroeDeCombate objetivo) {
            consultas.add(atacante.nombre() + " -> " + objetivo.nombre());
            if (fallo != null) {
                throw fallo;
            }
            return new ResolucionDelMotor("CAUSAR_DANO", dano, 14);
        }
    }

    private final RepositorioDePartidasEnMemoria partidas = new RepositorioDePartidasEnMemoria();
    private final CanalDePartidaEspia canal = new CanalDePartidaEspia();

    private static HeroeDeCombate heroe(String nombre, int vida) {
        return new HeroeDeCombate("h-" + nombre, nombre, null, 5, vida, vida);
    }

    /** Sala de N participantes, cada uno con su heroe y su vida. */
    private Partida partidaDe(UUID... jugadores) {
        Sala sala = Sala.crear(
                new ParametrosDeSala(6, Modalidad.HASTA_SEIS, 0, false, false, null), jugadores[0],
                new FichaDeParticipante("Ana", heroe("Arquero", 100)));
        for (int i = 1; i < jugadores.length; i++) {
            sala.unirse(jugadores[i],
                    new FichaDeParticipante("J" + i, heroe("Centinela" + i, 100)), null);
        }
        return partidas.guardar(Partida.iniciar(sala, AHORA));
    }

    private EjecutarAccion casoDeUso(MotorDeMentira motor) {
        return new EjecutarAccion(partidas, canal, motor, LiquidacionSinApuesta.nueva(), RecompensaSinLibro.nueva());
    }

    @Test
    @DisplayName("el golpe baja la vida del objetivo y queda guardada")
    void elGolpeBajaLaVidaYSeGuarda() {
        Partida partida = partidaDe(ANA, BRUNO);

        casoDeUso(MotorDeMentira.queHace(30)).ejecutar(partida.id(), ANA, BRUNO, "ATAQUE_BASICO");

        Partida enBase = partidas.buscarPorId(partida.id()).orElseThrow();
        assertEquals(70, enBase.participantes().get(1).heroe().vidaActual());
    }

    @Test
    @DisplayName("se pregunta al motor por el atacante contra el objetivo, no al reves")
    void preguntaAlMotorEnElOrdenCorrecto() {
        MotorDeMentira motor = MotorDeMentira.queHace(10);
        Partida partida = partidaDe(ANA, BRUNO);

        casoDeUso(motor).ejecutar(partida.id(), ANA, BRUNO, null);

        assertEquals(List.of("Arquero -> Centinela1"), motor.consultas);
    }

    @Test
    @DisplayName("la accion resuelta se anuncia con la vida ya aplicada, y antes que el turno")
    void anunciaLaAccionYLuegoElTurno() {
        // El orden importa: quien mira la vista ve primero moverse la barra y
        // despues de quien es el turno, que es el orden en que ocurren.
        Partida partida = partidaDe(ANA, BRUNO);

        casoDeUso(MotorDeMentira.queHace(25)).ejecutar(partida.id(), ANA, BRUNO, null);

        assertAll(
                () -> assertEquals(2, canal.anuncios.size()),
                () -> assertEquals("accion", canal.anuncios.get(0).tipo()),
                () -> assertEquals("turno", canal.anuncios.get(1).tipo()));
    }

    @Test
    @DisplayName("tras el golpe el turno pasa al siguiente")
    void elTurnoPasa() {
        Partida partida = partidaDe(ANA, BRUNO);

        Partida despues = casoDeUso(MotorDeMentira.queHace(5))
                .ejecutar(partida.id(), ANA, BRUNO, null);

        assertEquals(BRUNO, despues.turnoActual().idJugador());
    }

    @Test
    @DisplayName("un golpe que deja a cero termina la partida y ya no pasa el turno")
    void laDerrotaTerminaLaPartida() {
        Partida partida = partidaDe(ANA, BRUNO);

        Partida despues = casoDeUso(MotorDeMentira.queHace(100))
                .ejecutar(partida.id(), ANA, BRUNO, null);

        assertAll(
                () -> assertEquals(EstadoPartida.FINALIZADA, despues.estado()),
                () -> assertEquals(0, despues.participantes().get(1).heroe().vidaActual()),
                () -> assertEquals(ANA, despues.ganador().orElseThrow().idJugador()),
                // Accion y DESPUES fin. Nunca turno: no hay siguiente.
                () -> assertEquals(2, canal.anuncios.size()),
                () -> assertEquals("accion", canal.anuncios.get(0).tipo()),
                () -> assertEquals("fin", canal.anuncios.get(1).tipo()));
    }

    @Test
    @DisplayName("la vida no baja de cero por mucho dano que haga el motor")
    void laVidaNoSeVaANegativo() {
        Partida partida = partidaDe(ANA, BRUNO);

        Partida despues = casoDeUso(MotorDeMentira.queHace(9999))
                .ejecutar(partida.id(), ANA, BRUNO, null);

        assertEquals(0, despues.participantes().get(1).heroe().vidaActual());
    }

    @Test
    @DisplayName("con un solo rival en pie no hace falta indicar objetivo")
    void objetivoAutomaticoEnUnUnoContraUno() {
        Partida partida = partidaDe(ANA, BRUNO);

        Partida despues = casoDeUso(MotorDeMentira.queHace(10))
                .ejecutar(partida.id(), ANA, null, null);

        assertEquals(90, despues.participantes().get(1).heroe().vidaActual());
    }

    @Test
    @DisplayName("con dos rivales en pie hay que decir a quien se ataca")
    void conVariosRivalesHayQueElegir() {
        // Elegir por el jugador seria decidir su jugada.
        Partida partida = partidaDe(ANA, BRUNO, CARLA);

        assertThrows(SinObjetivoPosible.class,
                () -> casoDeUso(MotorDeMentira.queHace(10)).ejecutar(partida.id(), ANA, null, null));
    }

    @Test
    @DisplayName("un objetivo que no esta en la partida se rechaza")
    void objetivoInexistente() {
        Partida partida = partidaDe(ANA, BRUNO);

        assertThrows(SinObjetivoPosible.class,
                () -> casoDeUso(MotorDeMentira.queHace(10))
                        .ejecutar(partida.id(), ANA, UUID.randomUUID(), null));
    }

    @Test
    @DisplayName("no se puede atacarse a uno mismo")
    void nadieSeAtacaASiMismo() {
        Partida partida = partidaDe(ANA, BRUNO);

        assertThrows(SinObjetivoPosible.class,
                () -> casoDeUso(MotorDeMentira.queHace(10)).ejecutar(partida.id(), ANA, ANA, null));
    }

    @Test
    @DisplayName("jugar fuera de turno no llega ni a molestar al motor")
    void fueraDeTurnoNoLlamaAlMotor() {
        MotorDeMentira motor = MotorDeMentira.queHace(10);
        Partida partida = partidaDe(ANA, BRUNO);

        assertThrows(NoEsTuTurno.class,
                () -> casoDeUso(motor).ejecutar(partida.id(), BRUNO, ANA, null));

        assertTrue(motor.consultas.isEmpty());
    }

    @Test
    @DisplayName("una partida terminada se rechaza como terminada")
    void partidaTerminada() {
        Partida partida = partidaDe(ANA, BRUNO);
        partida.terminar();
        partidas.guardar(partida);

        assertThrows(PartidaYaTerminada.class,
                () -> casoDeUso(MotorDeMentira.queHace(10)).ejecutar(partida.id(), ANA, BRUNO, null));
    }

    @Test
    @DisplayName("si el motor no responde no se mueve ni una vida ni se anuncia nada")
    void motorCaidoNoDejaEfectos() {
        // Un dano inventado decidiria el combate con un numero que nadie
        // calculo, y el sintoma aparecería mucho despues en la barra de vida.
        Partida partida = partidaDe(ANA, BRUNO);

        assertThrows(MotorNoDisponible.class,
                () -> casoDeUso(MotorDeMentira.caido()).ejecutar(partida.id(), ANA, BRUNO, null));

        assertAll(
                () -> assertEquals(100, partidas.buscarPorId(partida.id()).orElseThrow()
                        .participantes().get(1).heroe().vidaActual()),
                () -> assertTrue(canal.anuncios.isEmpty()),
                () -> assertEquals(ANA, partidas.buscarPorId(partida.id()).orElseThrow()
                        .turnoActual().idJugador(), "el turno tampoco se movio"));
    }

    @Test
    @DisplayName("sin heroe conocido se pasa turno sin golpear, en vez de inventar un ataque")
    void sinHeroeSoloPasaTurno() {
        // Es el caso de la IA -su heroe lo decide el motor- y el de los
        // participantes anteriores a la puerta de SCRUM-1074.
        // Sala anterior a la puerta de SCRUM-1074: nadie tiene ficha, asi que
        // tampoco la IA -que copia la del anfitrion-.
        MotorDeMentira motor = MotorDeMentira.queHace(50);
        Sala conIa = Sala.crear(
                new ParametrosDeSala(2, Modalidad.CONTRA_IA, 0, true, false, null), ANA);
        Partida partida = partidas.guardar(Partida.iniciar(conIa, AHORA));

        Partida despues = casoDeUso(motor).ejecutar(partida.id(), ANA, null, null);

        assertAll(
                () -> assertTrue(motor.consultas.isEmpty(), "no se pregunta por un heroe que no hay"),
                // Pasan turno los dos -humano y maquina- sin golpear.
                () -> assertTrue(canal.anuncios.stream().allMatch(a -> "turno".equals(a.tipo())),
                        "nadie golpea: solo se pasa turno"),
                () -> assertEquals(EstadoPartida.EN_CURSO, despues.estado()));
    }

    /* HU-JUE-014, CA-04: el fin lleva el reparto de la apuesta. */
    @org.junit.jupiter.api.Nested
    @DisplayName("con apuesta en juego (HU-JUE-014)")
    class ConApuesta {

        private final RepositorioDeSalasEnMemoria salas = new RepositorioDeSalasEnMemoria();
        private final CreditosEnMemoria libro = new CreditosEnMemoria().conSaldo(ANA, 1_000).conSaldo(BRUNO, 1_000);

        private final AcreditadorEnMemoria libroDeRecompensas = new AcreditadorEnMemoria();

        private EjecutarAccion casoDeUsoConApuesta(MotorDeMentira motor) {
            LiquidarApuesta liquidar = new LiquidarApuesta(salas, new RepositorioDeLiquidacionesEnMemoria(), libro,
                    java.time.Clock.fixed(AHORA, java.time.ZoneOffset.UTC),
                    LiquidarApuesta.SiGanaLaMaquina.LIBERAR);
            AcreditarRecompensa recompensa = new AcreditarRecompensa(salas, new RepositorioDeRecompensasEnMemoria(),
                    libroDeRecompensas, new SancionesEnMemoria(),
                    java.time.Clock.fixed(AHORA, java.time.ZoneOffset.UTC));
            return new EjecutarAccion(partidas, canal, motor, liquidar, recompensa);
        }

        /** Ana y Bruno apuestan 100; Bruno tiene 10 de vida: cae al primer golpe. */
        private Partida partidaApostada() {
            Sala sala = Sala.crear(
                    new ParametrosDeSala(6, Modalidad.HASTA_SEIS, 100, false, false, null), ANA,
                    new FichaDeParticipante("Ana", heroe("Arquero", 100)));
            sala = sala.conReserva(libro.reservar(ANA, 100, sala.id(), 0).id());
            sala.unirse(BRUNO, new FichaDeParticipante("Bruno", heroe("Centinela", 10),
                    libro.reservar(BRUNO, 100, sala.id(), 1).id()), null);
            salas.guardar(sala);
            return partidas.guardar(Partida.iniciar(sala, AHORA));
        }

        @Test
        @DisplayName("al terminar, el aviso de fin lleva el reparto y el libro ya movio los creditos")
        void elFinLlevaElReparto() {
            Partida partida = partidaApostada();

            casoDeUsoConApuesta(MotorDeMentira.queHace(50)).ejecutar(partida.id(), ANA, BRUNO, "ATAQUE_BASICO");

            assertAll(
                    () -> assertEquals("fin", canal.anuncios.get(canal.anuncios.size() - 1).tipo()),
                    () -> assertEquals(List.of(
                                    new com.nexusbattles.plataforma.salaspartidas.dominio.RepartoDeCreditos(ANA, 100),
                                    new com.nexusbattles.plataforma.salaspartidas.dominio.RepartoDeCreditos(BRUNO, -100)),
                            canal.repartos.get(0)),
                    () -> assertEquals(1_100, libro.saldoDe(ANA)),
                    () -> assertEquals(900, libro.saldoDe(BRUNO)));
        }

        @Test
        @DisplayName("HU-JUE-012 CA-03: con apuesta, el mismo fin lleva ademas la recompensa por jugar, aparte del reparto")
        void elFinLlevaLaRecompensa() {
            Partida partida = partidaApostada();

            casoDeUsoConApuesta(MotorDeMentira.queHace(50)).ejecutar(partida.id(), ANA, BRUNO, "ATAQUE_BASICO");

            assertAll(
                    () -> assertEquals(1, libroDeRecompensas.informes.size(), "se informo una vez"),
                    () -> assertEquals(partida.id(), libroDeRecompensas.informes.get(0).idPartida()),
                    () -> assertEquals(List.of(ANA), libroDeRecompensas.informes.get(0).ganadores()),
                    () -> assertEquals(List.of(
                                    new com.nexusbattles.plataforma.salaspartidas.dominio.CreditoPorPartida(ANA, 4, true, null),
                                    new com.nexusbattles.plataforma.salaspartidas.dominio.CreditoPorPartida(BRUNO, 1, false, null)),
                            canal.recompensas.get(0), "hasta seis es grupal: 4 al ganador"),
                    () -> assertEquals(2, canal.repartos.get(0).size(), "y el reparto de la apuesta sigue ahi"));
        }

        @Test
        @DisplayName("HU-JUE-012 CA-05: si solo falla la recompensa, la apuesta se liquida igual y el fin sale sin recompensa")
        void soloLaRecompensaCaida() {
            Partida partida = partidaApostada();
            libroDeRecompensas.caido = true;

            casoDeUsoConApuesta(MotorDeMentira.queHace(50)).ejecutar(partida.id(), ANA, BRUNO, "ATAQUE_BASICO");

            assertAll(
                    () -> assertEquals(1_100, libro.saldoDe(ANA), "la apuesta se liquido"),
                    () -> assertEquals(2, canal.repartos.get(0).size()),
                    () -> assertTrue(canal.recompensas.get(0).isEmpty(), "la recompensa queda pendiente"));
        }

        @Test
        @DisplayName("CA-06: si el libro no responde al terminar, la partida termina igual y el fin sale sin reparto")
        void libroCaidoAlTerminar() {
            Partida partida = partidaApostada();
            libro.caido = true;

            Partida despues = casoDeUsoConApuesta(MotorDeMentira.queHace(50))
                    .ejecutar(partida.id(), ANA, BRUNO, "ATAQUE_BASICO");

            assertAll(
                    () -> assertEquals(EstadoPartida.FINALIZADA, despues.estado()),
                    () -> assertEquals(EstadoPartida.FINALIZADA,
                            partidas.buscarPorId(partida.id()).orElseThrow().estado(), "el ultimo golpe quedo guardado"),
                    () -> assertEquals("fin", canal.anuncios.get(canal.anuncios.size() - 1).tipo()),
                    () -> assertTrue(canal.repartos.get(0).isEmpty(), "sin reparto: queda pendiente, no se inventa"),
                    () -> assertEquals(100, libro.reservadoDe(ANA), "nada se perdio: las reservas siguen vivas"));
        }
    }

    /**
     * RF-JUE-004 — HU-SAL-004: modo cooperativo. Equipos de dos: Ana y Bruno
     * contra Carla y Dario. Ana abre.
     */
    @org.junit.jupiter.api.Nested
    @DisplayName("en equipos (HU-SAL-004)")
    class EnEquipos {

        private static final UUID DARIO = UUID.fromString("44444444-4444-4444-4444-444444444444");

        private Partida dosContraDos(int vidaDeCarla, int vidaDeDario) {
            Sala sala = Sala.crear(
                    new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, 0, false, 2), ANA,
                    new FichaDeParticipante("Ana", heroe("Arquero", 100)));
            sala.unirse(BRUNO, new FichaDeParticipante("Bruno", heroe("Centinela", 100)), null);
            sala.unirse(CARLA, new FichaDeParticipante("Carla", heroe("Maga", vidaDeCarla)), null);
            sala.unirse(DARIO, new FichaDeParticipante("Dario", heroe("Guerrero", vidaDeDario)), null);
            return partidas.guardar(Partida.iniciar(sala, AHORA));
        }

        @Test
        @DisplayName("no se ataca a un companero: se rechaza diciendo que es de tu equipo")
        void noSeAtacaAlCompanero() {
            Partida partida = dosContraDos(100, 100);
            EjecutarAccion casoDeUso = casoDeUso(MotorDeMentira.queHace(30));

            SinObjetivoPosible rechazo = assertThrows(SinObjetivoPosible.class,
                    () -> casoDeUso.ejecutar(partida.id(), ANA, BRUNO, null));

            assertAll(
                    () -> assertTrue(rechazo.getMessage().contains("equipo"), rechazo.getMessage()),
                    () -> assertEquals(100, partidas.buscarPorId(partida.id()).orElseThrow()
                            .participantes().get(1).heroe().vidaActual(), "Bruno intacto"));
        }

        @Test
        @DisplayName("con un solo rival en pie no hace falta apuntar, aunque el companero siga vivo")
        void unSoloRivalSeResuelveSolo() {
            // Dario cae antes de empezar a contar: solo queda Carla como rival.
            Partida partida = dosContraDos(100, 100);
            partida.aplicarDano(DARIO, 100);
            partidas.guardar(partida);
            MotorDeMentira motor = MotorDeMentira.queHace(10);

            casoDeUso(motor).ejecutar(partida.id(), ANA, null, null);

            assertEquals(List.of("Arquero -> Maga"), motor.consultas);
        }

        @Test
        @DisplayName("el turno salta a quien ya cayo, sea del equipo que sea")
        void elTurnoSaltaALosCaidos() {
            Partida partida = dosContraDos(100, 100);
            partida.aplicarDano(BRUNO, 100);
            partidas.guardar(partida);

            Partida despues = casoDeUso(MotorDeMentira.queHace(10)).ejecutar(partida.id(), ANA, CARLA, null);

            assertEquals(CARLA, despues.turnoActual().idJugador(), "Bruno cayo: de Ana pasa a Carla");
        }

        @Test
        @DisplayName("la partida termina cuando cae el ultimo del otro equipo, y ganan los dos del equipo en pie")
        void ganaElEquipo() {
            Partida partida = dosContraDos(10, 100);
            partida.aplicarDano(DARIO, 100);
            partidas.guardar(partida);

            Partida despues = casoDeUso(MotorDeMentira.queHace(50)).ejecutar(partida.id(), ANA, CARLA, null);

            assertAll(
                    () -> assertEquals(EstadoPartida.FINALIZADA, despues.estado()),
                    () -> assertEquals(java.util.Optional.of(1), despues.equipoGanador()),
                    () -> assertEquals(List.of(ANA, BRUNO), despues.ganadores().stream()
                            .map(com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida::idJugador)
                            .toList()),
                    () -> assertEquals("fin", canal.anuncios.get(canal.anuncios.size() - 1).tipo()));
        }
    }
}
