package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.aplicacion.AcreditadorDePartidas.InformeDePartida;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.AcreditadorDePartidas.TipoDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.CreditoPorPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.RecompensaDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AcreditarRecompensa · HU-JUE-012")
class AcreditarRecompensaTest {

    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRUNO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CARLA = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID DARIO = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant AHORA = Instant.parse("2026-09-21T10:00:00Z");

    private final RepositorioDeSalasEnMemoria salas = new RepositorioDeSalasEnMemoria();
    private final RepositorioDeRecompensasEnMemoria recompensas = new RepositorioDeRecompensasEnMemoria();
    private final AcreditadorEnMemoria libro = new AcreditadorEnMemoria();
    private final SancionesEnMemoria sanciones = new SancionesEnMemoria();

    private final AcreditarRecompensa acreditar = new AcreditarRecompensa(salas, recompensas, libro, sanciones,
            Clock.fixed(AHORA, ZoneOffset.UTC));

    private static HeroeDeCombate heroe(String nombre) {
        return new HeroeDeCombate("h-" + nombre, nombre, null, 5, 100, 100);
    }

    private Sala sala(Modalidad modalidad, int maximo, int heroesIA, Integer tamanoEquipo, UUID... jugadores) {
        Sala sala = Sala.crear(new ParametrosDeSala(maximo, modalidad, 0, heroesIA, false, tamanoEquipo),
                jugadores[0], new FichaDeParticipante("J0", heroe("H0")));
        for (int i = 1; i < jugadores.length; i++) {
            sala.unirse(jugadores[i], new FichaDeParticipante("J" + i, heroe("H" + i)), null);
        }
        return salas.guardar(sala);
    }

    private Sala unoContraUno(UUID a, UUID b) {
        return sala(Modalidad.UNO_CONTRA_UNO, 2, 0, null, a, b);
    }

    /** Partida terminada en la que solo quedan en pie los indicados. */
    private static Partida terminadaCon(Sala sala, UUID... enPie) {
        List<UUID> vivos = List.of(enPie);
        Partida partida = Partida.iniciar(sala, AHORA);
        for (ParticipanteDePartida p : partida.participantes()) {
            if (!vivos.contains(p.idJugador()) && p.heroe() != null) {
                partida.aplicarDano(p.idJugador(), p.heroe().vidaActual());
            }
        }
        partida.terminarSiSoloQuedaUno();
        if (partida.estado() != com.nexusbattles.plataforma.salaspartidas.dominio.EstadoPartida.FINALIZADA) {
            partida.terminar();
        }
        return partida;
    }

    private static Map<UUID, Integer> porJugador(List<CreditoPorPartida> creditos) {
        return creditos.stream().collect(Collectors.toMap(CreditoPorPartida::idJugador, CreditoPorPartida::creditos));
    }

    @Nested
    @DisplayName("CA-01 · uno contra uno")
    class UnoContraUno {

        @Test
        @DisplayName("el ganador recibe 2 y el otro 1, y se anuncia tal cual lo acredito el libro")
        void ganadorDosParticipanteUno() {
            Partida partida = terminadaCon(unoContraUno(ANA, BRUNO), ANA);

            List<CreditoPorPartida> creditos = acreditar.alTerminar(partida);

            InformeDePartida informe = libro.informes.get(0);
            assertAll(
                    () -> assertEquals(Map.of(ANA, 2, BRUNO, 1), porJugador(creditos)),
                    () -> assertTrue(creditos.get(0).ganador()),
                    () -> assertFalse(creditos.get(1).ganador()),
                    () -> assertEquals(partida.id(), informe.idPartida()),
                    () -> assertEquals(TipoDePartida.UNO_A_UNO, informe.tipo()),
                    () -> assertEquals(List.of(ANA), informe.ganadores()),
                    () -> assertEquals(RecompensaDePartida.Estado.ACREDITADA,
                            recompensas.buscarPorPartida(partida.id()).orElseThrow().estado()));
        }

        @Test
        @DisplayName("en empate nadie gana: todos reciben lo de participar")
        void empate() {
            Partida partida = terminadaCon(unoContraUno(ANA, BRUNO));

            List<CreditoPorPartida> creditos = acreditar.alTerminar(partida);

            assertAll(
                    () -> assertEquals(Map.of(ANA, 1, BRUNO, 1), porJugador(creditos)),
                    () -> assertTrue(libro.informes.get(0).ganadores().isEmpty()));
        }

        @Test
        @DisplayName("solo se recompensa una partida terminada")
        void soloTerminadas() {
            Partida enCurso = Partida.iniciar(unoContraUno(ANA, BRUNO), AHORA);
            assertThrows(IllegalArgumentException.class, () -> acreditar.alTerminar(enCurso));
            assertTrue(libro.informes.isEmpty());
        }
    }

    @Nested
    @DisplayName("CA-02 · grupal")
    class Grupal {

