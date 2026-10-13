package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.AccionNoPermitida;
import com.nexusbattles.plataforma.salaspartidas.dominio.AccionResuelta;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.MotorDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.MotorNoDisponible;
import com.nexusbattles.plataforma.salaspartidas.dominio.NoEsTuTurno;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.PartidaModificadaConcurrentemente;
import com.nexusbattles.plataforma.salaspartidas.dominio.PartidaYaTerminada;
import com.nexusbattles.plataforma.salaspartidas.dominio.ResultadoDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.SinObjetivoPosible;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La accion se resuelve en el motor y el combate avanza — RF-JUE-006,
 * RF-JUE-017, §6 (B7).
 *
 * <p>Lo que se prueba es la coordinacion: que se le pide al motor, que se
 * guarda, que se anuncia y en que orden, cuando pasa el turno y cuando termina.
 * Las reglas de la seccion 6 (tiradas, poder, cargas) son del motor y se
 * prueban alli; aqui el doble {@link MotorDeCombateSimulado} hace un dano fijo.
 */
@DisplayName("EjecutarAccion · el combate avanza con el motor (RF-JUE-006, §6)")
class EjecutarAccionTest {

    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRUNO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CARLA = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant AHORA = Instant.parse("2026-09-18T15:00:00Z");

    private final RepositorioDePartidasEnMemoria partidas = new RepositorioDePartidasEnMemoria();
    private final RepositorioDeSalasEnMemoria salas = new RepositorioDeSalasEnMemoria();
    private final CanalDePartidaEspia canal = new CanalDePartidaEspia();
    private final MotorDeCombateSimulado motor = new MotorDeCombateSimulado();

    /** Un heroe con prototipo: el motor lo puede resolver. */
    private static HeroeDeCombate heroe(String nombre, int vida) {
        return new HeroeDeCombate("h-" + nombre, nombre, "Guerrero Tanque", null, 1, vida, vida, 11);
    }

    /** Sala de N participantes, cada uno con su heroe de 100 de vida, en orden de entrada. */
    private Partida partidaDe(UUID... jugadores) {
        Sala sala = Sala.crear(
                new ParametrosDeSala(6, Modalidad.HASTA_SEIS, 0, false, false, null), jugadores[0],
                new FichaDeParticipante("Ana", heroe("Arquero", 100)));
        for (int i = 1; i < jugadores.length; i++) {
            sala.unirse(jugadores[i],
                    new FichaDeParticipante("J" + i, heroe("Centinela" + i, 100)), null);
        }
        sala.iniciarPartida(jugadores[0]);
        salas.guardar(sala);
        return partidas.guardar(Partida.iniciar(sala, AHORA));
    }

    private EjecutarAccion casoDeUso() {
        return new EjecutarAccion(partidas, salas, canal, motor, LiquidacionSinApuesta.nueva(),
                RecompensaSinLibro.nueva(), null, Clock.fixed(AHORA, ZoneOffset.UTC), () -> null);
    }

    private Partida enBase(Partida partida) {
        return partidas.buscarPorId(partida.id()).orElseThrow();
    }

    @Test
    @DisplayName("el golpe baja la vida del objetivo y queda guardada")
    void elGolpeBajaLaVidaYSeGuarda() {
        Partida partida = partidaDe(ANA, BRUNO);
        motor.dano = 30;

        casoDeUso().ejecutar(partida.id(), ANA, BRUNO, "ATAQUE_BASICO");

        assertEquals(70, enBase(partida).participantes().get(1).heroe().vidaActual());
    }

    @Test
    @DisplayName("al motor se le pide la accion elegida, del ejecutor contra el objetivo")
    void seLePideAlMotorLaAccionElegida() {
        Partida partida = partidaDe(ANA, BRUNO);

        casoDeUso().ejecutar(partida.id(), ANA, BRUNO, "Golpe con escudo");

        assertEquals("Golpe con escudo " + ANA + "->" + BRUNO, motor.acciones.get(0));
    }

    @Test
    @DisplayName("sin codigo, la basica de su heroe: ataque basico si ataca")
    void sinCodigoLaBasica() {
        Partida partida = partidaDe(ANA, BRUNO);

        casoDeUso().ejecutar(partida.id(), ANA, BRUNO, "  ");

        assertTrue(motor.acciones.get(0).startsWith(MotorDeCombate.ATAQUE_BASICO + " "));
    }

