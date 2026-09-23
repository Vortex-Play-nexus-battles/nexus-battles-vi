package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosInvalidos;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeVinculosDeTorneo;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.VinculoDeTorneo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("InformarEncuentroDeTorneo · HU-TOR-004 CA-04: el resultado sale de la partida jugada")
class InformarEncuentroDeTorneoTest {

    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRUNO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TORNEO = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final Instant AHORA = Instant.parse("2026-09-21T10:00:00Z");

    /** Doble en memoria del puerto de persistencia. */
    static class VinculosEnMemoria implements RepositorioDeVinculosDeTorneo {
        final Map<UUID, VinculoDeTorneo> porSala = new HashMap<>();

        @Override
        public void guardar(VinculoDeTorneo vinculo) {
            porSala.put(vinculo.idSala(), vinculo);
        }

        @Override
        public Optional<VinculoDeTorneo> buscarPorSala(UUID idSala) {
            return Optional.ofNullable(porSala.get(idSala));
        }
    }

    /** Doble del arbitro: anota lo informado y, si se le pide, rechaza. */
    static class ArbitroEspia implements ArbitroDeTorneo {
        record Informe(UUID torneo, int numero, UUID ganador, UUID partida) { }
        final List<Informe> informes = new ArrayList<>();
        String rechazo;

        @Override
        public void informarGanador(UUID idTorneo, int numero, UUID ganadorUid, UUID idPartida) {
            if (rechazo != null) {
                throw new TorneoNoDisponible(rechazo);
            }
            informes.add(new Informe(idTorneo, numero, ganadorUid, idPartida));
        }
    }

    private final VinculosEnMemoria vinculos = new VinculosEnMemoria();
    private final ArbitroEspia arbitro = new ArbitroEspia();
    private final InformarEncuentroDeTorneo informar =
            new InformarEncuentroDeTorneo(vinculos, arbitro, Clock.fixed(AHORA, ZoneOffset.UTC));

    private static HeroeDeCombate heroe(String nombre) {
        return new HeroeDeCombate("h-" + nombre, nombre, null, 5, 100, 100);
    }

