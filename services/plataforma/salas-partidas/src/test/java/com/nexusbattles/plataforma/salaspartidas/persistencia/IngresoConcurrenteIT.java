package com.nexusbattles.plataforma.salaspartidas.persistencia;

import com.nexusbattles.plataforma.salaspartidas.aplicacion.IngresarASala;
import com.nexusbattles.plataforma.salaspartidas.dominio.CanalDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.IngresoNoPermitido;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.PaginaDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Carrera por el ultimo cupo, contra PostgreSQL de verdad — HU-SAL-002.
 *
 * <p>Dos jugadores leen la misma sala con un solo cupo libre y los dos intentan
 * guardarse dentro. Sin bloqueo optimista, la segunda escritura pisaba a la
 * primera (Hibernate reescribe la coleccion de participantes entera) y los dos
 * recibian 200 y un aviso por el canal, aunque la base solo conservara a uno.
 *
 * <p>Se ejercita el caso de uso real con el adaptador JPA real. El unico doble
 * es un envoltorio del repositorio que sincroniza a los dos hilos <b>despues de
 * leer y antes de escribir</b>, para que la carrera ocurra siempre y no dependa
 * de la suerte del planificador. Nada de mocks.
 *
 * <p>Sin transaccion de prueba a proposito ({@code NOT_SUPPORTED}): con la
 * transaccion que {@code @DataJpaTest} abre por defecto, los hilos no verian la
 * sala insertada por el hilo de la prueba y cada escritura del adaptador tiene
 * que confirmarse de verdad para que la siguiente la vea.
 */