        @Test
        @DisplayName("hasta seis: el que queda en pie recibe 4 y los demas 1")
        void todosContraTodos() {
            Partida partida = terminadaCon(sala(Modalidad.HASTA_SEIS, 6, 0, null, ANA, BRUNO, CARLA), CARLA);

            List<CreditoPorPartida> creditos = acreditar.alTerminar(partida);

            assertAll(
                    () -> assertEquals(Map.of(ANA, 1, BRUNO, 1, CARLA, 4), porJugador(creditos)),
                    () -> assertEquals(TipoDePartida.GRUPAL, libro.informes.get(0).tipo()));
        }

        @Test
        @DisplayName("por equipos: se informa a todo el equipo ganador como ganadores; la cifra la pone el libro")
        void porEquipos() {
            // Cuatro humanos en equipos de dos: el anfitrion abre el 1 y se completa
            // antes de abrir el 2 (Ana y Bruno contra Carla y Dario). Gana el equipo 1.
            Sala sala = sala(Modalidad.HASTA_SEIS, 4, 0, 2, ANA, BRUNO, CARLA, DARIO);
            Partida partida = terminadaCon(sala, ANA, BRUNO);

            List<CreditoPorPartida> creditos = acreditar.alTerminar(partida);

            InformeDePartida informe = libro.informes.get(0);
            assertAll(
                    () -> assertTrue(partida.equipoGanador().isPresent(), "la partida es por equipos"),
                    () -> assertEquals(List.of(ANA, BRUNO), informe.ganadores()),
                    () -> assertEquals(Map.of(ANA, 4, BRUNO, 4, CARLA, 1, DARIO, 1), porJugador(creditos)));
        }
    }

    @Nested
    @DisplayName("CA-06 · la maquina")
    class LaMaquina {

        @Test
        @DisplayName("contra la IA: la maquina no se informa; si gana el humano recibe 2 (es uno contra uno)")
        void ganaElHumano() {
            Sala sala = sala(Modalidad.CONTRA_IA, 2, 1, null, ANA);
            Partida partida = terminadaCon(sala, ANA);

            List<CreditoPorPartida> creditos = acreditar.alTerminar(partida);

            InformeDePartida informe = libro.informes.get(0);
            assertAll(
                    () -> assertEquals(Map.of(ANA, 2), porJugador(creditos)),
                    () -> assertEquals(TipoDePartida.UNO_A_UNO, informe.tipo()),
                    () -> assertEquals(1, informe.jugadores().size(), "solo el humano"),
                    () -> assertEquals(List.of(ANA), informe.ganadores()));
        }

        @Test
        @DisplayName("si gana la maquina, no hay ganadores: el humano recibe 1 por participar (D-18)")
        void ganaLaMaquina() {
            Sala sala = sala(Modalidad.CONTRA_IA, 2, 1, null, ANA);
            Partida partida = Partida.iniciar(sala, AHORA);
            partida.aplicarDano(ANA, 100);
            partida.terminarSiSoloQuedaUno();

            List<CreditoPorPartida> creditos = acreditar.alTerminar(partida);

            assertAll(
                    () -> assertEquals(Map.of(ANA, 1), porJugador(creditos)),
                    () -> assertTrue(libro.informes.get(0).ganadores().isEmpty(),
                            "la maquina no tiene cuenta: no se informa como ganadora"));
        }
    }

    @Nested
    @DisplayName("CA-04 · sancionados")
    class Sancionados {

        @Test
        @DisplayName("un sancionado se informa como tal y el libro lo excluye")
        void seInformaLaSancion() {
            sanciones.sancionado(BRUNO);
            Partida partida = terminadaCon(unoContraUno(ANA, BRUNO), ANA);

            List<CreditoPorPartida> creditos = acreditar.alTerminar(partida);

            InformeDePartida informe = libro.informes.get(0);
            assertAll(
                    () -> assertEquals(Map.of(ANA, 2), porJugador(creditos)),
                    () -> assertTrue(informe.jugadores().stream()
                            .anyMatch(j -> j.id().equals(BRUNO) && j.sancionado())),
                    () -> assertTrue(informe.jugadores().stream()
                            .anyMatch(j -> j.id().equals(ANA) && !j.sancionado())));
        }

        @Test
        @DisplayName("si sanciones no responde, no se asume «sin sancion»: queda pendiente sin tocar el libro")
        void sancionesCaido() {
            sanciones.caido = true;
            Partida partida = terminadaCon(unoContraUno(ANA, BRUNO), ANA);

            List<CreditoPorPartida> creditos = acreditar.alTerminar(partida);

            RecompensaDePartida pendiente = recompensas.buscarPorPartida(partida.id()).orElseThrow();
            assertAll(
                    () -> assertTrue(creditos.isEmpty()),
                    () -> assertTrue(libro.informes.isEmpty(), "no se informo nada a medias"),
                    () -> assertEquals(RecompensaDePartida.Estado.PENDIENTE, pendiente.estado()),
                    () -> assertEquals(1, pendiente.intentos()));
        }
    }

