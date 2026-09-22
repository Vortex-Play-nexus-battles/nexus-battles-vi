package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.NoEsTuTurno;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.PartidaNoEncontrada;
import com.nexusbattles.plataforma.salaspartidas.dominio.PartidaYaTerminada;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El turno pasa de manos — RF-JUE-017.
 *
 * <p>Lo que se prueba es la puerta del turno: quien puede jugar, cuando, y que
 * un rechazo no mueva nada. El orden de rotacion en si ya esta probado en
 * {@code PartidaTest} y no se repite aqui.
 */
@DisplayName("AvanzarTurno · solo juega quien tiene el turno (RF-JUE-017)")
class AvanzarTurnoTest {

    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRUNO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant AHORA = Instant.parse("2026-09-18T12:00:00Z");

    private final RepositorioDePartidasEnMemoria partidas = new RepositorioDePartidasEnMemoria();
    private final CanalDePartidaEspia canal = new CanalDePartidaEspia();
    private final AvanzarTurno casoDeUso = new AvanzarTurno(partidas, canal);

    /** Ana y Bruno, en ese orden de turnos. Empieza Ana, que es la anfitriona. */
    private Partida partidaEnCurso() {
        Sala sala = Sala.crear(
                new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, false, false, null), ANA,
                new FichaDeParticipante("Ana",
                        new HeroeDeCombate("h-ana", "Arquero del Norte", null, 5, 120, 120)));
        sala.unirse(BRUNO,
                new FichaDeParticipante("Bruno",
                        new HeroeDeCombate("h-bruno", "Centinela", null, 3, 90, 90)),
                null);
        return partidas.guardar(Partida.iniciar(sala, AHORA));
    }

    @Test
    @DisplayName("quien tiene el turno lo juega y pasa al siguiente")
    void elTurnoPasaAlSiguiente() {
        Partida partida = partidaEnCurso();

        Partida despues = casoDeUso.ejecutar(partida.id(), ANA);

        assertAll(
                () -> assertEquals(BRUNO, despues.turnoActual().idJugador()),
                () -> assertEquals(2, despues.turnoActual().numeroTurno()));
    }

    @Test
    @DisplayName("el cambio se guarda antes de anunciarse")
    void guardaYAnuncia() {
        Partida partida = partidaEnCurso();

        casoDeUso.ejecutar(partida.id(), ANA);

        assertAll(
                () -> assertEquals(BRUNO, partidas.buscarPorId(partida.id()).orElseThrow()
                        .turnoActual().idJugador(), "la base ya tiene el turno nuevo"),
                () -> assertEquals(1, canal.anuncios.size()),
                () -> assertEquals("turno", canal.anuncios.get(0).tipo()));
    }

    @Test
    @DisplayName("jugar fuera de turno se rechaza y no mueve nada")
    void fueraDeTurnoNoMueveNada() {
        Partida partida = partidaEnCurso();

        assertThrows(NoEsTuTurno.class, () -> casoDeUso.ejecutar(partida.id(), BRUNO));

        assertAll(
                () -> assertEquals(ANA, partidas.buscarPorId(partida.id()).orElseThrow()
                        .turnoActual().idJugador(), "el turno sigue siendo de Ana"),
                () -> assertTrue(canal.anuncios.isEmpty(), "no se anuncia un turno que no cambio"));
    }

    @Test
    @DisplayName("un jugador que ni siquiera esta en la partida tampoco juega")
    void unExtranoNoJuega() {
        Partida partida = partidaEnCurso();

        assertThrows(NoEsTuTurno.class,
                () -> casoDeUso.ejecutar(partida.id(), UUID.randomUUID()));
    }

    @Test
    @DisplayName("una partida terminada se rechaza como terminada, no como turno ajeno")
    void terminadaSeRechazaComoTerminada() {
        // El orden de las comprobaciones importa: al reves, el ultimo en jugar
        // recibiria un «no es tu turno» que no explica nada, porque su turno SI
        // era.
        Partida partida = partidaEnCurso();
        partida.terminar();
        partidas.guardar(partida);

        assertThrows(PartidaYaTerminada.class, () -> casoDeUso.ejecutar(partida.id(), ANA));
    }

    @Test
    @DisplayName("una partida que no existe es 404")
    void partidaInexistente() {
        assertThrows(PartidaNoEncontrada.class,
                () -> casoDeUso.ejecutar(UUID.randomUUID(), ANA));
    }

    @Test
    @DisplayName("dos turnos seguidos rotan y vuelven al primero, sin perder la cuenta")
    void rotacionCompleta() {
        Partida partida = partidaEnCurso();

        casoDeUso.ejecutar(partida.id(), ANA);
        Partida despues = casoDeUso.ejecutar(partida.id(), BRUNO);

        assertAll(
                () -> assertEquals(ANA, despues.turnoActual().idJugador()),
                () -> assertEquals(3, despues.turnoActual().numeroTurno()),
                () -> assertEquals(EstadoPartida.EN_CURSO, despues.estado()),
                () -> assertEquals(2, canal.anuncios.size()));
    }
}
