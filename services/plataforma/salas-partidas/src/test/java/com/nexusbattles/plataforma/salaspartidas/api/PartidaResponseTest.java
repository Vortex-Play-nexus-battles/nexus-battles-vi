package com.nexusbattles.plataforma.salaspartidas.api;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El mapeo de la partida a lo que ve el navegador.
 *
 * <p><b>Por que existe.</b> Los mapeos de frontera son donde viven los fallos
 * que rompen una pantalla sin romper una prueba: el dominio esta perfecto, el
 * caso de uso esta perfecto, y lo que llega al cliente lleva un nulo donde no
 * toca. {@code HeroeResponse} tenia tres de sus cinco lineas sin cubrir, y la
 * que faltaba era justo la rama del nulo.
 *
 * <p>Esa rama no es teorica: una partida contra la maquina creada desde una sala
 * antigua —sin ficha del anfitrion— deja a la IA sin heroe, y eso tiene que
 * llegar al cliente como {@code heroe: null} y no como una excepcion.
 */
@DisplayName("PartidaResponse · lo que ve el navegador")
class PartidaResponseTest {

    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant AHORA = Instant.parse("2026-09-19T18:00:00Z");

    private static HeroeDeCombate arquero() {
        return new HeroeDeCombate("h-1", "Arquero del Norte", "/retratos/arquero.png", 5, 80, 100);
    }

    @Test
    @DisplayName("un heroe se copia campo a campo, retrato y nivel incluidos")
    void elHeroeSeCopiaEntero() {
        PartidaResponse.HeroeResponse respuesta =
                PartidaResponse.HeroeResponse.desde(arquero());

        assertAll(
                () -> assertEquals("h-1", respuesta.id()),
                () -> assertEquals("Arquero del Norte", respuesta.nombre()),
                () -> assertEquals("/retratos/arquero.png", respuesta.retratoUrl()),
                () -> assertEquals(5, respuesta.nivel()),
                () -> assertEquals(80, respuesta.vidaActual()),
                () -> assertEquals(100, respuesta.vidaMaxima()));
    }

    @Test
    @DisplayName("un participante sin heroe viaja como nulo, no como excepcion")
    void sinHeroeViajaNulo() {
        assertNull(PartidaResponse.HeroeResponse.desde(null));
    }

    @Test
    @DisplayName("la partida entera: el humano lleva su heroe y la maquina va marcada como IA")
    void laPartidaSeMapeaEntera() {
        Sala sala = Sala.crear(
                new ParametrosDeSala(2, Modalidad.CONTRA_IA, 50, true, false, null), ANA,
                new FichaDeParticipante("Ana", arquero()));
        Partida partida = Partida.iniciar(sala, AHORA);

        PartidaResponse respuesta = PartidaResponse.desde(partida);

        assertAll(
                () -> assertEquals(partida.id(), respuesta.id()),
                () -> assertEquals(sala.id(), respuesta.idSala()),
                () -> assertEquals(EstadoPartida.EN_CURSO, respuesta.estado()),
                () -> assertEquals(2, respuesta.participantes().size()),
                () -> assertEquals(AHORA, respuesta.iniciadaEn()),
                () -> assertEquals(ANA, respuesta.turnoActual().idJugador()),
                () -> assertEquals(1, respuesta.turnoActual().numeroTurno()),
                // El limite de tiempo lo impone el motor, fuera de este bloque:
                // el contrato lo declara anulable justamente por eso.
                () -> assertNull(respuesta.turnoActual().segundosRestantes()));

        PartidaResponse.ParticipanteResponse humano = respuesta.participantes().get(0);
        PartidaResponse.ParticipanteResponse maquina = respuesta.participantes().get(1);

        assertAll(
                () -> assertEquals(ANA, humano.jugador()),
                () -> assertNotNull(humano.heroe()),
                () -> assertEquals("Arquero del Norte", humano.heroe().nombre()),
                () -> assertTrue(humano.listo(), "quien esta en una partida iniciada, esta listo"),
                () -> assertTrue(maquina.esIA()),
                () -> assertEquals(0, maquina.creditosApostados(), "la maquina no apuesta"));
    }

    @Test
    @DisplayName("una sala antigua sin ficha deja a la maquina sin heroe, y se dice con un nulo")
    void laMaquinaSinFichaViajaSinHeroe() {
        Sala vieja = Sala.crear(
                new ParametrosDeSala(2, Modalidad.CONTRA_IA, 0, true, false, null), ANA);

        PartidaResponse respuesta = PartidaResponse.desde(Partida.iniciar(vieja, AHORA));

        assertNull(respuesta.participantes().get(1).heroe());
    }
}