@Testcontainers
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Import(RepositorioSalasJpa.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IngresoConcurrenteIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final UUID ANFITRION = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ANA = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID BRUNO = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Autowired
    private RepositorioDeSalas repositorio;

    @Test
    @DisplayName("dos jugadores compiten por el ultimo cupo: entra uno, el otro recibe sala llena, y solo se anuncia el que quedo")
    void dosJugadoresUnCupo() throws Exception {
        Sala sala = Sala.crear(
                new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, false, false, null), ANFITRION);
        repositorio.guardar(sala);

        CyclicBarrier ambosLeyeron = new CyclicBarrier(2);
        RepositorioDeSalas coordinado = new EsperaTrasLaPrimeraLectura(repositorio, ambosLeyeron);
        CanalConcurrente canal = new CanalConcurrente();
        IngresarASala ingresar = new IngresarASala(coordinado, canal);

        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Future<Object> deAna = hilos.submit(() -> intentar(ingresar, sala.id(), ANA));
            Future<Object> deBruno = hilos.submit(() -> intentar(ingresar, sala.id(), BRUNO));
            Object resultadoAna = deAna.get(30, TimeUnit.SECONDS);
            Object resultadoBruno = deBruno.get(30, TimeUnit.SECONDS);

            List<Object> resultados = List.of(resultadoAna, resultadoBruno);
            long entraron = resultados.stream().filter(Sala.class::isInstance).count();
            long rechazados = resultados.stream().filter(IngresoNoPermitido.class::isInstance).count();
            assertAll("exactamente uno entra y el otro es rechazado con motivo",
                    () -> assertEquals(1, entraron, "ganadores: " + resultados),
                    () -> assertEquals(1, rechazados, "perdedores: " + resultados));

            UUID ganador = resultadoAna instanceof Sala ? ANA : BRUNO;
            UUID perdedor = ganador.equals(ANA) ? BRUNO : ANA;
            IngresoNoPermitido rechazo = (IngresoNoPermitido) (resultadoAna instanceof Sala
                    ? resultadoBruno : resultadoAna);

            Sala enBase = repositorio.buscarPorId(sala.id()).orElseThrow();
            assertAll("la base conserva al ganador y solo al ganador",
                    () -> assertEquals(409, rechazo.estado()),
                    () -> assertTrue(rechazo.detalle().contains("maximo de participantes"),
                            "el motivo es el de sala llena, no un error tecnico: " + rechazo.detalle()),
                    () -> assertEquals(2, enBase.ocupacion(), "sin perdida de escritura"),
                    () -> assertEquals(EstadoSala.LLENA, enBase.estado()),
                    () -> assertTrue(enBase.participantes().contains(ANFITRION)),
                    () -> assertTrue(enBase.participantes().contains(ganador)),
                    () -> assertFalse(enBase.participantes().contains(perdedor)),
                    () -> assertEquals(1, enBase.version(), "una sola escritura tras la creacion"));

            assertAll("el canal solo anuncia a quien de verdad quedo dentro",
                    () -> assertEquals(1, canal.anuncios().size(), "anuncios: " + canal.anuncios()),
                    () -> assertEquals(ganador, canal.anuncios().get(0).idJugador()),
                    () -> assertEquals(2, canal.anuncios().get(0).ocupacion()));
        } finally {
            hilos.shutdownNow();
        }
    }

    @Test
    @DisplayName("una escritura con la version que otro ya avanzo no pisa nada: el adaptador la rechaza")
    void elAdaptadorNoPisaUnaEscrituraAdelantada() {
        Sala sala = Sala.crear(
                new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, false, false, null), ANFITRION);
        repositorio.guardar(sala);

        Sala lecturaDeAna = repositorio.buscarPorId(sala.id()).orElseThrow();
        Sala lecturaDeBruno = repositorio.buscarPorId(sala.id()).orElseThrow();

        lecturaDeAna.unirse(ANA);
        repositorio.guardar(lecturaDeAna); // version 0 -> 1

        lecturaDeBruno.unirse(BRUNO); // en memoria cabe; en la base ya no es la misma sala
        Object resultado = intentarGuardar(lecturaDeBruno);

        Sala enBase = repositorio.buscarPorId(sala.id()).orElseThrow();
        assertAll(
                () -> assertInstanceOf(
                        com.nexusbattles.plataforma.salaspartidas.dominio.SalaModificadaConcurrentemente.class,
                        resultado),
                () -> assertTrue(enBase.participantes().contains(ANA), "Ana sigue dentro"),
                () -> assertFalse(enBase.participantes().contains(BRUNO), "Bruno no piso a Ana"),
                () -> assertEquals(2, enBase.ocupacion()),
                () -> assertEquals(1, enBase.version()));
    }

    private static Object intentar(IngresarASala ingresar, UUID idSala, UUID idJugador) {
        try {
            return ingresar.ejecutar(idSala, idJugador);
        } catch (IngresoNoPermitido rechazo) {
            return rechazo;
        }
    }

    private Object intentarGuardar(Sala sala) {
        try {
            return repositorio.guardar(sala);
        } catch (RuntimeException error) {
            return error;
        }
    }

    /**
     * Repositorio real con un punto de encuentro: cada hilo, tras su PRIMERA
     * lectura, espera a que el otro tambien haya leido. Asi los dos parten de
     * la misma version de la sala y la carrera es segura de reproducir. Las
     * relecturas del reintento no esperan a nadie.
     */
    private static final class EsperaTrasLaPrimeraLectura implements RepositorioDeSalas {
        private final RepositorioDeSalas real;
        private final CyclicBarrier barrera;
        private final ThreadLocal<Boolean> yaLeyo = ThreadLocal.withInitial(() -> false);

        EsperaTrasLaPrimeraLectura(RepositorioDeSalas real, CyclicBarrier barrera) {
            this.real = real;
            this.barrera = barrera;
        }

        @Override
        public Sala guardar(Sala sala) {
            return real.guardar(sala);
        }

        @Override
        public Optional<Sala> buscarPorId(UUID id) {
            Optional<Sala> leida = real.buscarPorId(id);
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
        public PaginaDeSalas listar(Modalidad modalidad, EstadoSala estado, int pagina, int tamano) {
            return real.listar(modalidad, estado, pagina, tamano);
        }
    }

    /** Canal que anota anuncios desde varios hilos. */
    private static final class CanalConcurrente implements CanalDeSala {
        record Anuncio(UUID idJugador, int ocupacion) {
        }

        private final List<Anuncio> anuncios = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void anunciarIngreso(Sala sala, UUID idJugador) {
            anuncios.add(new Anuncio(idJugador, sala.ocupacion()));
        }

        List<Anuncio> anuncios() {
            return List.copyOf(anuncios);
        }
    }
}
