package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.AccionNoPermitida;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.MotorDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.MotorNoDisponible;
import com.nexusbattles.plataforma.salaspartidas.dominio.OrdenDeTurnos;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.PerfilDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Turno;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La maquina juega su turno — HU-SAL-004 (#28), RF-JUE-004, §6.1.3 (B7).
 *
 * <p>Desde B7 con las mismas reglas que un humano: su accion pasa por el mismo
 * motor con {@code DECISION_DE_LA_MAQUINA}, que decide con una politica simple
 * y determinista (D-B7-12). Aqui se prueba que la maquina juega, cuando, y que
 * un fallo en su turno no rompe el del humano.
 */
@DisplayName("Turno de la maquina · la modalidad contra IA se puede jugar (HU-SAL-004)")
class TurnoDeLaMaquinaTest {

    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant AHORA = Instant.parse("2026-09-18T18:00:00Z");

    private final RepositorioDePartidasEnMemoria partidas = new RepositorioDePartidasEnMemoria();
    private final CanalDePartidaEspia canal = new CanalDePartidaEspia();
    private final MotorDeCombateSimulado motor = new MotorDeCombateSimulado();

    private static HeroeDeCombate arquero() {
        return new HeroeDeCombate("h-ana", "Arquero del Norte", "Guerrero Armas", null, 1, 100, 100, 11);
    }

    /** Sala contra la IA: Ana con su heroe, y la maquina con una copia. Ana abre. */
    private Partida contraLaMaquina() {
        Sala sala = Sala.crear(
                new ParametrosDeSala(2, Modalidad.CONTRA_IA, 0, true, false, null), ANA,
                new FichaDeParticipante("Ana", arquero()));
        return partidas.guardar(Partida.iniciar(sala, AHORA));
    }

    private EjecutarAccion casoDeUso() {
        return new EjecutarAccion(partidas, canal, motor, LiquidacionSinApuesta.nueva(), RecompensaSinLibro.nueva());
    }

    private static UUID maquinaDe(Partida partida) {
        return partida.participantes().stream().filter(ParticipanteDePartida::esIA).findFirst().orElseThrow()
                .idJugador();
    }

    // =====================================================================
    // El heroe de la maquina
    // =====================================================================

    @Test
    @DisplayName("sin heroe del catalogo, la maquina entra con una copia del del anfitrion a plena vida")
    void laMaquinaTieneHeroe() {
        Partida partida = contraLaMaquina();
        ParticipanteDePartida maquina = partida.participantes().get(1);

        assertAll(
                () -> assertTrue(maquina.esIA()),
                () -> assertNotNull(maquina.heroe(), "sin heroe no podria combatir"),
                () -> assertEquals("Arquero del Norte", maquina.heroe().nombre()),
                () -> assertEquals(100, maquina.heroe().vidaActual()),
                () -> assertEquals(0, maquina.creditosApostados(), "la maquina no apuesta"));
    }

    @Test
    @DisplayName("con heroe del catalogo, la maquina combate con el suyo (D-B7-11)")
    void laMaquinaConHeroeDelCatalogo() {
        Sala sala = Sala.crear(
                new ParametrosDeSala(2, Modalidad.CONTRA_IA, 0, true, false, null), ANA,
                new FichaDeParticipante("Ana", arquero()));
        HeroeDeCombate delCatalogo = HeroeDeCombate.aPleno("ia-1", "Mago Hielo", "Mago Hielo", 40, 10, null)
                .conPerfil(PerfilDeCombate.delCatalogo(1));

        Partida partida = Partida.iniciar(sala, AHORA, OrdenDeTurnos.DE_ENTRADA, List.of(delCatalogo));

        HeroeDeCombate deLaMaquina = partida.participantes().get(1).heroe();
        assertAll(
                () -> assertEquals("Mago Hielo", deLaMaquina.prototipo()),
                () -> assertEquals(40, deLaMaquina.vidaMaxima()),
                () -> assertEquals(1, deLaMaquina.nivelDeCombate()));
    }

    @Test
    @DisplayName("sin ficha del anfitrion la maquina se queda sin heroe, en vez de con uno inventado")
    void sinFichaNoHayHeroeParaLaMaquina() {
        Sala vieja = Sala.crear(
                new ParametrosDeSala(2, Modalidad.CONTRA_IA, 0, true, false, null), ANA);

        Partida partida = Partida.iniciar(vieja, AHORA);

        assertEquals(null, partida.participantes().get(1).heroe());
    }

    // =====================================================================
    // La maquina juega
    // =====================================================================

    @Test
    @DisplayName("tras la accion humana la maquina juega la suya, con la decision del motor")
    void laMaquinaRespondeSola() {
        motor.dano = 20;
        Partida partida = contraLaMaquina();

        Partida despues = casoDeUso().ejecutar(partida.id(), ANA, null, null);

        assertAll(
                () -> assertEquals(2, motor.acciones.size(), "jugaron los dos"),
                () -> assertTrue(motor.acciones.get(1).startsWith(MotorDeCombate.DECISION_DE_LA_MAQUINA + " "),
                        "la maquina no elige accion aqui: decide el motor, con sus mismas reglas"),
                () -> assertEquals(80, despues.participantes().get(0).heroe().vidaActual(),
                        "la maquina devolvio el golpe a Ana"),
                () -> assertEquals(80, despues.participantes().get(1).heroe().vidaActual()),
                () -> assertEquals(ANA, despues.turnoActual().idJugador(), "y el turno vuelve al humano"));
    }

    @Test
    @DisplayName("el combate de la maquina se anuncia igual que el de un humano")
    void laMaquinaAnunciaLoSuyo() {
        Partida partida = contraLaMaquina();

        casoDeUso().ejecutar(partida.id(), ANA, null, null);

        // accion (Ana) -> turno -> accion (maquina) -> turno.
        assertEquals(List.of("accion", "turno", "accion", "turno"), canal.tipos());
    }

    @Test
    @DisplayName("si Ana remata a la maquina, la partida termina antes de que responda")
    void anaRemata() {
        motor.dano = 100;
        Partida partida = contraLaMaquina();

        Partida despues = casoDeUso().ejecutar(partida.id(), ANA, null, null);

        assertAll(
                () -> assertEquals(EstadoPartida.FINALIZADA, despues.estado()),
                () -> assertEquals(ANA, despues.ganador().orElseThrow().idJugador()),
                () -> assertEquals(1, motor.acciones.size()));
    }

    @Test
    @DisplayName("el turno de la maquina se guarda: no vive solo en memoria")
    void loQueHaceLaMaquinaSePersiste() {
        motor.dano = 25;
        Partida partida = contraLaMaquina();

        casoDeUso().ejecutar(partida.id(), ANA, null, null);

        Partida enBase = partidas.buscarPorId(partida.id()).orElseThrow();
        assertAll(
                () -> assertEquals(75, enBase.participantes().get(0).heroe().vidaActual()),
                () -> assertEquals(75, enBase.participantes().get(1).heroe().vidaActual()),
                () -> assertEquals(3, enBase.turnoActual().numeroTurno()));
    }

    @Test
    @DisplayName("si el motor se cae en el turno de la maquina, ella pasa y el combate sigue")
    void motorCaidoEnElTurnoDeLaMaquina() {
        // No se propaga el 503: quien mando la accion ya la vio resuelta.
        motor.dano = 15;
        motor.mientrasResuelve = partida -> {
            if (motor.acciones.size() == 2) {
                throw new MotorNoDisponible("apagado");
            }
        };
        Partida partida = contraLaMaquina();

        Partida despues = casoDeUso().ejecutar(partida.id(), ANA, null, null);

        assertAll(
                () -> assertEquals(85, despues.participantes().get(1).heroe().vidaActual()),
                () -> assertEquals(100, despues.participantes().get(0).heroe().vidaActual()),
                () -> assertEquals(EstadoPartida.EN_CURSO, despues.estado()),
                () -> assertEquals(ANA, despues.turnoActual().idJugador()),
                () -> assertEquals("TURNO_PERDIDO", canal.motivos.get(canal.motivos.size() - 1)));
    }

    @Test
    @DisplayName("HU-DIS-003: si el motor esta degradado (corta circuitos) en el turno de la maquina, tambien pasa")
    void motorDegradadoEnElTurnoDeLaMaquina() {
        motor.dano = 15;
        motor.mientrasResuelve = partida -> {
            if (motor.acciones.size() == 2) {
                throw new com.nexusbattles.plataforma.resiliencia.DependenciaDegradada(
                        "motor-combate", "Motor de combate", null);
            }
        };
        Partida partida = contraLaMaquina();

        Partida despues = casoDeUso().ejecutar(partida.id(), ANA, null, null);

        assertAll(
                () -> assertEquals(85, despues.participantes().get(1).heroe().vidaActual()),
                () -> assertEquals(100, despues.participantes().get(0).heroe().vidaActual()),
                () -> assertEquals(ANA, despues.turnoActual().idJugador()));
    }

    @Test
    @DisplayName("si el motor rechaza la decision de la maquina, ella pasa el turno")
    void decisionRechazada() {
        motor.mientrasResuelve = partida -> {
            if (motor.acciones.size() == 2) {
                throw new AccionNoPermitida("EJECUTOR_CAIDO", "Sin vida no se actua.");
            }
        };
        Partida partida = contraLaMaquina();

        Partida despues = casoDeUso().ejecutar(partida.id(), ANA, null, null);

        assertEquals(ANA, despues.turnoActual().idJugador());
    }

    @Test
    @DisplayName("si el sorteo le da el primer turno a la maquina, juega al empezar")
    void laMaquinaAbre() {
        Partida iniciada = contraLaMaquina();
        UUID maquina = maquinaDe(iniciada);
        Partida abreLaMaquina = Partida.rehidratar(iniciada.id(), iniciada.idSala(), iniciada.estado(),
                iniciada.participantes(), Turno.primero(maquina), iniciada.recompensaEnJuego(),
                iniciada.iniciadaEn(), iniciada.version(), 7L, null, null);
        partidas.guardar(abreLaMaquina);

        casoDeUso().jugarLaMaquinaSiLeToca(iniciada.id());

        Partida despues = partidas.buscarPorId(iniciada.id()).orElseThrow();
        assertAll(
                () -> assertEquals(1, motor.acciones.size()),
                () -> assertTrue(motor.acciones.get(0).startsWith(MotorDeCombate.DECISION_DE_LA_MAQUINA + " " + maquina)),
                () -> assertEquals(ANA, despues.turnoActual().idJugador()),
                () -> assertEquals(90, despues.participante(ANA).orElseThrow().heroe().vidaActual()));
    }

    @Test
    @DisplayName("una maquina sin heroe pasa turno en vez de golpear a ciegas")
    void maquinaSinHeroePasaTurno() {
        Sala vieja = Sala.crear(
                new ParametrosDeSala(2, Modalidad.CONTRA_IA, 0, true, false, null), ANA);
        Partida partida = partidas.guardar(Partida.iniciar(vieja, AHORA));

        Partida despues = casoDeUso().ejecutar(partida.id(), ANA, null, null);

        assertAll(
                () -> assertTrue(motor.acciones.isEmpty(), "nadie tiene heroe: no se molesta al motor"),
                () -> assertEquals(EstadoPartida.EN_CURSO, despues.estado()));
    }

    @Test
    @DisplayName("el bucle de la maquina no se queda dando vueltas")
    void elBucleTieneGuarda() {
        motor.dano = 1;
        Partida partida = contraLaMaquina();

        Partida despues = casoDeUso().ejecutar(partida.id(), ANA, null, null);

        assertEquals(ANA, despues.turnoActual().idJugador());
    }
}
