package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.PartidaAjena;
import com.nexusbattles.plataforma.salaspartidas.dominio.PartidaNoEncontrada;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Estado del combate para pintar y para reconectar — RF-JUE-017. */
@DisplayName("ObtenerPartida · estado del combate (RF-JUE-017)")
class ObtenerPartidaTest {

    private static final UUID ANFITRION = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final RepositorioDePartidasEnMemoria partidas = new RepositorioDePartidasEnMemoria();
    private final ObtenerPartida casoDeUso = new ObtenerPartida(partidas);

    @Test
    @DisplayName("devuelve la partida guardada tal cual")
    void devuelveLaPartida() {
        Sala sala = Sala.crear(
                new ParametrosDeSala(2, Modalidad.CONTRA_IA, 0, true, false, null), ANFITRION);
        Partida guardada = partidas.guardar(
                Partida.iniciar(sala, Instant.parse("2026-09-17T20:00:00Z")));

        assertEquals(guardada.id(), casoDeUso.ejecutar(guardada.id()).id());
    }

    @Test
    @DisplayName("una partida que no existe es 404, no una vista en blanco")
    void partidaInexistente() {
        assertThrows(PartidaNoEncontrada.class, () -> casoDeUso.ejecutar(UUID.randomUUID()));
        assertThrows(PartidaNoEncontrada.class, () -> casoDeUso.ejecutar(UUID.randomUUID(), ANFITRION, false));
    }

    private Partida guardadaContraLaMaquina() {
        Sala sala = Sala.crear(
                new ParametrosDeSala(2, Modalidad.CONTRA_IA, 0, true, false, null), ANFITRION);
        return partidas.guardar(Partida.iniciar(sala, Instant.parse("2026-09-17T20:00:00Z")));
    }

    @Test
    @DisplayName("1.7.0: quien juega la partida la ve")
    void quienJuegaLaVe() {
        Partida guardada = guardadaContraLaMaquina();

        assertEquals(guardada.id(), casoDeUso.ejecutar(guardada.id(), ANFITRION, false).id());
    }

    @Test
    @DisplayName("1.7.0: un jugador que no la juega recibe 403 partida-ajena")
    void unAjenoNoLaVe() {
        Partida guardada = guardadaContraLaMaquina();

        PartidaAjena error = assertThrows(PartidaAjena.class,
                () -> casoDeUso.ejecutar(guardada.id(), UUID.randomUUID(), false));

        assertAll(
                () -> assertEquals(403, error.estado()),
                () -> assertEquals(PartidaAjena.TIPO, error.tipo()));
    }

    @Test
    @DisplayName("1.7.0: los roles de operacion la ven sin jugarla, para atender reportes")
    void operacionLaVe() {
        Partida guardada = guardadaContraLaMaquina();

        assertEquals(guardada.id(), casoDeUso.ejecutar(guardada.id(), UUID.randomUUID(), true).id());
    }
}
