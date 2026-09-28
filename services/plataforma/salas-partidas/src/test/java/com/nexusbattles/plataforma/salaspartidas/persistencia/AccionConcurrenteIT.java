package com.nexusbattles.plataforma.salaspartidas.persistencia;

import com.nexusbattles.plataforma.salaspartidas.aplicacion.EjecutarAccion;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.LiquidacionSinApuesta;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.MotorDeCombateSimulado;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.RecompensaSinLibro;
import com.nexusbattles.plataforma.salaspartidas.dominio.AccionResuelta;
import com.nexusbattles.plataforma.salaspartidas.dominio.CanalDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.InicioDeTurno;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.MotorDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.PartidaModificadaConcurrentemente;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepartoDeCreditos;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDePartidas;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.ResolucionDeAccion;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Dos acciones del MISMO turno a la vez, contra PostgreSQL de verdad — B7,
 * bloqueo optimista de la partida (V14).
 *
 * <p>Un doble clic o dos pestanas mandan la accion dos veces. Las dos
 * peticiones leen la partida con el turno del jugador, las dos pasan la
 * comprobacion de turno y las dos piden la accion al motor. Sin
 * {@code @Version} la segunda escritura pisaba a la primera y el jugador jugaba
 * DOS turnos seguidos. Con ella, entra una y la otra recibe
 * {@link PartidaModificadaConcurrentemente} sin aplicar nada.
 *
 * <p>Caso de uso real y adaptador JPA real. El unico andamio es un envoltorio
 * del repositorio que sincroniza a los dos hilos DESPUES de leer y ANTES de
 * escribir, para que la carrera ocurra siempre y no dependa del planificador
 * (mismo patron que {@link IngresoConcurrenteIT}). Sin transaccion de prueba:
 * cada escritura tiene que confirmarse de verdad para que la otra la vea.
 */
