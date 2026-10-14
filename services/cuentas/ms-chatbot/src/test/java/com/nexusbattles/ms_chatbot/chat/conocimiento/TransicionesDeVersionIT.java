package com.nexusbattles.ms_chatbot.chat.conocimiento;

import com.jayway.jsonpath.JsonPath;
import com.nexusbattles.ms_chatbot.chat.moderacion.ModeracionDeContenido;
import com.nexusbattles.ms_chatbot.chat.motor.MotorRespuestas;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ms-chatbot.yaml 1.6.0 (7.4.10) — las transiciones de version se hacen en
 * fila, contra PostgreSQL real con V1-V12 y la base publicada por V11.
 *
 * <p>Cada prueba fuerza la carrera, sin depender de la suerte: el despliegue
 * A se detiene a mitad de su evaluación (ya con produccion y la candidata
 * bloqueadas), la operación B arranca y queda esperando el bloqueo de la fila
 * en PostgreSQL ({@code pg_stat_activity}), y entonces A sigue y confirma.
 * Antes de 1.6.0, B terminaba en un 500 (el índice parcial
 * {@code uk_versiones_una_en_produccion} de V4 rechazaba la segunda
 * producción) o publicaba otra vez la misma candidata.
 */
@Testcontainers
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@DisplayName("Chatbot · transiciones de version en fila contra PostgreSQL (1.6.0)")
class TransicionesDeVersionIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private static final String RUTA = "/chatbot/admin/base-conocimiento";
    private static final long ESPERA_MAXIMA_S = 60;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EvaluacionBaseConocimientoService evaluacion;

    // El punto donde se detiene el despliegue A: el motor real, espiado.
    @MockitoSpyBean
    private MotorRespuestas motor;

    // La publicacion programada de verdad corre cada minuto: aqui no, para que
    // no se meta en la carrera que arma la prueba.
    @MockitoBean
    private DespliegueProgramadoTarea tareaProgramada;

    // La lista negra es otro servicio (no se usa aqui).
    @MockitoBean
    private ModeracionDeContenido moderacion;

    private final ExecutorService hilos = Executors.newFixedThreadPool(2);
    private final CountDownLatch aEnEvaluacion = new CountDownLatch(1);
    private final CountDownLatch aSigue = new CountDownLatch(1);

    @BeforeEach
    void prepararCandidataYDetenerElPrimerDespliegue() throws Exception {
        if (contar("BORRADOR") > 0) {
            mvc.perform(delete(RUTA + "/borrador").with(administrador())).andExpect(status().isNoContent());
        }
        mvc.perform(post(RUTA + "/borrador").with(administrador())).andExpect(status().isCreated());

        // La primera respuesta del motor (la evaluacion de A, ya con las filas
        // bloqueadas) espera a que B este esperando el bloqueo.
        AtomicBoolean primera = new AtomicBoolean(true);
        doAnswer(invocacion -> {
            if (primera.compareAndSet(true, false)) {
                aEnEvaluacion.countDown();
                if (!aSigue.await(ESPERA_MAXIMA_S, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("La prueba nunca dejo seguir al despliegue A.");
                }
            }
            return invocacion.callRealMethod();
        }).when(motor).responderCon(anyString(), anyList());
    }

    @AfterEach
    void cerrar() {
        aSigue.countDown();
        hilos.shutdownNow();
    }

    @Test
    @DisplayName("desplegar y revertir a la vez: queda una sola en produccion, la candidata, y la reversion recibe 409")
    void desplegarYRevertirALaVez() throws Exception {
        UUID produccionAntes = idEn("PRODUCCION");
        UUID candidata = idEn("BORRADOR");

        Future<MvcResult> despliegue = enOtroHilo(() -> mvc.perform(post(RUTA + "/borrador/despliegue")
            .with(administrador())).andReturn());
        esperar(aEnEvaluacion);
        Future<MvcResult> reversion = enOtroHilo(() -> mvc.perform(post(RUTA + "/produccion/reversion")
            .with(administrador())).andReturn());
        esperarAQueOtraTransaccionEspereElBloqueo();
        aSigue.countDown();

        MvcResult ganador = despliegue.get(ESPERA_MAXIMA_S, TimeUnit.SECONDS);
        MvcResult perdedor = reversion.get(ESPERA_MAXIMA_S, TimeUnit.SECONDS);

        assertThat(ganador.getResponse().getStatus()).isEqualTo(200);
        assertThat(perdedor.getResponse().getStatus()).isEqualTo(409);
        assertThat(motivo(perdedor)).isEqualTo("CAMBIO_CONCURRENTE");
        assertThat(contar("PRODUCCION")).isEqualTo(1);
        assertThat(idEn("PRODUCCION")).isEqualTo(candidata);
        assertThat(estadoDe(produccionAntes)).isEqualTo("RETIRADA");
        // Cada UPDATE de una version subio su `revision` (bloqueo optimista, V12).
        assertThat(revisionDe(candidata)).isPositive();
        assertThat(revisionDe(produccionAntes)).isPositive();

        // El 409 no deja nada trabado: revertir ahora, ya sin carrera, funciona
        // y vuelve exactamente a la version de antes.
        mvc.perform(post(RUTA + "/produccion/reversion").with(administrador())).andExpect(status().isOk());
        assertThat(contar("PRODUCCION")).isEqualTo(1);
        assertThat(idEn("PRODUCCION")).isEqualTo(produccionAntes);
    }

    @Test
    @DisplayName("dos despliegues a la vez: la candidata se publica una sola vez y el segundo recibe 409")
    void dosDesplieguesALaVez() throws Exception {
        UUID candidata = idEn("BORRADOR");
        int evaluacionesAntes = contarEvaluacionesDe(candidata);

        Future<MvcResult> primero = enOtroHilo(() -> mvc.perform(post(RUTA + "/borrador/despliegue")
            .with(administrador())).andReturn());
        esperar(aEnEvaluacion);
        Future<MvcResult> segundo = enOtroHilo(() -> mvc.perform(post(RUTA + "/borrador/despliegue")
            .with(administrador())).andReturn());
        esperarAQueOtraTransaccionEspereElBloqueo();
        aSigue.countDown();

        assertThat(primero.get(ESPERA_MAXIMA_S, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
        MvcResult perdedor = segundo.get(ESPERA_MAXIMA_S, TimeUnit.SECONDS);
        assertThat(perdedor.getResponse().getStatus()).isEqualTo(409);
        assertThat(motivo(perdedor)).isEqualTo("CAMBIO_CONCURRENTE");
        assertThat(contar("PRODUCCION")).isEqualTo(1);
        assertThat(contar("BORRADOR")).isZero();
        assertThat(idEn("PRODUCCION")).isEqualTo(candidata);
        assertThat(contarEvaluacionesDe(candidata)).as("la candidata se evaluo una sola vez")
            .isEqualTo(evaluacionesAntes + 1);
    }

    @Test
    @DisplayName("el boton y la publicacion programada a la vez: se publica una vez y la programada no hace nada")
    void despliegueYPublicacionProgramadaALaVez() throws Exception {
        UUID candidata = idEn("BORRADOR");
        jdbc.update("UPDATE chatbot.versiones_base_conocimiento SET despliegue_programado_en = now() - interval '1 minute' "
            + "WHERE estado = 'BORRADOR'");

        Future<MvcResult> boton = enOtroHilo(() -> mvc.perform(post(RUTA + "/borrador/despliegue")
            .with(administrador())).andReturn());
        esperar(aEnEvaluacion);
        Future<Optional<DespliegueProgramado>> programada = enOtroHilo(
            () -> evaluacion.desplegarSiCorresponde(Instant.now()));
        esperarAQueOtraTransaccionEspereElBloqueo();
        aSigue.countDown();

        assertThat(boton.get(ESPERA_MAXIMA_S, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
        assertThat(programada.get(ESPERA_MAXIMA_S, TimeUnit.SECONDS)).as("no hizo nada").isEmpty();
        assertThat(contar("PRODUCCION")).isEqualTo(1);
        assertThat(idEn("PRODUCCION")).isEqualTo(candidata);
        assertThat(jdbc.queryForObject("SELECT despliegue_programado_en IS NULL FROM chatbot.versiones_base_conocimiento "
            + "WHERE id = ?", Boolean.class, candidata)).isTrue();
    }

    // ------------------------------------------------------------- ayudas

    private static RequestPostProcessor administrador() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"));
    }

    private <T> Future<T> enOtroHilo(Callable<T> tarea) {
        return hilos.submit(tarea);
    }

    private static void esperar(CountDownLatch senal) throws InterruptedException {
        if (!senal.await(ESPERA_MAXIMA_S, TimeUnit.SECONDS)) {
            fail("El despliegue A nunca llego a su evaluacion.");
        }
    }

    // B ya pidio la fila que A tiene bloqueada y PostgreSQL lo tiene en espera.
    private void esperarAQueOtraTransaccionEspereElBloqueo() throws InterruptedException {
        long hasta = System.nanoTime() + TimeUnit.SECONDS.toNanos(ESPERA_MAXIMA_S);
        while (System.nanoTime() < hasta) {
            Integer esperando = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity "
                + "WHERE datname = current_database() AND wait_event_type = 'Lock'", Integer.class);
            if (esperando != null && esperando > 0) {
                return;
            }
            Thread.sleep(20);
        }
        fail("La segunda operacion nunca quedo esperando el bloqueo de la fila.");
    }

    private int contar(String estado) {
        Integer cuantas = jdbc.queryForObject(
            "SELECT count(*) FROM chatbot.versiones_base_conocimiento WHERE estado = ?", Integer.class, estado);
        return cuantas == null ? 0 : cuantas;
    }

    private UUID idEn(String estado) {
        return jdbc.queryForObject("SELECT id FROM chatbot.versiones_base_conocimiento WHERE estado = ?",
            UUID.class, estado);
    }

    private String estadoDe(UUID version) {
        return jdbc.queryForObject("SELECT estado FROM chatbot.versiones_base_conocimiento WHERE id = ?",
            String.class, version);
    }

    private long revisionDe(UUID version) {
        Long revision = jdbc.queryForObject(
            "SELECT revision FROM chatbot.versiones_base_conocimiento WHERE id = ?", Long.class, version);
        return revision == null ? -1 : revision;
    }

    private int contarEvaluacionesDe(UUID version) {
        Integer cuantas = jdbc.queryForObject(
            "SELECT count(*) FROM chatbot.evaluaciones_version WHERE version_id = ?", Integer.class, version);
        return cuantas == null ? 0 : cuantas;
    }

    private static String motivo(MvcResult resultado) throws Exception {
        return JsonPath.read(resultado.getResponse().getContentAsString(), "$.motivo");
    }
}