    @Test
    @DisplayName("se guarda el estado de combate que devolvio el motor: poder y turnos del ejecutor")
    void seGuardaElEstadoDeCombate() {
        Partida partida = partidaDe(ANA, BRUNO);
        motor.costo = 4;

        Partida despues = casoDeUso().ejecutar(partida.id(), ANA, BRUNO, "Mano de piedra");

        var ana = despues.participante(ANA).orElseThrow().combate();
        assertAll(
                () -> assertEquals(MotorDeCombateSimulado.PODER_MAXIMO - 4, ana.poderActual()),
                () -> assertEquals(1, ana.turnosJugados()),
                () -> assertEquals(MotorDeCombateSimulado.PODER_MAXIMO,
                        enBase(partida).participante(BRUNO).orElseThrow().combate().poderActual(),
                        "Bruno empezo su turno: +2, sin pasar del maximo"));
    }

    @Test
    @DisplayName("la accion resuelta se anuncia con la vida ya aplicada, la tirada, y antes que el turno")
    void anunciaLaAccionYLuegoElTurno() {
        Partida partida = partidaDe(ANA, BRUNO);
        motor.dano = 25;

        casoDeUso().ejecutar(partida.id(), ANA, BRUNO, null);

        AccionResuelta accion = canal.acciones.get(0);
        AccionResuelta.Afectado bruno = accion.afectados().stream()
                .filter(a -> a.idJugador().equals(BRUNO)).findFirst().orElseThrow();
        assertAll(
                () -> assertEquals(List.of("accion", "turno"), canal.tipos()),
                () -> assertEquals(List.of("ACCION"), canal.motivos),
                () -> assertEquals("CAUSAR_DANO", accion.accion().nombre(), "en un ataque, la categoria"),
                () -> assertEquals(14, accion.accion().ataque().ataqueResuelto()),
                () -> assertEquals(75, bruno.vidaActual()),
                () -> assertEquals(-25, bruno.diferencia()),
                () -> assertTrue(accion.afectados().stream().anyMatch(a -> a.idJugador().equals(ANA)),
                        "el ejecutor tambien: cambia su poder aunque no su vida"));
    }

    @Test
    @DisplayName("tras la accion el turno pasa al siguiente y el motor empieza su turno")
    void elTurnoPasa() {
        Partida partida = partidaDe(ANA, BRUNO);

        Partida despues = casoDeUso().ejecutar(partida.id(), ANA, BRUNO, null);

        assertAll(
                () -> assertEquals(BRUNO, despues.turnoActual().idJugador()),
                () -> assertEquals(List.of(BRUNO.toString()), motor.turnos));
    }

    @Test
    @DisplayName("un golpe que deja a cero termina la partida, con resultado, hora y sala finalizada")
    void laDerrotaTerminaLaPartida() {
        Partida partida = partidaDe(ANA, BRUNO);
        motor.dano = 100;

        Partida despues = casoDeUso().ejecutar(partida.id(), ANA, BRUNO, null);

        assertAll(
                () -> assertEquals(EstadoPartida.FINALIZADA, despues.estado()),
                () -> assertEquals(0, despues.participantes().get(1).heroe().vidaActual()),
                () -> assertEquals(ANA, despues.ganador().orElseThrow().idJugador()),
                () -> assertEquals(ResultadoDePartida.GANADOR, despues.resultado().orElseThrow()),
                () -> assertEquals(AHORA, despues.finalizadaEn()),
                () -> assertEquals(EstadoSala.FINALIZADA, salas.buscarPorId(despues.idSala()).orElseThrow().estado()),
                // Accion y DESPUES fin. Nunca turno: no hay siguiente.
                () -> assertEquals(List.of("accion", "fin"), canal.tipos()),
                () -> assertTrue(motor.turnos.isEmpty(), "no empieza ningun turno"));
    }

