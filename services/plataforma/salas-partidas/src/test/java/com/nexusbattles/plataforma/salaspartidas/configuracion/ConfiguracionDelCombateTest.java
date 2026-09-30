package com.nexusbattles.plataforma.salaspartidas.configuracion;

import com.nexusbattles.plataforma.resiliencia.parametros.LectorDeParametros;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.EjecutarAccion;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.PartidaModificadaConcurrentemente;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDePartidas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Turno;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tiempo por turno y paso de los turnos agotados — decision D-B7-14.
 *
 * <p>El documento no fija un tiempo por turno: el parametro
 * {@code salas.partidas.segundos-por-turno} nace sin valor y sin valor no hay
 * limite. Aqui se prueba la lectura del parametro y la tarea que pasa los
 * turnos agotados, que no puede pararse por una partida que falle.
 */
@DisplayName("ConfiguracionDelCombate · tiempo por turno (D-B7-14)")
class ConfiguracionDelCombateTest {

    private static final Instant AHORA = Instant.parse("2026-09-25T10:00:00Z");

    @Test
    @DisplayName("sin valor, cero o negativo no hay limite; con valor, esos segundos")
    void tiempoPorTurno() {
        assertAll(
                () -> assertNull(new ConfiguracionDelCombate.TiempoPorTurno(() -> 0).get()),
                () -> assertNull(new ConfiguracionDelCombate.TiempoPorTurno(() -> -5).get()),
                () -> assertEquals(30, new ConfiguracionDelCombate.TiempoPorTurno(() -> 30).get()),
                () -> assertEquals(Integer.MAX_VALUE,
                        new ConfiguracionDelCombate.TiempoPorTurno(() -> Long.MAX_VALUE).get()));
    }

    @Test
    @DisplayName("sin catalogo de parametros manda el respaldo de la variable de entorno")
    void sinCatalogoManda() {
        LectorDeParametros sinCatalogo = LectorDeParametros.soloRespaldo();
        ConfiguracionDelCombate configuracion = new ConfiguracionDelCombate();

        assertAll(
                () -> assertNull(configuracion.tiempoPorTurno(sinCatalogo, 0).get(), "0: sin limite"),
                () -> assertEquals(45, configuracion.tiempoPorTurno(sinCatalogo, 45).get()));
    }

    private static Partida vencida() {
        UUID uno = UUID.randomUUID();
        return Partida.rehidratar(UUID.randomUUID(), UUID.randomUUID(), EstadoPartida.EN_CURSO,
                List.of(ParticipanteDePartida.humano(uno, 0), ParticipanteDePartida.humano(UUID.randomUUID(), 0)),
                Turno.primero(uno), 0, AHORA, 3, null, null, AHORA.minusSeconds(1));
    }

    @Test
    @DisplayName("pasa cada turno agotado, y una partida que falla no para a las demas")
    void pasaLosTurnosAgotados() {
        Partida adelantada = vencida();
        Partida rota = vencida();
        Partida buena = vencida();
        RepositorioDePartidas partidas = new SoloVencidas(List.of(adelantada, rota, buena));
        EjecutarAccion ejecutar = mock(EjecutarAccion.class);
        when(ejecutar.agotarTurno(adelantada.id()))
                .thenThrow(new PartidaModificadaConcurrentemente(adelantada.id()));
        when(ejecutar.agotarTurno(rota.id())).thenThrow(new IllegalStateException("fila corrupta"));
        when(ejecutar.agotarTurno(buena.id())).thenReturn(Optional.of(buena));
        ConfiguracionDelCombate.VencimientoDeTurnos vencimiento = new ConfiguracionDelCombate()
                .vencimientoDeTurnos(partidas, ejecutar);

        assertDoesNotThrow(vencimiento::pasarTurnosAgotados);

        verify(ejecutar).agotarTurno(adelantada.id());
        verify(ejecutar).agotarTurno(rota.id());
        verify(ejecutar).agotarTurno(buena.id());
    }

    @Test
    @DisplayName("el reloj de la tarea es el del sistema: pregunta por los vencidos de AHORA")
    void preguntaConElRelojDelSistema() {
        List<Instant> preguntas = new java.util.ArrayList<>();
        RepositorioDePartidas partidas = new SoloVencidas(List.of()) {
            @Override
            public List<Partida> conTurnoVencido(Instant ahora) {
                preguntas.add(ahora);
                return List.of();
            }
        };
        ConfiguracionDelCombate.VencimientoDeTurnos vencimiento = new ConfiguracionDelCombate.VencimientoDeTurnos(
                partidas, mock(EjecutarAccion.class), Clock.fixed(AHORA, ZoneOffset.UTC));

        vencimiento.pasarTurnosAgotados();

        assertEquals(List.of(AHORA), preguntas);
    }

    /** Almacen que solo sabe contestar que partidas tienen el turno vencido. */
    private static class SoloVencidas implements RepositorioDePartidas {

        private final List<Partida> vencidas;

        SoloVencidas(List<Partida> vencidas) {
            this.vencidas = vencidas;
        }

        @Override
        public Partida guardar(Partida partida) {
            return partida;
        }

        @Override
        public Optional<Partida> buscarPorId(UUID id) {
            return vencidas.stream().filter(p -> p.id().equals(id)).findFirst();
        }

        @Override
        public Optional<Partida> buscarPorSala(UUID idSala) {
            return Optional.empty();
        }

        @Override
        public List<Partida> conTurnoVencido(Instant ahora) {
            return vencidas;
        }
    }
}