@Testcontainers
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Import({RepositorioPartidasJpa.class, RepositorioSalasJpa.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AccionConcurrenteIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final UUID ANA = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID BRUNO = UUID.fromString("77777777-7777-7777-7777-777777777777");

    @Autowired
    private RepositorioDePartidas partidas;

    @Autowired
    private RepositorioDeSalas salas;

    /** Uno contra uno con heroes reales; abre Ana (orden de entrada, para saber quien juega). */
    private Partida partidaGuardada() {
        Sala sala = Sala.crear(new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, false, false, null), ANA,
                new FichaDeParticipante("Ana", new HeroeDeCombate("h-a", "Muro", "Guerrero Tanque", null, 1, 44, 44,
                        11)));
        sala.unirse(BRUNO, new FichaDeParticipante("Bruno", new HeroeDeCombate("h-b", "Escarcha", "Mago Hielo",
                null, 1, 40, 40, 10)), null);
        sala.iniciarPartida(ANA);
        salas.guardar(sala);
        return partidas.guardar(Partida.iniciar(sala, Instant.parse("2026-09-27T12:00:00Z")));
    }

    @Test
    @DisplayName("dos acciones simultaneas del mismo turno: se aplica UNA, la otra sale partida-modificada")
    void dosAccionesUnTurno() throws Exception {
        Partida partida = partidaGuardada();
        long versionInicial = partida.version();

        CyclicBarrier ambosLeyeron = new CyclicBarrier(2);
        CanalConcurrente canal = new CanalConcurrente();
        MotorDeCombateSimulado simulado = new MotorDeCombateSimulado();
        EjecutarAccion ejecutar = new EjecutarAccion(new EsperaTrasLaPrimeraLectura(partidas, ambosLeyeron), canal,
                sincronizado(simulado), LiquidacionSinApuesta.nueva(), RecompensaSinLibro.nueva());

        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Future<Object> primera = hilos.submit(() -> intentar(ejecutar, partida.id()));
            Future<Object> segunda = hilos.submit(() -> intentar(ejecutar, partida.id()));
            List<Object> resultados = List.of(primera.get(30, TimeUnit.SECONDS), segunda.get(30, TimeUnit.SECONDS));

            long aplicadas = resultados.stream().filter(Partida.class::isInstance).count();
            long rechazadas = resultados.stream().filter(PartidaModificadaConcurrentemente.class::isInstance).count();
            Partida enBase = partidas.buscarPorId(partida.id()).orElseThrow();

            assertAll("una entra y la otra se rechaza sin aplicar nada",
                    () -> assertEquals(1, aplicadas, "resultados: " + resultados),
                    () -> assertEquals(1, rechazadas, "resultados: " + resultados),
                    () -> assertEquals(2, enBase.turnoActual().numeroTurno(), "UN solo turno jugado"),
                    () -> assertEquals(BRUNO, enBase.turnoActual().idJugador(), "el turno paso una vez, a Bruno"),
                    () -> assertEquals(40 - simulado.dano,
                            enBase.participante(BRUNO).orElseThrow().heroe().vidaActual(),
                            "Bruno recibio UN golpe, no dos"),
                    () -> assertEquals(versionInicial + 1, enBase.version(), "una sola escritura"));
            assertAll("solo se anuncia lo que quedo guardado",
                    () -> assertEquals(1, canal.acciones.size(), "acciones: " + canal.acciones),
                    () -> assertEquals(1, canal.turnos.size(), "turnos: " + canal.turnos));
            assertInstanceOf(PartidaModificadaConcurrentemente.class,
                    resultados.stream().filter(r -> !(r instanceof Partida)).findFirst().orElseThrow());
        } finally {
            hilos.shutdownNow();
        }
    }

    private static Object intentar(EjecutarAccion ejecutar, UUID idPartida) {
        try {
            return ejecutar.ejecutar(idPartida, ANA, null, MotorDeCombate.ATAQUE_BASICO);
        } catch (RuntimeException rechazo) {
            return rechazo;
        }
    }

    /** El doble del motor no es seguro entre hilos (anota en listas): se le pone un cerrojo. */
    private static MotorDeCombate sincronizado(MotorDeCombateSimulado simulado) {
        return new MotorDeCombate() {
            @Override
            public synchronized ResolucionDeAccion resolverAccion(String accion, UUID ejecutor, UUID objetivo,
                                                                  Partida partida) {
                return simulado.resolverAccion(accion, ejecutor, objetivo, partida);
            }

            @Override
            public synchronized InicioDeTurno iniciarTurno(UUID combatiente, Partida partida, boolean aVidaCompleta) {
                return simulado.iniciarTurno(combatiente, partida, aVidaCompleta);
            }
        };
    }

    /**
     * Repositorio real con un punto de encuentro: cada hilo, tras su PRIMERA
     * lectura, espera a que el otro tambien haya leido. Asi los dos parten de
     * la misma version de la partida.
     */
    private static final class EsperaTrasLaPrimeraLectura implements RepositorioDePartidas {
        private final RepositorioDePartidas real;
        private final CyclicBarrier barrera;
        private final ThreadLocal<Boolean> yaLeyo = ThreadLocal.withInitial(() -> false);

        EsperaTrasLaPrimeraLectura(RepositorioDePartidas real, CyclicBarrier barrera) {
            this.real = real;
            this.barrera = barrera;
        }

        @Override
        public Partida guardar(Partida partida) {
            return real.guardar(partida);
        }

        @Override
        public Optional<Partida> buscarPorId(UUID id) {
            Optional<Partida> leida = real.buscarPorId(id);
            if (!yaLeyo.get()) {
                yaLeyo.set(true);
                try {
                    barrera.await(20, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException("Los dos hilos no llegaron a leer a la vez", e);
                }
            }
            return leida;
        }

        @Override
        public Optional<Partida> buscarPorSala(UUID idSala) {
            return real.buscarPorSala(idSala);
        }
    }

    /** Canal que anota anuncios desde varios hilos. */
    private static final class CanalConcurrente implements CanalDePartida {
        final List<AccionResuelta> acciones = Collections.synchronizedList(new ArrayList<>());
        final List<UUID> turnos = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void anunciarAccionResuelta(AccionResuelta accion) {
            acciones.add(accion);
        }

        @Override
        public void anunciarInicio(Sala sala, Partida partida) {
            // no entra en la carrera
        }

        @Override
        public void anunciarTurno(Partida partida) {
            turnos.add(partida.turnoActual().idJugador());
        }

        @Override
        public void anunciarFin(Partida partida, List<RepartoDeCreditos> reparto) {
            // no entra en la carrera
        }
    }
}