    @Test
    @DisplayName("HU-TOR-004 CA-04: al terminar, la partida se pasa al informe de torneo, despues del aviso de fin")
    void alTerminarInformaAlTorneo() {
        Partida partida = partidaDe(ANA, BRUNO);
        motor.dano = 100;
        List<Partida> informadas = new ArrayList<>();
        InformarEncuentroDeTorneo torneo = new InformarEncuentroDeTorneo(
                new InformarEncuentroDeTorneoTest.VinculosEnMemoria(),
                (idTorneo, numero, ganador, idPartida) -> { }, Clock.systemUTC()) {
            @Override
            public java.util.Optional<com.nexusbattles.plataforma.salaspartidas.dominio.VinculoDeTorneo> alTerminar(
                    Partida terminada) {
                informadas.add(terminada);
                assertEquals("fin", canal.anuncios.get(canal.anuncios.size() - 1).tipo(), "primero el aviso");
                return java.util.Optional.empty();
            }
        };

        new EjecutarAccion(partidas, canal, motor, LiquidacionSinApuesta.nueva(), RecompensaSinLibro.nueva(), torneo)
                .ejecutar(partida.id(), ANA, BRUNO, null);

        assertAll(
                () -> assertEquals(1, informadas.size()),
                () -> assertEquals(EstadoPartida.FINALIZADA, informadas.get(0).estado()));
    }

    @Test
    @DisplayName("HU-TOR-004: si el informe de torneo revienta, la partida termina igual y el fin ya salio")
    void unFalloDelTorneoNoRompeLaPartida() {
        Partida partida = partidaDe(ANA, BRUNO);
        motor.dano = 100;
        InformarEncuentroDeTorneo torneo = new InformarEncuentroDeTorneo(
                new InformarEncuentroDeTorneoTest.VinculosEnMemoria(),
                (idTorneo, numero, ganador, idPartida) -> { }, Clock.systemUTC()) {
            @Override
            public java.util.Optional<com.nexusbattles.plataforma.salaspartidas.dominio.VinculoDeTorneo> alTerminar(
                    Partida terminada) {
                throw new IllegalStateException("la base de vinculos no responde");
            }
        };

        Partida despues = new EjecutarAccion(partidas, canal, motor, LiquidacionSinApuesta.nueva(),
                RecompensaSinLibro.nueva(), torneo).ejecutar(partida.id(), ANA, BRUNO, null);

        assertAll(
                () -> assertEquals(EstadoPartida.FINALIZADA, despues.estado()),
                () -> assertEquals("fin", canal.anuncios.get(1).tipo()));
    }

    @Test
    @DisplayName("con un solo rival en pie no hace falta indicar objetivo")
    void objetivoAutomaticoEnUnUnoContraUno() {
        Partida partida = partidaDe(ANA, BRUNO);

        Partida despues = casoDeUso().ejecutar(partida.id(), ANA, null, null);

        assertEquals(90, despues.participantes().get(1).heroe().vidaActual());
    }

    @Test
    @DisplayName("con dos rivales en pie el motor pide el objetivo: 409 accion-no-permitida, y no se aplica nada")
    void conVariosRivalesHayQueElegir() {
        Partida partida = partidaDe(ANA, BRUNO, CARLA);

        AccionNoPermitida rechazo = assertThrows(AccionNoPermitida.class,
                () -> casoDeUso().ejecutar(partida.id(), ANA, null, null));

        assertAll(
                () -> assertEquals("OBJETIVO_REQUERIDO", rechazo.motivo()),
                () -> assertEquals(AccionNoPermitida.TIPO, rechazo.tipo()),
                () -> assertTrue(canal.anuncios.isEmpty()),
                () -> assertEquals(ANA, enBase(partida).turnoActual().idJugador(), "el turno sigue siendo suyo"),
                () -> assertEquals(1, enBase(partida).version(), "no se guardo nada"));
    }

    @Test
    @DisplayName("un objetivo que no esta en la partida se rechaza sin molestar al motor")
    void objetivoInexistente() {
        Partida partida = partidaDe(ANA, BRUNO);

        assertThrows(SinObjetivoPosible.class,
                () -> casoDeUso().ejecutar(partida.id(), ANA, UUID.randomUUID(), null));
        assertTrue(motor.acciones.isEmpty());
    }

    @Test
    @DisplayName("atacarse a uno mismo lo rechaza el motor")
    void nadieSeAtacaASiMismo() {
        Partida partida = partidaDe(ANA, BRUNO);

        assertThrows(AccionNoPermitida.class, () -> casoDeUso().ejecutar(partida.id(), ANA, ANA, null));
    }

    @Test
    @DisplayName("jugar fuera de turno no llega ni a molestar al motor")
    void fueraDeTurnoNoLlamaAlMotor() {
        Partida partida = partidaDe(ANA, BRUNO);

        assertThrows(NoEsTuTurno.class, () -> casoDeUso().ejecutar(partida.id(), BRUNO, ANA, null));

        assertTrue(motor.acciones.isEmpty());
    }