    private static Sala unoContraUno() {
        Sala sala = Sala.crear(new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, 0, false, null),
                ANA, new FichaDeParticipante("Ana", heroe("A")));
        sala.unirse(BRUNO, new FichaDeParticipante("Bruno", heroe("B")), null);
        return sala;
    }

    private static Partida terminadaGanando(Sala sala, UUID ganador) {
        Partida partida = Partida.iniciar(sala, AHORA);
        for (ParticipanteDePartida p : partida.participantes()) {
            if (!p.idJugador().equals(ganador) && p.heroe() != null) {
                partida.aplicarDano(p.idJugador(), p.heroe().vidaActual());
            }
        }
        partida.terminarSiSoloQuedaUno();
        return partida;
    }

    @Test
    @DisplayName("vincular guarda la sala como encuentro N del torneo, con quien lo vinculo y cuando")
    void vincula() {
        Sala sala = unoContraUno();

        VinculoDeTorneo vinculo = informar.vincular(sala.id(), TORNEO, 3, ANA);

        assertAll(
                () -> assertEquals(Optional.of(vinculo), informar.vinculoDe(sala.id())),
                () -> assertEquals(3, vinculo.numero()),
                () -> assertEquals(ANA, vinculo.vinculadoPor()),
                () -> assertEquals(AHORA, vinculo.vinculadoEn()),
                () -> assertFalse(vinculo.informado()));
    }

    @Test
    @DisplayName("el encuentro va del 1 al 14: fuera de eso es un 400 con el campo")
    void encuentroFueraDeRango() {
        Sala sala = unoContraUno();
        assertThrows(ParametrosInvalidos.class, () -> informar.vincular(sala.id(), TORNEO, 0, ANA));
        assertThrows(ParametrosInvalidos.class, () -> informar.vincular(sala.id(), TORNEO, 15, ANA));
        assertTrue(vinculos.porSala.isEmpty());
    }

    @Test
    @DisplayName("al terminar, el ganador humano se informa a torneos por uid y el vinculo queda informado")
    void informaAlGanador() {
        Sala sala = unoContraUno();
        informar.vincular(sala.id(), TORNEO, 1, ANA);
        Partida partida = terminadaGanando(sala, BRUNO);

        Optional<VinculoDeTorneo> resultado = informar.alTerminar(partida);

        assertAll(
                () -> assertEquals(1, arbitro.informes.size()),
                () -> assertEquals(new ArbitroEspia.Informe(TORNEO, 1, BRUNO, partida.id()), arbitro.informes.get(0)),
                () -> assertTrue(resultado.orElseThrow().informado()),
                () -> assertEquals(AHORA, resultado.orElseThrow().informadoEn()),
                () -> assertNull(resultado.orElseThrow().ultimoFallo()),
                () -> assertTrue(vinculos.porSala.get(sala.id()).informado(), "quedo guardado"));
    }

    @Test
    @DisplayName("una partida que no es encuentro no informa nada ni falla")
    void sinVinculoNoHaceNada() {
        Partida partida = terminadaGanando(unoContraUno(), ANA);

        assertAll(
                () -> assertTrue(informar.alTerminar(partida).isEmpty()),
                () -> assertTrue(arbitro.informes.isEmpty()));
    }

    @Test
    @DisplayName("una partida en curso no se informa: el resultado solo sale de la partida terminada")
    void enCursoNoInforma() {
        Sala sala = unoContraUno();
        informar.vincular(sala.id(), TORNEO, 1, ANA);

        Optional<VinculoDeTorneo> resultado = informar.alTerminar(Partida.iniciar(sala, AHORA));

        assertAll(
                () -> assertTrue(resultado.isEmpty()),
                () -> assertTrue(arbitro.informes.isEmpty()));
    }

    @Test
    @DisplayName("si torneos no responde, el fallo queda anotado en el vinculo (nunca en silencio) y no se pierde")
    void torneosNoResponde() {
        Sala sala = unoContraUno();
        informar.vincular(sala.id(), TORNEO, 2, ANA);
        arbitro.rechazo = "torneos respondio 503";

        VinculoDeTorneo resultado = informar.alTerminar(terminadaGanando(sala, ANA)).orElseThrow();

        assertAll(
                () -> assertFalse(resultado.informado()),
                () -> assertEquals("torneos respondio 503", resultado.ultimoFallo()),
                () -> assertEquals("torneos respondio 503", vinculos.porSala.get(sala.id()).ultimoFallo()));
    }

    @Test
    @DisplayName("un encuentro ya informado no se vuelve a informar")
    void noRepite() {
        Sala sala = unoContraUno();
        informar.vincular(sala.id(), TORNEO, 1, ANA);
        Partida partida = terminadaGanando(sala, ANA);
        informar.alTerminar(partida);

        informar.alTerminar(partida);

        assertEquals(1, arbitro.informes.size());
    }

    @Test
    @DisplayName("si gana la maquina no hay uid que mandar: queda anotado para el administrador")
    void ganaLaMaquina() {
        Sala sala = Sala.crear(new ParametrosDeSala(2, Modalidad.CONTRA_IA, 0, 1, false, null),
                ANA, new FichaDeParticipante("Ana", heroe("A")));
        informar.vincular(sala.id(), TORNEO, 1, ANA);
        Partida partida = Partida.iniciar(sala, AHORA);
        partida.aplicarDano(ANA, 100);
        partida.terminarSiSoloQuedaUno();

        VinculoDeTorneo resultado = informar.alTerminar(partida).orElseThrow();

        assertAll(
                () -> assertTrue(arbitro.informes.isEmpty()),
                () -> assertFalse(resultado.informado()),
                () -> assertTrue(resultado.ultimoFallo().contains("administrador")));
    }
}
