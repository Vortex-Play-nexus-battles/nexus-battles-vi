package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.LiquidacionDeApuesta;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepartoDeCreditos;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El reintento periodico cierra lo pendiente y vuelve a anunciar el fin con el reparto. */
@DisplayName("ReintentarLiquidaciones · HU-JUE-014 CA-06")
class ReintentarLiquidacionesTest {

    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRUNO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant AHORA = Instant.parse("2026-09-21T10:00:00Z");

    private final RepositorioDeSalasEnMemoria salas = new RepositorioDeSalasEnMemoria();
    private final RepositorioDePartidasEnMemoria partidas = new RepositorioDePartidasEnMemoria();
    private final RepositorioDeLiquidacionesEnMemoria liquidaciones = new RepositorioDeLiquidacionesEnMemoria();
    private final CreditosEnMemoria libro = new CreditosEnMemoria().conSaldo(ANA, 1_000).conSaldo(BRUNO, 1_000);
    private final CanalDePartidaEspia canal = new CanalDePartidaEspia();
    private final LiquidarApuesta liquidar = new LiquidarApuesta(salas, liquidaciones, libro,
            Clock.fixed(AHORA, ZoneOffset.UTC), LiquidarApuesta.SiGanaLaMaquina.LIBERAR);
    private final ReintentarLiquidaciones reintentar =
            new ReintentarLiquidaciones(liquidaciones, partidas, liquidar, canal);

    private static HeroeDeCombate heroe(String nombre) {
        return new HeroeDeCombate("h-" + nombre, nombre, null, 5, 100, 100);
    }

    /** Partida con apuesta que termino con el libro caido: queda pendiente. */
    private Partida pendienteGanadaPor(UUID ganador) {
        Sala sala = Sala.crear(new ParametrosDeSala(6, Modalidad.HASTA_SEIS, 100, false, false, null),
                ANA, new FichaDeParticipante("Ana", heroe("A")));
        sala = sala.conReserva(libro.reservar(ANA, 100, sala.id(), 0).id());
        sala.unirse(BRUNO, new FichaDeParticipante("Bruno", heroe("B"), libro.reservar(BRUNO, 100, sala.id(), 1).id()), null);
        salas.guardar(sala);
        Partida partida = Partida.iniciar(sala, AHORA);
        partida.aplicarDano(ganador.equals(ANA) ? BRUNO : ANA, 100);
        partida.terminarSiSoloQuedaUno();
        partidas.guardar(partida);
        libro.caido = true;
        assertTrue(liquidar.alTerminar(partida).isEmpty());
        libro.caido = false;
        return partida;
    }

    @Test
    @DisplayName("cierra la pendiente y vuelve a anunciar partida.finalizada con el reparto")
    void cierraYAnuncia() {
        Partida partida = pendienteGanadaPor(ANA);

        int cerradas = reintentar.ejecutar();

        assertAll(
                () -> assertEquals(1, cerradas),
                () -> assertEquals(LiquidacionDeApuesta.Estado.LIQUIDADA,
                        liquidaciones.buscarPorPartida(partida.id()).orElseThrow().estado()),
                () -> assertEquals(1_100, libro.saldoDe(ANA)),
                () -> assertEquals(1, canal.anuncios.size()),
                () -> assertEquals("fin", canal.anuncios.get(0).tipo()),
                () -> assertEquals(partida.id(), canal.anuncios.get(0).partida().id()),
                () -> assertEquals(List.of(new RepartoDeCreditos(ANA, 100), new RepartoDeCreditos(BRUNO, -100)),
                        canal.repartos.get(0)));
    }

    @Test
    @DisplayName("si el libro sigue caido, no anuncia nada y la deja para la siguiente vuelta")
    void sigueCaido() {
        Partida partida = pendienteGanadaPor(ANA);
        libro.caido = true;

        int cerradas = reintentar.ejecutar();

        LiquidacionDeApuesta pendiente = liquidaciones.buscarPorPartida(partida.id()).orElseThrow();
        assertAll(
                () -> assertEquals(0, cerradas),
                () -> assertEquals(LiquidacionDeApuesta.Estado.PENDIENTE, pendiente.estado()),
                () -> assertEquals(2, pendiente.intentos()),
                () -> assertTrue(canal.anuncios.isEmpty()));
    }

    @Test
    @DisplayName("una pendiente cuya partida no existe se anota y se salta, sin tumbar la vuelta")
    void partidaInexistente() {
        liquidaciones.guardar(LiquidacionDeApuesta.nueva(UUID.randomUUID(), UUID.randomUUID(), AHORA));
        Partida partida = pendienteGanadaPor(BRUNO);

        int cerradas = reintentar.ejecutar();

        assertAll(
                () -> assertEquals(1, cerradas, "la buena se cierra aunque la huerfana este antes"),
                () -> assertEquals(1_100, libro.saldoDe(BRUNO)),
                () -> assertEquals(partida.id(), canal.anuncios.get(0).partida().id()));
    }

    @Test
    @DisplayName("sin pendientes no hace nada")
    void sinPendientes() {
        assertEquals(0, reintentar.ejecutar());
        assertTrue(canal.anuncios.isEmpty());
    }
}