    @Test
    @DisplayName("una partida terminada se rechaza como terminada")
    void partidaTerminada() {
        Partida partida = partidaDe(ANA, BRUNO);
        partida.terminar();
        partidas.guardar(partida);

        assertThrows(PartidaYaTerminada.class, () -> casoDeUso().ejecutar(partida.id(), ANA, BRUNO, null));
    }

    @Test
    @DisplayName("si el motor no responde no se mueve ni una vida ni se anuncia nada")
    void motorCaidoNoDejaEfectos() {
        Partida partida = partidaDe(ANA, BRUNO);
        motor.falloAlResolver = new MotorNoDisponible("apagado");

        assertThrows(MotorNoDisponible.class, () -> casoDeUso().ejecutar(partida.id(), ANA, BRUNO, null));

        assertAll(
                () -> assertEquals(100, enBase(partida).participantes().get(1).heroe().vidaActual()),
                () -> assertTrue(canal.anuncios.isEmpty()),
                () -> assertEquals(ANA, enBase(partida).turnoActual().idJugador(), "el turno tampoco se movio"));
    }

    @Test
    @DisplayName("si el motor no responde al EMPEZAR el turno siguiente, la accion vale y el turno pasa igual")
    void motorCaidoAlEmpezarElTurno() {
        Partida partida = partidaDe(ANA, BRUNO);
        motor.falloAlEmpezarTurno = new MotorNoDisponible("apagado");

        Partida despues = casoDeUso().ejecutar(partida.id(), ANA, BRUNO, null);

        assertAll(
                () -> assertEquals(90, despues.participante(BRUNO).orElseThrow().heroe().vidaActual()),
                () -> assertEquals(BRUNO, despues.turnoActual().idJugador()),
                () -> assertEquals(List.of("accion", "turno"), canal.tipos()));
    }

    @Test
    @DisplayName("un sangrado al empezar el turno se anuncia como EFECTO_POR_TURNO; si lo tumba, el turno sigue")
    void sangradoAlEmpezarElTurno() {
        Partida partida = partidaDe(ANA, BRUNO, CARLA);
        motor.sangrado.put(BRUNO, 95);

        Partida despues = casoDeUso().ejecutar(partida.id(), ANA, BRUNO, null);

        AccionResuelta efecto = canal.acciones.get(1);
        assertAll(
                () -> assertEquals(0, despues.participante(BRUNO).orElseThrow().heroe().vidaActual(), "90 - 95"),
                () -> assertEquals(AccionResuelta.EFECTO_POR_TURNO, efecto.accion().codigo()),
                () -> assertEquals("Cierra sangrienta", efecto.accion().nombre()),
                () -> assertEquals(-90, efecto.afectados().get(0).diferencia()),
                () -> assertEquals(CARLA, despues.turnoActual().idJugador(), "Bruno cayo: le toca a Carla"),
                () -> assertEquals(List.of(BRUNO.toString(), CARLA.toString()), motor.turnos),
                () -> assertEquals(List.of("accion", "accion", "turno"), canal.tipos()));
    }

    @Test
    @DisplayName("un sangrado que deja un solo bando en pie termina la partida")
    void sangradoQueTermina() {
        Partida partida = partidaDe(ANA, BRUNO);
        motor.sangrado.put(BRUNO, 90);

        Partida despues = casoDeUso().ejecutar(partida.id(), ANA, BRUNO, null);

        assertAll(
                () -> assertEquals(EstadoPartida.FINALIZADA, despues.estado()),
                () -> assertEquals(ANA, despues.ganador().orElseThrow().idJugador()),
                () -> assertEquals(List.of("accion", "accion", "fin"), canal.tipos()));
    }

    @Test
    @DisplayName("sin heroe conocido se pasa turno sin golpear, en vez de inventar un ataque")
    void sinHeroeSoloPasaTurno() {
        // Sala anterior a la puerta de SCRUM-1074: nadie tiene ficha, asi que
        // tampoco la IA -que copia la del anfitrion-.
        Sala conIa = Sala.crear(
                new ParametrosDeSala(2, Modalidad.CONTRA_IA, 0, true, false, null), ANA);
        Partida partida = partidas.guardar(Partida.iniciar(conIa, AHORA));

        Partida despues = casoDeUso().ejecutar(partida.id(), ANA, null, null);

        assertAll(
                () -> assertTrue(motor.acciones.isEmpty(), "no se pregunta por un heroe que no hay"),
                () -> assertTrue(canal.anuncios.stream().allMatch(a -> "turno".equals(a.tipo())),
                        "nadie golpea: solo se pasa turno"),
                () -> assertTrue(canal.motivos.stream().allMatch("TURNO_PERDIDO"::equals)),
                () -> assertEquals(EstadoPartida.EN_CURSO, despues.estado()));
    }

