package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.IngresoNoPermitido;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.NoEsElAnfitrion;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaNoEncontrada;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Arranque del combate — HU-SAL-004, RF-JUE-017.
 *
 * <p>Se prueba la coordinacion: quien puede, que se guarda, que se anuncia y en
 * que orden. Las reglas de si la sala puede empezar viven en {@code Sala} y se
 * prueban alli; aqui se comprueba que el caso de uso las respeta en vez de
 * duplicarlas.
 */
@DisplayName("IniciarPartida · arranque del combate (HU-SAL-004)")
class IniciarPartidaTest {

    private static final UUID ANFITRION = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID INVITADO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant AHORA = Instant.parse("2026-09-17T20:00:00Z");

    private final RepositorioDeSalasEnMemoria salas = new RepositorioDeSalasEnMemoria();
    private final RepositorioDePartidasEnMemoria partidas = new RepositorioDePartidasEnMemoria();
    private final CanalDePartidaEspia canal = new CanalDePartidaEspia();
    private final InventarioEnMemoria inventario = InventarioEnMemoria.conHeroe();

    /** Un identificador cualquiera, con el apodo que el inventario necesita. */
    private static JugadorAutenticado como(UUID id) {
        return new JugadorAutenticado(id, "jugador-" + id.toString().substring(0, 8));
    }

    private final IniciarPartida casoDeUso = new IniciarPartida(
            salas, partidas, canal, inventario, Clock.fixed(AHORA, ZoneOffset.UTC));

    private Sala salaConInvitado() {
        Sala sala = Sala.crear(
                new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, false, false, null), ANFITRION);
        sala.unirse(INVITADO);
        return salas.guardar(sala);
    }

    @Test
    @DisplayName("el anfitrion arranca el combate y la sala queda en juego")
    void arrancaYLaSalaQuedaEnJuego() {
        Sala sala = salaConInvitado();

        Partida partida = casoDeUso.ejecutar(sala.id(), como(ANFITRION));

        assertAll(
                () -> assertEquals(sala.id(), partida.idSala()),
                () -> assertEquals(AHORA, partida.iniciadaEn()),
                () -> assertEquals(EstadoSala.EN_JUEGO,
                        salas.buscarPorId(sala.id()).orElseThrow().estado()));
    }

    @Test
    @DisplayName("la partida queda guardada antes de anunciarse")
    void guardaAntesDeAnunciar() {
        Sala sala = salaConInvitado();

        Partida partida = casoDeUso.ejecutar(sala.id(), como(ANFITRION));

        assertAll(
                () -> assertTrue(partidas.buscarPorId(partida.id()).isPresent()),
                () -> assertEquals(1, canal.anuncios.size()),
                () -> assertEquals("inicio", canal.anuncios.get(0).tipo()),
                () -> assertEquals(partida.id(), canal.anuncios.get(0).partida().id()));
    }

    @Test
    @DisplayName("pulsar dos veces devuelve la misma partida, no un error ni una segunda")
    void esIdempotente() {
        Sala sala = salaConInvitado();

        Partida primera = casoDeUso.ejecutar(sala.id(), como(ANFITRION));
        Partida segunda = casoDeUso.ejecutar(sala.id(), como(ANFITRION));

        assertAll(
                () -> assertEquals(primera.id(), segunda.id()),
                // Y no se vuelve a anunciar: el canal ya lo dijo una vez.
                () -> assertEquals(1, canal.anuncios.size()));
    }

    @Test
    @DisplayName("un invitado no puede iniciar la partida de otro")
    void soloElAnfitrion() {
        Sala sala = salaConInvitado();

        assertThrows(NoEsElAnfitrion.class, () -> casoDeUso.ejecutar(sala.id(), como(INVITADO)));
    }

    @Test
    @DisplayName("una sala sin rival ni heroe de la IA no arranca")
    void sinRivalNoArranca() {
        Sala solo = salas.guardar(Sala.crear(
                new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, false, false, null), ANFITRION));

        assertThrows(IngresoNoPermitido.class, () -> casoDeUso.ejecutar(solo.id(), como(ANFITRION)));
    }

    @Test
    @DisplayName("una sala con heroe de la IA arranca aunque este sola")
    void conIaArrancaSola() {
        Sala conIa = salas.guardar(Sala.crear(
                new ParametrosDeSala(2, Modalidad.CONTRA_IA, 0, true, false, null), ANFITRION));

        Partida partida = casoDeUso.ejecutar(conIa.id(), como(ANFITRION));

        assertEquals(2, partida.participantes().size());
    }

    @Test
    @DisplayName("una sala que no existe es 404, y no se anuncia nada")
    void salaInexistente() {
        assertThrows(SalaNoEncontrada.class,
                () -> casoDeUso.ejecutar(UUID.randomUUID(), como(ANFITRION)));
        assertTrue(canal.anuncios.isEmpty());
    }

    @Test
    @DisplayName("si la sala rechaza empezar, no se guarda ninguna partida")
    void sinEfectosCuandoRechaza() {
        Sala solo = salas.guardar(Sala.crear(
                new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, false, false, null), ANFITRION));

        assertThrows(IngresoNoPermitido.class, () -> casoDeUso.ejecutar(solo.id(), como(ANFITRION)));
        assertTrue(partidas.buscarPorSala(solo.id()).isEmpty());
    }

    @Test
    @DisplayName("el primer turno es del anfitrion")
    void elPrimerTurnoEsDelAnfitrion() {
        Sala sala = salaConInvitado();

        Partida partida = casoDeUso.ejecutar(sala.id(), como(ANFITRION));

        assertEquals(ANFITRION, partida.turnoActual().idJugador());
    }
}