    @Nested
    @DisplayName("CA-05 · idempotencia y reintento")
    class Reintento {

        @Test
        @DisplayName("si el libro no responde, queda pendiente con el motivo y el fin sale sin recompensa")
        void quedaPendiente() {
            libro.caido = true;
            Partida partida = terminadaCon(unoContraUno(ANA, BRUNO), ANA);

            List<CreditoPorPartida> creditos = acreditar.alTerminar(partida);

            RecompensaDePartida pendiente = recompensas.buscarPorPartida(partida.id()).orElseThrow();
            assertAll(
                    () -> assertTrue(creditos.isEmpty()),
                    () -> assertEquals(RecompensaDePartida.Estado.PENDIENTE, pendiente.estado()),
                    () -> assertEquals(1, pendiente.intentos()),
                    () -> assertTrue(pendiente.ultimoError().contains("no responde")));
        }

        @Test
        @DisplayName("el reintento informa la misma partida y la cierra con lo acreditado")
        void elReintentoLaCierra() {
            libro.caido = true;
            Partida partida = terminadaCon(unoContraUno(ANA, BRUNO), ANA);
            acreditar.alTerminar(partida);
            libro.caido = false;

            Optional<List<CreditoPorPartida>> creditos = acreditar.reintentar(
                    recompensas.buscarPorPartida(partida.id()).orElseThrow(), partida);

            assertAll(
                    () -> assertEquals(Map.of(ANA, 2, BRUNO, 1), porJugador(creditos.orElseThrow())),
                    () -> assertEquals(partida.id(), libro.informes.get(1).idPartida()),
                    () -> assertEquals(RecompensaDePartida.Estado.ACREDITADA,
                            recompensas.buscarPorPartida(partida.id()).orElseThrow().estado()),
                    () -> assertEquals(2, recompensas.buscarPorPartida(partida.id()).orElseThrow().intentos()));
        }

        @Test
        @DisplayName("si el libro ya la tenia (409), el reintento la da por acreditada sin anunciar de nuevo")
        void elLibroYaLaTenia() {
            Partida partida = terminadaCon(unoContraUno(ANA, BRUNO), ANA);
            libro.procesadas.add(partida.id()); // la primera si entro, aunque la respuesta se perdio
            recompensas.guardar(RecompensaDePartida.nueva(partida.id(), partida.idSala(), AHORA));

            Optional<List<CreditoPorPartida>> creditos = acreditar.reintentar(
                    recompensas.buscarPorPartida(partida.id()).orElseThrow(), partida);

            assertAll(
                    () -> assertTrue(creditos.isPresent(), "se cerro"),
                    () -> assertTrue(creditos.get().isEmpty(), "sin detalle nuevo que anunciar"),
                    () -> assertEquals(RecompensaDePartida.Estado.ACREDITADA,
                            recompensas.buscarPorPartida(partida.id()).orElseThrow().estado()));
        }

        @Test
        @DisplayName("repetir el aviso de fin de una partida ya acreditada no vuelve a informar al libro")
        void idempotente() {
            Partida partida = terminadaCon(unoContraUno(ANA, BRUNO), ANA);
            acreditar.alTerminar(partida);

            List<CreditoPorPartida> segundaVez = acreditar.alTerminar(partida);

            assertAll(
                    () -> assertTrue(segundaVez.isEmpty()),
                    () -> assertEquals(1, libro.informes.size()));
        }

        @Test
        @DisplayName("un rechazo del libro (4xx) tambien queda pendiente: es un desacuerdo que hay que ver, no tapar")
        void rechazo() {
            libro.rechaza = true;
            Partida partida = terminadaCon(unoContraUno(ANA, BRUNO), ANA);

            acreditar.alTerminar(partida);

            RecompensaDePartida pendiente = recompensas.buscarPorPartida(partida.id()).orElseThrow();
            assertAll(
                    () -> assertEquals(RecompensaDePartida.Estado.PENDIENTE, pendiente.estado()),
                    () -> assertTrue(pendiente.ultimoError().contains("400")));
        }
    }

    @Test
    @DisplayName("sin sala guardada, el tipo sale de cuantos jugaron: mas de dos es grupal")
    void sinSalaElTipoSaleDeLosJugadores() {
        Sala sala = sala(Modalidad.HASTA_SEIS, 6, 0, null, ANA, BRUNO, CARLA);
        Partida partida = terminadaCon(sala, ANA);
        AcreditarRecompensa sinSalas = new AcreditarRecompensa(new RepositorioDeSalasEnMemoria(), recompensas,
                libro, sanciones, Clock.fixed(AHORA, ZoneOffset.UTC));

        sinSalas.alTerminar(partida);

        assertEquals(TipoDePartida.GRUPAL, libro.informes.get(0).tipo());
    }
}