    @Test
    @DisplayName("dos acciones del mismo turno: la que llega tarde recibe partida-modificada y no se aplica")
    void dosAccionesALaVez() {
        Partida partida = partidaDe(ANA, BRUNO);
        // Mientras el motor resuelve la primera, otra escritura de la partida
        // se adelanta (el doble clic de la otra pestana).
        motor.mientrasResuelve = leida -> {
            motor.mientrasResuelve = otra -> { };
            Partida otraCopia = enBase(partida);
            otraCopia.fijarVencimientoDelTurno(AHORA);
            partidas.guardar(otraCopia);
        };

        assertThrows(PartidaModificadaConcurrentemente.class,
                () -> casoDeUso().ejecutar(partida.id(), ANA, BRUNO, null));

        assertAll(
                () -> assertEquals(100, enBase(partida).participante(BRUNO).orElseThrow().heroe().vidaActual(),
                        "la que llego tarde no movio la vida"),
                () -> assertTrue(canal.anuncios.isEmpty(), "ni se anuncio"));
    }

    @Nested
    @DisplayName("tiempo por turno (D-B7-14)")
    class TiempoPorTurno {

        private EjecutarAccion conTiempo(Integer segundos, Instant ahora) {
            return new EjecutarAccion(partidas, salas, canal, motor, LiquidacionSinApuesta.nueva(),
                    RecompensaSinLibro.nueva(), null, Clock.fixed(ahora, ZoneOffset.UTC), () -> segundos);
        }

        @Test
        @DisplayName("sin valor no hay limite: el turno no vence")
        void sinValorNoVence() {
            Partida partida = partidaDe(ANA, BRUNO);

            Partida despues = conTiempo(null, AHORA).ejecutar(partida.id(), ANA, BRUNO, null);

            assertNull(despues.turnoVenceEn());
        }

        @Test
        @DisplayName("con valor, el turno siguiente vence a esa distancia")
        void conValorVence() {
            Partida partida = partidaDe(ANA, BRUNO);

            Partida despues = conTiempo(30, AHORA).ejecutar(partida.id(), ANA, BRUNO, null);

            assertEquals(AHORA.plusSeconds(30), despues.turnoVenceEn());
        }

        @Test
        @DisplayName("un turno agotado pasa sin accion, con motivo TIEMPO_AGOTADO")
        void turnoAgotado() {
            Partida partida = partidaDe(ANA, BRUNO);
            partida.fijarVencimientoDelTurno(AHORA.minusSeconds(1));
            partidas.guardar(partida);

            Partida despues = conTiempo(30, AHORA).agotarTurno(partida.id()).orElseThrow();

            assertAll(
                    () -> assertEquals(BRUNO, despues.turnoActual().idJugador()),
                    () -> assertEquals(List.of("TIEMPO_AGOTADO"), canal.motivos),
                    () -> assertTrue(motor.acciones.isEmpty(), "nadie jugo"));
        }

        @Test
        @DisplayName("un turno que todavia no vence, o sin limite, no se toca")
        void turnoVigente() {
            Partida partida = partidaDe(ANA, BRUNO);
            assertTrue(conTiempo(30, AHORA).agotarTurno(partida.id()).isEmpty(), "sin limite");

            partida = enBase(partida);
            partida.fijarVencimientoDelTurno(AHORA.plusSeconds(10));
            partidas.guardar(partida);
            assertTrue(conTiempo(30, AHORA).agotarTurno(partida.id()).isEmpty(), "aun no vence");
            assertTrue(conTiempo(30, AHORA).agotarTurno(UUID.randomUUID()).isEmpty(), "no existe");
        }
    }

    /* HU-JUE-014, CA-04: el fin lleva el reparto de la apuesta. */
    @Nested
    @DisplayName("con apuesta en juego (HU-JUE-014)")
    class ConApuesta {

        private final CreditosEnMemoria libro = new CreditosEnMemoria().conSaldo(ANA, 1_000).conSaldo(BRUNO, 1_000);

        private final AcreditadorEnMemoria libroDeRecompensas = new AcreditadorEnMemoria();

