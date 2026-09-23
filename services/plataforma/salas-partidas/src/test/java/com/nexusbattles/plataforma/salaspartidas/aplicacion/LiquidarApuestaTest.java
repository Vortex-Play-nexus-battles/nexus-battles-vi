package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.LiquidacionDeApuesta;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepartoDeCreditos;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Liquidacion de la apuesta — HU-JUE-014, CA-04, CA-05 y CA-06.
 *
 * <p>Se afirma el estado del libro despues de liquidar: cuanto le queda a cada
 * uno. Las llamadas importan menos que el saldo.
 */
@DisplayName("LiquidarApuesta · HU-JUE-014")
class LiquidarApuestaTest {

    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRUNO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CARLA = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant AHORA = Instant.parse("2026-09-21T10:00:00Z");
    private static final int APUESTA = 100;

    private final RepositorioDeSalasEnMemoria salas = new RepositorioDeSalasEnMemoria();
    private final RepositorioDeLiquidacionesEnMemoria liquidaciones = new RepositorioDeLiquidacionesEnMemoria();
    private final CreditosEnMemoria libro = new CreditosEnMemoria()
            .conSaldo(ANA, 1_000).conSaldo(BRUNO, 1_000).conSaldo(CARLA, 1_000);

    private LiquidarApuesta liquidar(LiquidarApuesta.SiGanaLaMaquina politica) {
        return new LiquidarApuesta(salas, liquidaciones, libro,
                Clock.fixed(AHORA, ZoneOffset.UTC), politica);
    }

    private LiquidarApuesta liquidar() {
        return liquidar(LiquidarApuesta.SiGanaLaMaquina.LIBERAR);
    }

    private static HeroeDeCombate heroe(String nombre, int vida) {
        return new HeroeDeCombate("h-" + nombre, nombre, null, 5, vida, vida);
    }

    /** Sala con apuesta y cada participante con su reserva ya hecha en el libro. */
    private Sala salaApostada(boolean conIA, UUID... jugadores) {
        Modalidad modalidad = conIA ? Modalidad.CONTRA_IA : Modalidad.HASTA_SEIS;
        int maximo = conIA ? 2 : 6;
        Sala sala = Sala.crear(new ParametrosDeSala(maximo, modalidad, APUESTA, conIA, false, null),
                jugadores[0], new FichaDeParticipante("J0", heroe("H0", 100)));
        sala = sala.conReserva(libro.reservar(jugadores[0], APUESTA, sala.id(), 0).id());
        for (int i = 1; i < jugadores.length; i++) {
            UUID reserva = libro.reservar(jugadores[i], APUESTA, sala.id(), i).id();
            sala.unirse(jugadores[i], new FichaDeParticipante("J" + i, heroe("H" + i, 100), reserva), null);
        }
        return salas.guardar(sala);
    }

    /** Partida terminada en la que solo {@code enPie} conserva vida. */
    private static Partida terminadaCon(Sala sala, UUID enPie) {
        Partida partida = Partida.iniciar(sala, AHORA);
        for (ParticipanteDePartida p : partida.participantes()) {
            if (!p.idJugador().equals(enPie) && p.heroe() != null) {
                partida.aplicarDano(p.idJugador(), p.heroe().vidaActual());
            }
        }
        partida.terminarSiSoloQuedaUno();
        return partida;
    }

    /** Todos caidos: empate. */
    private static Partida terminadaSinNadieEnPie(Sala sala) {
        Partida partida = Partida.iniciar(sala, AHORA);
        for (ParticipanteDePartida p : partida.participantes()) {
            partida.aplicarDano(p.idJugador(), p.heroe().vidaActual());
        }
        partida.terminar();
        return partida;
    }

    private static Map<UUID, Integer> porJugador(List<RepartoDeCreditos> reparto) {
        return reparto.stream().collect(Collectors.toMap(RepartoDeCreditos::idJugador, RepartoDeCreditos::creditos));
    }

    @Nested
    @DisplayName("CA-04 · con ganador")
    class ConGanador {

