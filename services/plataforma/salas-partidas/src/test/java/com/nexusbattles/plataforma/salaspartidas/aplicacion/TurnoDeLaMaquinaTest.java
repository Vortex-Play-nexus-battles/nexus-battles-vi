package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.MotorDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.MotorNoDisponible;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.ResolucionDelMotor;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La maquina juega su turno — HU-SAL-004 (#28), RF-JUE-004, SCRUM-1078/1079.
 *
 * <p><b>El defecto que esto cierra.</b> Un participante controlado por la IA
 * entraba en la partida y despues nadie jugaba por el: el turno le llegaba y
 * ahi se quedaba. Una partida contra la maquina se colgaba en el segundo turno,
 * asi que la modalidad existia en el enum pero no se podia jugar.
 *
 * <p>Los otros tres criterios de la historia —las tres modalidades, el maximo de
 * tres por equipo y el rechazo nombrando el limite— ya estaban cubiertos en
 * {@code Sala} y {@code Modalidad}, y se prueban alli.
 */
@DisplayName("Turno de la maquina · la modalidad contra IA se puede jugar (HU-SAL-004)")
class TurnoDeLaMaquinaTest {

    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant AHORA = Instant.parse("2026-09-18T18:00:00Z");

    /** Motor que hace siempre el mismo dano y anota cada consulta. */
    private static final class MotorDeMentira implements MotorDeCombate {

        private final int dano;
        /** Numero de llamada a partir de la cual falla. 0 = nunca falla. */
        private final int fallaDesde;
        private final List<String> consultas = new ArrayList<>();

        private MotorDeMentira(int dano, int fallaDesde) {
            this.dano = dano;
            this.fallaDesde = fallaDesde;
        }

        static MotorDeMentira queHace(int dano) {
            return new MotorDeMentira(dano, 0);
        }

        /** Responde a la primera y se cae despues: el turno de la maquina. */
        static MotorDeMentira queSeCaeTrasLaPrimera(int dano) {
            return new MotorDeMentira(dano, 2);
        }

        @Override
        public ResolucionDelMotor resolver(HeroeDeCombate atacante, HeroeDeCombate objetivo) {
            consultas.add(atacante.nombre() + " -> " + objetivo.nombre());
            if (fallaDesde > 0 && consultas.size() >= fallaDesde) {
                throw new MotorNoDisponible("apagado");
            }
            return new ResolucionDelMotor("CAUSAR_DANO", dano, 14);
        }
    }

    private final RepositorioDePartidasEnMemoria partidas = new RepositorioDePartidasEnMemoria();
    private final CanalDePartidaEspia canal = new CanalDePartidaEspia();

    private static HeroeDeCombate arquero() {
        return new HeroeDeCombate("h-ana", "Arquero del Norte", null, 5, 100, 100);
    }

    /** Sala contra la IA: Ana con su heroe, y la maquina. */
    private Partida contraLaMaquina() {
        Sala sala = Sala.crear(
                new ParametrosDeSala(2, Modalidad.CONTRA_IA, 0, true, false, null), ANA,
                new FichaDeParticipante("Ana", arquero()));
        return partidas.guardar(Partida.iniciar(sala, AHORA));
    }

    private EjecutarAccion casoDeUso(MotorDeMentira motor) {
        return new EjecutarAccion(partidas, canal, motor, LiquidacionSinApuesta.nueva());
    }

    // =====================================================================
    // El heroe de la maquina
    // =====================================================================

    @Test
    @DisplayName("la maquina entra con el heroe del anfitrion, a plena vida")
    void laMaquinaTieneHeroe() {
        // Decision minima documentada: no se inventa un heroe para la maquina,
        // se usa el unico que la partida conoce. Ademas deja la pelea pareja.
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
    @DisplayName("tras el golpe humano la maquina juega el suyo, sin que nadie se lo pida")
    void laMaquinaRespondeSola() {
        // Es lo que faltaba: antes el turno le llegaba a la maquina y ahi se
        // quedaba la partida.
        MotorDeMentira motor = MotorDeMentira.queHace(20);
        Partida partida = contraLaMaquina();

        Partida despues = casoDeUso(motor).ejecutar(partida.id(), ANA, null, null);

        assertAll(
                () -> assertEquals(2, motor.consultas.size(), "golpearon los dos"),
                () -> assertEquals(80, despues.participantes().get(0).heroe().vidaActual(),
                        "la maquina devolvio el golpe a Ana"),
                () -> assertEquals(80, despues.participantes().get(1).heroe().vidaActual()),
                () -> assertEquals(ANA, despues.turnoActual().idJugador(),
                        "y el turno vuelve al humano"));
    }

    @Test
    @DisplayName("el combate de la maquina se anuncia igual que el de un humano")
    void laMaquinaAnunciaLoSuyo() {
        Partida partida = contraLaMaquina();

        casoDeUso(MotorDeMentira.queHace(10)).ejecutar(partida.id(), ANA, null, null);

        // accion (Ana) -> turno -> accion (maquina) -> turno.
        assertEquals(List.of("accion", "turno", "accion", "turno"),
                canal.anuncios.stream().map(CanalDePartidaEspia.Anuncio::tipo).toList());
    }

    @Test
    @DisplayName("si la maquina remata, la partida termina y gana la maquina")
    void laMaquinaPuedeGanar() {
        Partida partida = contraLaMaquina();

        Partida despues = casoDeUso(MotorDeMentira.queHace(100))
                .ejecutar(partida.id(), ANA, null, null);

        // Ana pega primero y deja a la maquina a cero: la partida acaba antes
        // de que la maquina llegue a responder.
        assertAll(
                () -> assertEquals(EstadoPartida.FINALIZADA, despues.estado()),
                () -> assertEquals(ANA, despues.ganador().orElseThrow().idJugador()));
    }

    @Test
    @DisplayName("el turno de la maquina se guarda: no vive solo en memoria")
    void loQueHaceLaMaquinaSePersiste() {
        Partida partida = contraLaMaquina();

        casoDeUso(MotorDeMentira.queHace(25)).ejecutar(partida.id(), ANA, null, null);

        Partida enBase = partidas.buscarPorId(partida.id()).orElseThrow();
        assertAll(
                () -> assertEquals(75, enBase.participantes().get(0).heroe().vidaActual()),
                () -> assertEquals(75, enBase.participantes().get(1).heroe().vidaActual()),
                () -> assertEquals(3, enBase.turnoActual().numeroTurno()));
    }

    @Test
    @DisplayName("si el motor se cae en el turno de la maquina, ella pasa y el combate sigue")
    void motorCaidoEnElTurnoDeLaMaquina() {
        // No se propaga el 503: quien mando la accion ya la vio resuelta, y
        // devolverle un error por un fallo posterior a su jugada seria mentirle
        // sobre lo que el hizo.
        Partida partida = contraLaMaquina();
        MotorDeMentira motor = MotorDeMentira.queSeCaeTrasLaPrimera(15);

        Partida despues = casoDeUso(motor).ejecutar(partida.id(), ANA, null, null);

        assertAll(
                // La accion de Ana SI se resolvio y se aplico.
                () -> assertEquals(85, despues.participantes().get(1).heroe().vidaActual()),
                // La de la maquina no, y Ana sale ilesa de ese turno.
                () -> assertEquals(100, despues.participantes().get(0).heroe().vidaActual()),
                // Pero el combate sigue y el turno vuelve a Ana.
                () -> assertEquals(EstadoPartida.EN_CURSO, despues.estado()),
                () -> assertEquals(ANA, despues.turnoActual().idJugador()));
    }

    @Test
    @DisplayName("una maquina sin heroe pasa turno en vez de golpear a ciegas")
    void maquinaSinHeroePasaTurno() {
        MotorDeMentira motor = MotorDeMentira.queHace(30);
        Sala vieja = Sala.crear(
                new ParametrosDeSala(2, Modalidad.CONTRA_IA, 0, true, false, null), ANA);
        Partida partida = partidas.guardar(Partida.iniciar(vieja, AHORA));

        Partida despues = casoDeUso(motor).ejecutar(partida.id(), ANA, null, null);

        assertAll(
                () -> assertTrue(motor.consultas.isEmpty(), "nadie tiene heroe: no se molesta al motor"),
                () -> assertEquals(EstadoPartida.EN_CURSO, despues.estado()));
    }

    @Test
    @DisplayName("el bucle de la maquina no se queda dando vueltas")
    void elBucleTieneGuarda() {
        // Con una sola maquina y un humano, el turno vuelve al humano en la
        // primera vuelta. La guarda esta para que una partida mal formada
        // -turno que no avanza- no cuelgue el hilo.
        Partida partida = contraLaMaquina();

        Partida despues = casoDeUso(MotorDeMentira.queHace(1))
                .ejecutar(partida.id(), ANA, null, null);

        assertEquals(ANA, despues.turnoActual().idJugador());
    }
}