        private EjecutarAccion casoDeUsoConApuesta() {
            LiquidarApuesta liquidar = new LiquidarApuesta(salas, new RepositorioDeLiquidacionesEnMemoria(), libro,
                    Clock.fixed(AHORA, ZoneOffset.UTC), LiquidarApuesta.SiGanaLaMaquina.LIBERAR);
            AcreditarRecompensa recompensa = new AcreditarRecompensa(salas, new RepositorioDeRecompensasEnMemoria(),
                    libroDeRecompensas, new SancionesEnMemoria(), Clock.fixed(AHORA, ZoneOffset.UTC));
            return new EjecutarAccion(partidas, salas, canal, motor, liquidar, recompensa, null,
                    Clock.fixed(AHORA, ZoneOffset.UTC), () -> null);
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
            motor.dano = 50;
            return partidas.guardar(Partida.iniciar(sala, AHORA));
        }

        @Test
        @DisplayName("al terminar, el aviso de fin lleva el reparto y el libro ya movio los creditos")
        void elFinLlevaElReparto() {
            Partida partida = partidaApostada();

            casoDeUsoConApuesta().ejecutar(partida.id(), ANA, BRUNO, "ATAQUE_BASICO");

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
        @DisplayName("HU-JUE-012 CA-03: el mismo fin lleva la recompensa por jugar; dos combatientes es un 1 vs 1 (D-B7-16)")
        void elFinLlevaLaRecompensa() {
            Partida partida = partidaApostada();

            casoDeUsoConApuesta().ejecutar(partida.id(), ANA, BRUNO, "ATAQUE_BASICO");

            assertAll(
                    () -> assertEquals(1, libroDeRecompensas.informes.size(), "se informo una vez"),
                    () -> assertEquals(partida.id(), libroDeRecompensas.informes.get(0).idPartida()),
                    () -> assertEquals(List.of(ANA), libroDeRecompensas.informes.get(0).ganadores()),
                    () -> assertEquals(List.of(
                                    new com.nexusbattles.plataforma.salaspartidas.dominio.CreditoPorPartida(ANA, 2, true, null),
                                    new com.nexusbattles.plataforma.salaspartidas.dominio.CreditoPorPartida(BRUNO, 1, false, null)),
                            canal.recompensas.get(0),
                            "una sala «hasta seis» con dos combatientes es un 1 vs 1: 2 al ganador"),
                    () -> assertEquals(2, canal.repartos.get(0).size(), "y el reparto de la apuesta sigue ahi"));
        }

        @Test
        @DisplayName("HU-JUE-012 CA-05: si solo falla la recompensa, la apuesta se liquida igual y el fin sale sin recompensa")
        void soloLaRecompensaCaida() {
            Partida partida = partidaApostada();
            libroDeRecompensas.caido = true;

            casoDeUsoConApuesta().ejecutar(partida.id(), ANA, BRUNO, "ATAQUE_BASICO");

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

            Partida despues = casoDeUsoConApuesta().ejecutar(partida.id(), ANA, BRUNO, "ATAQUE_BASICO");

            assertAll(
                    () -> assertEquals(EstadoPartida.FINALIZADA, despues.estado()),
                    () -> assertEquals(EstadoPartida.FINALIZADA, enBase(partida).estado(),
                            "el ultimo golpe quedo guardado"),
                    () -> assertEquals("fin", canal.anuncios.get(canal.anuncios.size() - 1).tipo()),
                    () -> assertTrue(canal.repartos.get(0).isEmpty(), "sin reparto: queda pendiente, no se inventa"),
                    () -> assertEquals(100, libro.reservadoDe(ANA), "nada se perdio: las reservas siguen vivas"));
        }
    }

    /**
     * Auditoria del 4-oct: «cuando yo ataco, tambien me hago dano a mi mismo».
     *
     * <p>El servidor nunca dirige un ataque contra quien lo lanza. Lo que el
     * jugador veia era el contragolpe de la maquina, que llega en la misma
     * rafaga, o un reflejo (Pinchos de escudo) anunciado sin su causa. Desde el
     * canal 1.7.0 cada aviso dice a quien apunto y por que cambio cada vida.
     */
    @Nested
    @DisplayName("sin auto-dano al atacar (auditoria del 4-oct)")
    class SinAutoDano {