        @Test
        @DisplayName("el ganador se lleva lo de los demas y recupera lo suyo; los demas pierden su apuesta")
        void elGanadorSeLlevaTodo() {
            Sala sala = salaApostada(false, ANA, BRUNO, CARLA);
            Partida partida = terminadaCon(sala, BRUNO);

            List<RepartoDeCreditos> reparto = liquidar().alTerminar(partida);

            assertAll(
                    () -> assertEquals(1_200, libro.saldoDe(BRUNO), "gana las dos apuestas ajenas"),
                    () -> assertEquals(900, libro.saldoDe(ANA)),
                    () -> assertEquals(900, libro.saldoDe(CARLA)),
                    () -> assertEquals(0, libro.reservadoDe(ANA) + libro.reservadoDe(BRUNO) + libro.reservadoDe(CARLA),
                            "no queda nada retenido"),
                    () -> assertEquals(Map.of(ANA, -100, BRUNO, 200, CARLA, -100), porJugador(reparto)),
                    () -> assertEquals(LiquidacionDeApuesta.Estado.LIQUIDADA,
                            liquidaciones.buscarPorPartida(partida.id()).orElseThrow().estado()));
        }

        @Test
        @DisplayName("liquidar dos veces la misma partida no cobra dos veces")
        void idempotente() {
            Sala sala = salaApostada(false, ANA, BRUNO);
            Partida partida = terminadaCon(sala, ANA);
            LiquidarApuesta liquidar = liquidar();

            List<RepartoDeCreditos> primera = liquidar.alTerminar(partida);
            List<RepartoDeCreditos> segunda = liquidar.alTerminar(partida);

            assertAll(
                    () -> assertEquals(1_100, libro.saldoDe(ANA)),
                    () -> assertEquals(900, libro.saldoDe(BRUNO)),
                    () -> assertEquals(primera, segunda),
                    () -> assertEquals(1, liquidaciones.buscarPorPartida(partida.id()).orElseThrow().intentos(),
                            "la segunda vez ni se intenta: ya estaba liquidada"));
        }
    }

    @Test
    @DisplayName("CA-04 · en empate se devuelve todo y el reparto es cero para todos")
    void empate() {
        Sala sala = salaApostada(false, ANA, BRUNO);
        Partida partida = terminadaSinNadieEnPie(sala);

        List<RepartoDeCreditos> reparto = liquidar().alTerminar(partida);

        assertAll(
                () -> assertEquals(1_000, libro.saldoDe(ANA)),
                () -> assertEquals(1_000, libro.saldoDe(BRUNO)),
                () -> assertEquals(0, libro.reservadoDe(ANA) + libro.reservadoDe(BRUNO)),
                () -> assertEquals(Map.of(ANA, 0, BRUNO, 0), porJugador(reparto)));
    }

    @Test
    @DisplayName("CA-05 · sin apuesta no se toca el libro ni se anota nada")
    void sinApuesta() {
        Sala sala = salas.guardar(Sala.crear(
                new ParametrosDeSala(6, Modalidad.HASTA_SEIS, 0, false, false, null), ANA,
                new FichaDeParticipante("Ana", heroe("A", 100))));
        sala.unirse(BRUNO, new FichaDeParticipante("Bruno", heroe("B", 100)), null);
        Partida partida = terminadaCon(sala, ANA);

        List<RepartoDeCreditos> reparto = liquidar().alTerminar(partida);

        assertAll(
                () -> assertTrue(reparto.isEmpty()),
                () -> assertTrue(libro.llamadas.isEmpty()),
                () -> assertTrue(liquidaciones.filas.isEmpty()));
    }

    @Test
    @DisplayName("solo se liquida una partida terminada")
    void soloTerminadas() {
        Sala sala = salaApostada(false, ANA, BRUNO);
        Partida enCurso = Partida.iniciar(sala, AHORA);

        assertThrows(IllegalArgumentException.class, () -> liquidar().alTerminar(enCurso));
    }

    @Nested
    @DisplayName("gana la maquina (decision pendiente del PO, configurable)")
    class GanaLaMaquina {

        private Partida contraLaMaquinaPerdida() {
            Sala sala = salaApostada(true, ANA);
            Partida partida = Partida.iniciar(sala, AHORA);
            partida.aplicarDano(ANA, 100);
            partida.terminarSiSoloQuedaUno();
            assertTrue(partida.ganador().orElseThrow().esIA(), "la maquina quedo en pie");
            return partida;
        }

