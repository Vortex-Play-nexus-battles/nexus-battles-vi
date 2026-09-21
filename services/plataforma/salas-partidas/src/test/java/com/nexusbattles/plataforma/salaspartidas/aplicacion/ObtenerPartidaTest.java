package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.PartidaNoEncontrada;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

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
    }
}