        private Partida contraLaMaquina() {
            Sala sala = Sala.crear(new ParametrosDeSala(2, Modalidad.CONTRA_IA, 0, true, false, null), ANA,
                    new FichaDeParticipante("Ana", heroe("Arquero", 100)));
            return partidas.guardar(Partida.iniciar(sala, AHORA));
        }

        private UUID maquinaDe(Partida partida) {
            return partida.participantes().stream()
                    .filter(com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida::esIA)
                    .findFirst().orElseThrow().idJugador();
        }

        private AccionResuelta.Afectado afectado(AccionResuelta aviso, UUID id) {
            return aviso.afectados().stream().filter(a -> a.idJugador().equals(id)).findFirst().orElseThrow();
        }

        @Test
        @DisplayName("al atacar a la maquina baja la vida del objetivo y nunca la de quien ataca")
        void elAtaqueNoLeQuitaVidaAlAtacante() {
            Partida partida = contraLaMaquina();
            UUID maquina = maquinaDe(partida);
            motor.dano = 12;

            casoDeUso().ejecutar(partida.id(), ANA, maquina, MotorDeCombate.ATAQUE_BASICO);

            AccionResuelta golpe = canal.acciones.get(0);
            AccionResuelta.Afectado deAna = afectado(golpe, ANA);
            AccionResuelta.Afectado deLaMaquina = afectado(golpe, maquina);
            assertAll(
                    () -> assertEquals(ANA, golpe.idEjecutor()),
                    () -> assertEquals(maquina, golpe.idObjetivo(), "el aviso dice a quien apunto"),
                    () -> assertEquals(-12, deLaMaquina.diferencia()),
                    () -> assertEquals(List.of(new AccionResuelta.Causa("DANO", ANA, MotorDeCombate.ATAQUE_BASICO, 12)),
                            deLaMaquina.causas()),
                    () -> assertEquals(0, deAna.diferencia(), "Ana viaja por su poder, no porque se golpeara"),
                    () -> assertEquals(100, deAna.vidaActual()),
                    () -> assertTrue(deAna.causas().isEmpty(), "nada le cambio la vida a Ana con su golpe"));
        }

        @Test
        @DisplayName("el contragolpe de la maquina llega en su propio aviso, con la maquina como ejecutora")
        void elContragolpeEsDeLaMaquina() {
            Partida partida = contraLaMaquina();
            UUID maquina = maquinaDe(partida);
            motor.dano = 12;

            Partida despues = casoDeUso().ejecutar(partida.id(), ANA, maquina, MotorDeCombate.ATAQUE_BASICO);

            AccionResuelta contragolpe = canal.acciones.get(1);
            assertAll(
                    () -> assertEquals(List.of("accion", "turno", "accion", "turno"), canal.tipos(),
                            "tu golpe, su turno, su golpe, tu turno: dos avisos de accion distintos"),
                    () -> assertEquals(maquina, contragolpe.idEjecutor()),
                    () -> assertEquals(ANA, contragolpe.idObjetivo()),
                    () -> assertEquals(-12, afectado(contragolpe, ANA).diferencia()),
                    () -> assertEquals(List.of(new AccionResuelta.Causa("DANO", maquina, MotorDeCombate.ATAQUE_BASICO,
                            12)), afectado(contragolpe, ANA).causas(), "el dano de Ana lo causo la maquina"),
                    () -> assertEquals(88, despues.participante(ANA).orElseThrow().heroe().vidaActual()));
        }

        @Test
        @DisplayName("si el objetivo le devuelve dano (Pinchos de escudo), viaja como REFLEJO con quien lo causo")
        void elReflejoViajaConSuCausa() {
            Partida partida = partidaDe(ANA, BRUNO);
            motor.reflejo = 1;

            casoDeUso().ejecutar(partida.id(), ANA, BRUNO, MotorDeCombate.ATAQUE_BASICO);

            AccionResuelta.Afectado deAna = afectado(canal.acciones.get(0), ANA);
            assertAll(
                    () -> assertEquals(-1, deAna.diferencia()),
                    () -> assertEquals(List.of(new AccionResuelta.Causa("REFLEJO", BRUNO, "Pinchos de escudo", 1)),
                            deAna.causas(), "no es un golpe propio: se lo devolvio Bruno"),
                    () -> assertEquals(99, enBase(partida).participante(ANA).orElseThrow().heroe().vidaActual()));
        }