        @Test
        @DisplayName("por defecto se devuelve lo apostado: nadie pierde contra la maquina")
        void liberar() {
            Partida partida = contraLaMaquinaPerdida();

            List<RepartoDeCreditos> reparto = liquidar(LiquidarApuesta.SiGanaLaMaquina.LIBERAR).alTerminar(partida);

            assertAll(
                    () -> assertEquals(1_000, libro.saldoDe(ANA)),
                    () -> assertEquals(0, libro.reservadoDe(ANA)),
                    () -> assertEquals(Map.of(ANA, 0), porJugador(reparto)));
        }

        @Test
        @DisplayName("con CONSUMIR la casa se queda con la apuesta")
        void consumir() {
            Partida partida = contraLaMaquinaPerdida();

            List<RepartoDeCreditos> reparto = liquidar(LiquidarApuesta.SiGanaLaMaquina.CONSUMIR).alTerminar(partida);

            assertAll(
                    () -> assertEquals(900, libro.saldoDe(ANA)),
                    () -> assertEquals(0, libro.reservadoDe(ANA)),
                    () -> assertEquals(Map.of(ANA, -100), porJugador(reparto)));
        }

        /**
         * D-02 es un parametro, no una constante de arranque: desde R12 su valor
         * sale del catalogo de admin-parametros. Si se leyera una sola vez al
         * construir el caso de uso, cambiarlo exigiria reiniciar el servicio y
         * seria configurable solo de nombre.
         */
        @Test
        @DisplayName("la politica se pregunta en cada liquidacion, no se fija al arrancar")
        void laPoliticaSeLeeEnCadaLiquidacion() {
            AtomicReference<LiquidarApuesta.SiGanaLaMaquina> politica =
                    new AtomicReference<>(LiquidarApuesta.SiGanaLaMaquina.LIBERAR);
            LiquidarApuesta liquidar = new LiquidarApuesta(salas, liquidaciones, libro,
                    Clock.fixed(AHORA, ZoneOffset.UTC), politica::get);

            liquidar.alTerminar(contraLaMaquinaPerdida());
            assertEquals(1_000, libro.saldoDe(ANA), "con LIBERAR se devuelve lo apostado");

            // El PO cambia el parametro. El MISMO objeto tiene que obedecer.
            politica.set(LiquidarApuesta.SiGanaLaMaquina.CONSUMIR);
            liquidar.alTerminar(contraLaMaquinaPerdida());
            assertEquals(900, libro.saldoDe(ANA), "con CONSUMIR la casa se queda con la apuesta");
        }

        @Test
        @DisplayName("un proveedor que no da politica no rompe la liquidacion: se libera")
        void sinPoliticaSeLibera() {
            LiquidarApuesta liquidar = new LiquidarApuesta(salas, liquidaciones, libro,
                    Clock.fixed(AHORA, ZoneOffset.UTC), () -> null);

            List<RepartoDeCreditos> reparto = liquidar.alTerminar(contraLaMaquinaPerdida());

            assertAll(
                    () -> assertEquals(1_000, libro.saldoDe(ANA)),
                    () -> assertEquals(Map.of(ANA, 0), porJugador(reparto)));
        }
    }

    @Nested
    @DisplayName("CA-06 · el libro no responde")
    class LibroCaido {

        @Test
        @DisplayName("la liquidacion queda PENDIENTE con el motivo, sin reparto, y nada se pierde")
        void quedaPendiente() {
            Sala sala = salaApostada(false, ANA, BRUNO);
            Partida partida = terminadaCon(sala, ANA);
            libro.caido = true;

            List<RepartoDeCreditos> reparto = liquidar().alTerminar(partida);

            LiquidacionDeApuesta pendiente = liquidaciones.buscarPorPartida(partida.id()).orElseThrow();
            assertAll(
                    () -> assertTrue(reparto.isEmpty(), "sin reparto: no se promete lo que no se cobro"),
                    () -> assertEquals(LiquidacionDeApuesta.Estado.PENDIENTE, pendiente.estado()),
                    () -> assertEquals(1, pendiente.intentos()),
                    () -> assertTrue(pendiente.ultimoError().contains("caido")),
                    () -> assertEquals(sala.id(), pendiente.idSala()),
                    () -> assertEquals(APUESTA, libro.reservadoDe(ANA), "las reservas siguen vivas"),
                    () -> assertEquals(APUESTA, libro.reservadoDe(BRUNO)));
        }

