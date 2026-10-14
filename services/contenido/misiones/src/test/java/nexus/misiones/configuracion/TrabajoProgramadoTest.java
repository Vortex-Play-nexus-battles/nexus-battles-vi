package nexus.misiones.configuracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import nexus.misiones.aplicacion.Dobles;
import nexus.misiones.aplicacion.LiquidarEjecucion;
import nexus.misiones.aplicacion.ParametrosDeMisiones;
import nexus.misiones.aplicacion.PerfilDeCombateDelHeroe;
import nexus.misiones.aplicacion.RotacionesPorDefectoDeEnemigos;
import nexus.misiones.aplicacion.SimularEjecucion;
import nexus.misiones.aplicacion.TrabajoDeMisiones;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * HU-SIM-007, criterio 1: el trabajo en segundo plano corre solo. Aqui se prueba el cableado de Spring (sin Mongo ni
 * Docker): con el programador encendido, el trabajo da vueltas por su cuenta cada intervalo; apagado, no da ninguna.
 * Que cada vuelta haga lo que debe, con el reloj controlado, lo prueba {@code ContinuidadEnSegundoPlanoTest}.
 */
class TrabajoProgramadoTest {

    /** El trabajo de verdad, armado con dobles en memoria, que ademas cuenta las vueltas que le dan. */
    private static final class TrabajoContado extends TrabajoDeMisiones {
        final AtomicInteger vueltas = new AtomicInteger();
        final CountDownLatch dosVueltas = new CountDownLatch(2);

        TrabajoContado() {
            super(new Dobles.Ejecuciones(), simulador(), liquidador(), parametros(), Clock.systemUTC());
        }

        private static ParametrosDeMisiones parametros() {
            return ParametrosDeMisiones.porOmision();
        }

        private static SimularEjecucion simulador() {
            Dobles.Heroes heroes = new Dobles.Heroes();
            return new SimularEjecucion(new Dobles.Catalogo(List.of(), List.of()), new Dobles.Ejecuciones(),
                    new Dobles.Eventos(), heroes, new Dobles.Motor(),
                    new PerfilDeCombateDelHeroe(new Dobles.Inventario(), new Dobles.Productos(), heroes),
                    new RotacionesPorDefectoDeEnemigos(heroes), parametros(), Clock.systemUTC());
        }

        private static LiquidarEjecucion liquidador() {
            return new LiquidarEjecucion(new Dobles.Ejecuciones(), new Dobles.Catalogo(List.of(), List.of()),
                    new Dobles.Inventario(), new Dobles.Libro(), new Dobles.Directorio(), new Dobles.Correo(),
                    new Dobles.Avisos(), parametros(), Clock.systemUTC());
        }

        @Override
        public void ejecutar() {
            vueltas.incrementAndGet();
            dosVueltas.countDown();
            super.ejecutar();
        }
    }

    private static AnnotationConfigApplicationContext contexto(TrabajoContado trabajo, String... propiedades) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        TestPropertyValues.of(propiedades).applyTo(ctx);
        ctx.registerBean("trabajoDeMisiones", TrabajoDeMisiones.class, () -> trabajo);
        ctx.register(TrabajoProgramado.class);
        ctx.refresh();
        return ctx;
    }

    @Test
    @DisplayName("criterio 1: con el programador encendido el trabajo da vueltas por su cuenta, sin que nadie lo llame")
    void criterio1_elProgramadorDaVueltasSolo() throws InterruptedException {
        TrabajoContado trabajo = new TrabajoContado();

        try (AnnotationConfigApplicationContext ctx = contexto(trabajo,
                "misiones.trabajo.intervalo-ms=20")) {
            assertThat(ctx.getBeansOfType(TrabajoProgramado.class)).hasSize(1);
            assertThat(trabajo.dosVueltas.await(10, TimeUnit.SECONDS))
                    .as("el programador debe dar al menos dos vueltas sin ayuda").isTrue();
        }
        assertThat(trabajo.vueltas.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("criterio 1: apagado con misiones.trabajo.activo=false, no se crea y no da ninguna vuelta")
    void criterio1_apagadoNoDaVueltas() throws InterruptedException {
        TrabajoContado trabajo = new TrabajoContado();

        try (AnnotationConfigApplicationContext ctx = contexto(trabajo,
                "misiones.trabajo.intervalo-ms=20", "misiones.trabajo.activo=false")) {
            assertThat(ctx.getBeansOfType(TrabajoProgramado.class)).isEmpty();
            Thread.sleep(300);
        }
        assertThat(trabajo.vueltas.get()).isZero();
    }

    @Test
    @DisplayName("criterio 1: por omision el programador esta encendido y espera 30 s entre vueltas")
    void criterio1_porOmisionEstaEncendido() throws Exception {
        var vuelta = TrabajoProgramado.class.getMethod("darUnaVuelta");
        var programada = vuelta.getAnnotation(org.springframework.scheduling.annotation.Scheduled.class);
        var condicion = TrabajoProgramado.class.getAnnotation(
                org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class);

        assertThat(programada.fixedDelayString()).isEqualTo("${misiones.trabajo.intervalo-ms:30000}");
        assertThat(condicion.matchIfMissing()).isTrue();
        assertThat(condicion.name()).containsExactly("misiones.trabajo.activo");
    }
}
