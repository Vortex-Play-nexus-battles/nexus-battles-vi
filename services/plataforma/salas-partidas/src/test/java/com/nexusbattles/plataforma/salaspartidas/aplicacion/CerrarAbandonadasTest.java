package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.MotivoDeCancelacion;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Auditoria de DEV del 30-sep: 500 creditos apartados en una apuesta del 28 de
 * septiembre, sin sala visible ni forma de liberarlos. Una sala que nadie
 * empieza o una partida que nadie termina ya no retiene la apuesta para siempre.
 */
@DisplayName("CerrarAbandonadas · la apuesta de una sala abandonada vuelve a cada uno")
class CerrarAbandonadasTest {

    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRUNO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final int APUESTA = 500;
    private static final Duration PLAZO = Duration.ofHours(72);

    private final RepositorioDeSalasEnMemoria salas = new RepositorioDeSalasEnMemoria();
    private final RepositorioDePartidasEnMemoria partidas = new RepositorioDePartidasEnMemoria();
    private final RepositorioDeLiquidacionesEnMemoria liquidaciones = new RepositorioDeLiquidacionesEnMemoria();
    private final CreditosEnMemoria libro = new CreditosEnMemoria().conSaldo(ANA, 1_000).conSaldo(BRUNO, 1_000);
    private final CanalDeSalaEspia canalDeSala = new CanalDeSalaEspia();
    private final CanalDePartidaEspia canalDePartida = new CanalDePartidaEspia();

    /** El reloj del cierre: 73 h despues de ahora, asi lo creado ahora ya paso el plazo. */
    private final Instant dentroDe73Horas = Instant.now().plus(Duration.ofHours(73));

    private CerrarAbandonadas cierre(Instant ahora) {
        Clock reloj = Clock.fixed(ahora, ZoneOffset.UTC);
        LiquidarApuesta apuesta = new LiquidarApuesta(salas, liquidaciones, libro, reloj,
                LiquidarApuesta.SiGanaLaMaquina.LIBERAR);
        return new CerrarAbandonadas(salas, partidas, new CancelarSala(salas, libro, canalDeSala), apuesta,
                canalDePartida, reloj, PLAZO);
    }

    private static HeroeDeCombate heroe(String nombre) {
        return new HeroeDeCombate("h-" + nombre, nombre, null, 5, 100, 100);
    }

    /** Sala 1 contra 1 con la apuesta de los dos ya reservada en el libro. */
    private Sala salaApostadaLlena() {
        Sala sala = Sala.crear(new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, APUESTA, false, false, null),
                ANA, new FichaDeParticipante("ana", heroe("A")));
        sala = sala.conReserva(libro.reservar(ANA, APUESTA, sala.id(), 0).id());
        UUID reservaDeBruno = libro.reservar(BRUNO, APUESTA, sala.id(), 1).id();
        sala.unirse(BRUNO, new FichaDeParticipante("bruno", heroe("B"), reservaDeBruno), null);
        return salas.guardar(sala);
    }

    @Test
    @DisplayName("una sala que nadie empezo se cancela por INACTIVIDAD y devuelve la apuesta de cada participante")
    void salaSinEmpezar() {
        Sala sala = salaApostadaLlena();
        assertEquals(APUESTA, libro.reservadoDe(ANA));
        assertEquals(APUESTA, libro.reservadoDe(BRUNO));

        CerrarAbandonadas.Cerradas cerradas = cierre(dentroDe73Horas).ejecutar();

        assertEquals(1, cerradas.salas());
        assertEquals(EstadoSala.CANCELADA, salas.buscarPorId(sala.id()).orElseThrow().estado());
        assertEquals(0, libro.reservadoDe(ANA), "la apuesta de la anfitriona vuelve");
        assertEquals(0, libro.reservadoDe(BRUNO), "y la del invitado");
        assertEquals(List.of(MotivoDeCancelacion.INACTIVIDAD), canalDeSala.motivos());
    }

    @Test
    @DisplayName("una partida que nadie termino se da por terminada sin ganador: la apuesta se devuelve, no se reparte")
    void partidaSinTerminar() {
        Sala sala = salaApostadaLlena();
        sala.iniciarPartida(ANA);
        salas.guardar(sala);
        Partida partida = partidas.guardar(Partida.iniciar(sala, Instant.now()));

        CerrarAbandonadas.Cerradas cerradas = cierre(dentroDe73Horas).ejecutar();

        assertEquals(1, cerradas.partidas());
        assertEquals(EstadoPartida.FINALIZADA, partidas.buscarPorId(partida.id()).orElseThrow().estado());
        assertEquals(EstadoSala.FINALIZADA, salas.buscarPorId(sala.id()).orElseThrow().estado());
        assertEquals(1_000, libro.saldoDe(ANA), "nadie gano: nadie pierde lo suyo");
        assertEquals(0, libro.reservadoDe(ANA));
        assertEquals(0, libro.reservadoDe(BRUNO));
        assertEquals(List.of("fin"), canalDePartida.tipos());
        assertTrue(canalDePartida.recompensas.get(0).isEmpty(),
                "una partida abandonada no es una partida jugada: no hay recompensa por jugar");
    }

    @Test
    @DisplayName("lo que no ha pasado el plazo no se toca")
    void dentroDelPlazo() {
        Sala abierta = salaApostadaLlena();

        CerrarAbandonadas.Cerradas cerradas = cierre(Instant.now().plus(Duration.ofHours(71))).ejecutar();

        assertEquals(new CerrarAbandonadas.Cerradas(0, 0), cerradas);
        assertEquals(EstadoSala.LLENA, salas.buscarPorId(abierta.id()).orElseThrow().estado());
        assertEquals(APUESTA, libro.reservadoDe(ANA));
    }

    @Test
    @DisplayName("cerrar dos veces no hace nada la segunda: ni se cancela otra vez ni se libera dos veces")
    void idempotente() {
        salaApostadaLlena();
        CerrarAbandonadas cierre = cierre(dentroDe73Horas);

        cierre.ejecutar();
        CerrarAbandonadas.Cerradas segunda = cierre.ejecutar();

        assertEquals(new CerrarAbandonadas.Cerradas(0, 0), segunda);
        assertEquals(1, canalDeSala.motivos().size());
    }

    @Test
    @DisplayName("si el libro no responde, la sala igual se cierra y la reserva la recoge ms-finanzas al vencer")
    void libroCaido() {
        Sala sala = salaApostadaLlena();
        libro.fallaAlLiberar = true;

        CerrarAbandonadas.Cerradas cerradas = cierre(dentroDe73Horas).ejecutar();

        assertEquals(1, cerradas.salas());
        assertEquals(EstadoSala.CANCELADA, salas.buscarPorId(sala.id()).orElseThrow().estado());
    }

    @Test
    @DisplayName("un plazo que no dura nada no arranca el servicio")
    void plazoInvalido() {
        Clock reloj = Clock.systemUTC();
        LiquidarApuesta apuesta = new LiquidarApuesta(salas, liquidaciones, libro, reloj,
                LiquidarApuesta.SiGanaLaMaquina.LIBERAR);
        CancelarSala cancelar = new CancelarSala(salas, libro, canalDeSala);
        assertThrows(IllegalArgumentException.class, () -> new CerrarAbandonadas(salas, partidas, cancelar, apuesta,
                canalDePartida, reloj, Duration.ZERO));
    }
}