        @Test
        @DisplayName("un ataque basico contra uno mismo se rechaza sin preguntarle al motor")
        void contraUnoMismoNiSeLePreguntaAlMotor() {
            Partida partida = partidaDe(ANA, BRUNO);

            AccionNoPermitida rechazo = assertThrows(AccionNoPermitida.class,
                    () -> casoDeUso().ejecutar(partida.id(), ANA, ANA, MotorDeCombate.ATAQUE_BASICO));

            assertAll(
                    () -> assertEquals("OBJETIVO_INVALIDO", rechazo.motivo()),
                    () -> assertTrue(motor.acciones.isEmpty(), "ni se le pregunta al motor"),
                    () -> assertTrue(canal.anuncios.isEmpty()),
                    () -> assertEquals(100, enBase(partida).participante(ANA).orElseThrow().heroe().vidaActual()));
        }

        @Test
        @DisplayName("una accion que no es el ataque basico la decide el motor: una sanacion si puede ir a uno mismo")
        void otrasAccionesLasDecideElMotor() {
            Partida partida = partidaDe(ANA, BRUNO);

            assertThrows(AccionNoPermitida.class,
                    () -> casoDeUso().ejecutar(partida.id(), ANA, ANA, "Toque de la Vida"));

            assertEquals("Toque de la Vida " + ANA + "->" + ANA, motor.acciones.get(0),
                    "el doble trata todo como ataque y lo rechaza, pero la pregunta llego al motor");
        }
    }

    /**
     * RF-JUE-004 — HU-SAL-004: modo cooperativo. Equipos de dos: Ana y Bruno
     * contra Carla y Dario. Ana abre (orden de entrada).
     */
    @Nested
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
        @DisplayName("no se ataca a un companero: el motor lo rechaza diciendo que es de tu equipo")
        void noSeAtacaAlCompanero() {
            Partida partida = dosContraDos(100, 100);

            AccionNoPermitida rechazo = assertThrows(AccionNoPermitida.class,
                    () -> casoDeUso().ejecutar(partida.id(), ANA, BRUNO, null));

            assertAll(
                    () -> assertTrue(rechazo.getMessage().contains("equipo"), rechazo.getMessage()),
                    () -> assertEquals(100, enBase(partida).participantes().get(1).heroe().vidaActual(),
                            "Bruno intacto"),
                    () -> assertTrue(motor.acciones.isEmpty(),
                            "el ataque basico a un companero se rechaza antes de llegar al motor"));
        }

        @Test
        @DisplayName("con un solo rival en pie no hace falta apuntar, aunque el companero siga vivo")
        void unSoloRivalSeResuelveSolo() {
            Partida partida = dosContraDos(100, 100);
            partida.aplicarDano(DARIO, 100);
            partidas.guardar(partida);

            casoDeUso().ejecutar(partida.id(), ANA, null, null);

            assertEquals(MotorDeCombate.ATAQUE_BASICO + " " + ANA + "->null", motor.acciones.get(0));
            assertEquals(90, enBase(partida).participante(CARLA).orElseThrow().heroe().vidaActual());
        }

        @Test
        @DisplayName("el turno salta a quien ya cayo, sea del equipo que sea")
        void elTurnoSaltaALosCaidos() {
            Partida partida = dosContraDos(100, 100);
            partida.aplicarDano(BRUNO, 100);
            partidas.guardar(partida);

            Partida despues = casoDeUso().ejecutar(partida.id(), ANA, CARLA, null);

            assertEquals(CARLA, despues.turnoActual().idJugador(), "Bruno cayo: de Ana pasa a Carla");
        }

        @Test
        @DisplayName("termina cuando cae el ultimo del otro equipo, y gana el equipo entero (D-B7-15)")
        void ganaElEquipo() {
            Partida partida = dosContraDos(10, 100);
            partida.aplicarDano(DARIO, 100);
            partida.aplicarDano(BRUNO, 100);
            partidas.guardar(partida);
            motor.dano = 50;

            Partida despues = casoDeUso().ejecutar(partida.id(), ANA, CARLA, null);

            assertAll(
                    () -> assertEquals(EstadoPartida.FINALIZADA, despues.estado()),
                    () -> assertEquals(java.util.Optional.of(1), despues.equipoGanador()),
                    () -> assertEquals(List.of(ANA, BRUNO), despues.ganadores().stream()
                                    .map(com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida::idJugador)
                                    .toList(),
                            "Bruno cayo, pero su equipo gano"),
                    () -> assertEquals("fin", canal.anuncios.get(canal.anuncios.size() - 1).tipo()));
        }
    }
}