        @Test
        @DisplayName("el reintento la cierra cuando el libro vuelve, y devuelve el reparto")
        void elReintentoLaCierra() {
            Sala sala = salaApostada(false, ANA, BRUNO);
            Partida partida = terminadaCon(sala, ANA);
            libro.caido = true;
            LiquidarApuesta liquidar = liquidar();
            liquidar.alTerminar(partida);
            libro.caido = false;

            Optional<List<RepartoDeCreditos>> reparto = liquidar.reintentar(
                    liquidaciones.buscarPorPartida(partida.id()).orElseThrow(), partida);

            assertAll(
                    () -> assertEquals(Map.of(ANA, 100, BRUNO, -100), porJugador(reparto.orElseThrow())),
                    () -> assertEquals(1_100, libro.saldoDe(ANA)),
                    () -> assertEquals(900, libro.saldoDe(BRUNO)),
                    () -> assertEquals(LiquidacionDeApuesta.Estado.LIQUIDADA,
                            liquidaciones.buscarPorPartida(partida.id()).orElseThrow().estado()),
                    () -> assertEquals(2, liquidaciones.buscarPorPartida(partida.id()).orElseThrow().intentos()));
        }

        @Test
        @DisplayName("un fallo a mitad de liquidacion no cobra dos veces al reintentar")
        void aMitad() {
            Sala sala = salaApostada(false, ANA, BRUNO, CARLA);
            Partida partida = terminadaCon(sala, ANA);
            // El libro cobra la primera reserva ajena y se cae en la segunda.
            CreditosEnMemoria conFalloAMitad = new CreditosEnMemoria() {
                private int cobros;

                @Override
                public void consumir(UUID idReserva, UUID idBeneficiario) {
                    if (++cobros == 2) {
                        throw new com.nexusbattles.plataforma.salaspartidas.dominio.CreditosNoDisponibles("se cayo a mitad");
                    }
                    super.consumir(idReserva, idBeneficiario);
                }
            };
            conFalloAMitad.saldos.putAll(libro.saldos);
            conFalloAMitad.reservas.putAll(libro.reservas);
            LiquidarApuesta liquidar = new LiquidarApuesta(salas, liquidaciones, conFalloAMitad,
                    Clock.fixed(AHORA, ZoneOffset.UTC), LiquidarApuesta.SiGanaLaMaquina.LIBERAR);

            assertTrue(liquidar.alTerminar(partida).isEmpty(), "quedo pendiente");
            Optional<List<RepartoDeCreditos>> reparto = liquidar.reintentar(
                    liquidaciones.buscarPorPartida(partida.id()).orElseThrow(), partida);

            assertAll(
                    () -> assertTrue(reparto.isPresent()),
                    () -> assertEquals(1_200, conFalloAMitad.saldoDe(ANA), "exactamente dos apuestas, no tres"),
                    () -> assertEquals(900, conFalloAMitad.saldoDe(BRUNO)),
                    () -> assertEquals(900, conFalloAMitad.saldoDe(CARLA)));
        }

        @Test
        @DisplayName("si la sala de una pendiente ya no existe, se anota y no se inventa nada")
        void sinSala() {
            Sala sala = salaApostada(false, ANA, BRUNO);
            Partida partida = terminadaCon(sala, ANA);
            LiquidacionDeApuesta huerfana = LiquidacionDeApuesta.nueva(partida.id(), UUID.randomUUID(), AHORA);

            Optional<List<RepartoDeCreditos>> reparto = liquidar().reintentar(huerfana, partida);

            assertAll(
                    () -> assertTrue(reparto.isEmpty()),
                    () -> assertEquals(1_000, libro.saldoDe(ANA)));
        }
    }
}
