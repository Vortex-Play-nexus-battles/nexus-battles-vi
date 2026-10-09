package nexus.misiones.configuracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import nexus.misiones.aplicacion.Dobles;
import nexus.misiones.aplicacion.ServicioDeHeroes;
import nexus.misiones.dominio.simulacion.DecisionDeTurno;
import nexus.misiones.dominio.simulacion.TurnoParaDecidir;
import nexus.misiones.ia.DecisorConModelo;
import nexus.misiones.ia.ModeloVersionado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * El cableado de Spring de la IA con modelo, sin MongoDB (aqui no hay Docker para el contexto completo de las IT):
 * el decisor de las simulaciones es un bean aparte que NUNCA debe hacer ambiguo al bean de heroes, que tambien es un
 * decisor. Con la bandera apagada el decisor ES la regla, y un bean que devuelve la misma instancia de otro
 * (un {@code ServicioDeHeroes}) volveria ambigua cualquier inyeccion de ese tipo.
 */
class ConfiguracionDeIaContextoTest {

    @TempDir
    Path carpeta;

    /** Lo que inyectan los casos de uso: heroes por su tipo, y el decisor de las simulaciones por su nombre. */
    static final class Consumidor {
        final ServicioDeHeroes heroes;
        final nexus.misiones.dominio.simulacion.DecisorDeTurno decisor;

        Consumidor(ServicioDeHeroes heroes, @Qualifier("decisorDeTurnoConfigurado")
                nexus.misiones.dominio.simulacion.DecisorDeTurno decisor) {
            this.heroes = heroes;
            this.decisor = decisor;
        }
    }

    private AnnotationConfigApplicationContext contexto(String... propiedades) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        TestPropertyValues.of(propiedades).applyTo(ctx);
        ctx.registerBean("servicioDeHeroes", ServicioDeHeroes.class, Dobles.Heroes::new);
        ctx.register(ConfiguracionDeIa.class);
        ctx.registerBean("consumidor", Consumidor.class);
        ctx.refresh();
        return ctx;
    }

    /** Como arranca el servicio: con el application.properties de main, sin variables de entorno de la IA. */
    private AnnotationConfigApplicationContext contextoConLaConfiguracionDelServicio(String... propiedades) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        new ConfigDataApplicationContextInitializer().initialize(ctx);
        TestPropertyValues.of(propiedades).applyTo(ctx);
        ctx.registerBean("servicioDeHeroes", ServicioDeHeroes.class, Dobles.Heroes::new);
        ctx.register(ConfiguracionDeIa.class);
        ctx.registerBean("consumidor", Consumidor.class);
        ctx.refresh();
        return ctx;
    }

    private static nexus.misiones.dominio.simulacion.DecisorDeTurno delegadoDe(AnnotationConfigApplicationContext ctx) {
        return ((ConfiguracionDeIa.DecisorDeLaSimulacion) ctx.getBean(Consumidor.class).decisor).delegado();
    }

    private Path modeloDePrueba() throws IOException {
        for (String nombre : List.of("modelo.onnx", "modelo.json")) {
            try (InputStream in = getClass().getResourceAsStream("/ia/" + nombre)) {
                Files.write(carpeta.resolve(nombre), in.readAllBytes());
            }
        }
        return carpeta.resolve("modelo.onnx");
    }

    @Test
    @DisplayName("apagado expresamente arranca, el decisor decide como heroes y ServicioDeHeroes sigue siendo unico")
    void apagadoExpresamente() {
        try (AnnotationConfigApplicationContext ctx = contexto("misiones.ia.modelo.habilitado=false",
                "misiones.ia.modelo.ruta=" + ModeloVersionado.onnx())) {
            Consumidor consumidor = ctx.getBean(Consumidor.class);

            assertThat(ctx.getBeansOfType(ServicioDeHeroes.class)).hasSize(1);
            TurnoParaDecidir turno = new TurnoParaDecidir("Guerrero Armas", 1, List.of(), 1, 8, 44, java.util.Map.of(),
                    List.of());
            assertThat(consumidor.decisor.decidir(turno)).isEqualTo(consumidor.heroes.decidir(turno))
                    .isEqualTo(new DecisionDeTurno("Ataque básico", 0, List.of()));
        }
    }

    @Test
    @DisplayName("encendido sin modelo valido arranca igual, y la regla decide")
    void encendidoSinModelo() {
        try (AnnotationConfigApplicationContext ctx = contexto("misiones.ia.modelo.habilitado=true",
                "misiones.ia.modelo.ruta=/no/existe/modelo.onnx")) {
            assertThat(ctx.getBeansOfType(ServicioDeHeroes.class)).hasSize(1);
            assertThat(delegadoDe(ctx)).isNotInstanceOf(DecisorConModelo.class);
        }
    }

    @Test
    @DisplayName("encendido con el modelo de prueba: el decisor usa el modelo y al apagar el contexto se libera")
    void encendidoConModelo() throws IOException {
        try (AnnotationConfigApplicationContext ctx = contexto("misiones.ia.modelo.habilitado=true",
                "misiones.ia.modelo.ruta=" + modeloDePrueba(), "misiones.ia.modelo.confianza-minima=0.5")) {
            assertThat(ctx.getBeansOfType(ServicioDeHeroes.class)).hasSize(1);
            assertThat(delegadoDe(ctx)).isInstanceOf(DecisorConModelo.class);
        }
    }

    // ---------------------------------------------------------------- encendido por omision (la imagen de DEV)

    @Test
    @DisplayName("la configuracion del servicio la trae encendida y con la ruta de la imagen, sin tocar variables")
    void laConfiguracionDelServicioLaEnciende() {
        try (AnnotationConfigApplicationContext ctx = contextoConLaConfiguracionDelServicio()) {
            assertThat(ctx.getEnvironment().getProperty("misiones.ia.modelo.habilitado", Boolean.class)).isTrue();
            assertThat(ctx.getEnvironment().getProperty("misiones.ia.modelo.ruta")).isEqualTo("/app/ia/modelo.onnx");
        }
    }

    @Test
    @DisplayName("encendida por omision pero sin el archivo (pruebas locales, gradlew test): arranca y decide la regla")
    void sinElArchivoDecideLaRegla() {
        org.junit.jupiter.api.Assumptions.assumeFalse(Files.exists(Path.of("/app/ia/modelo.onnx")),
                "esta maquina tiene el modelo de la imagen en /app/ia");
        try (AnnotationConfigApplicationContext ctx = contextoConLaConfiguracionDelServicio()) {
            Consumidor consumidor = ctx.getBean(Consumidor.class);
            TurnoParaDecidir turno = new TurnoParaDecidir("Guerrero Armas", 1, List.of(), 1, 8, 44, java.util.Map.of(),
                    List.of());

            assertThat(ctx.getBeansOfType(ServicioDeHeroes.class)).hasSize(1);
            assertThat(delegadoDe(ctx)).isNotInstanceOf(DecisorConModelo.class);
            assertThat(consumidor.decisor.decidir(turno)).isEqualTo(consumidor.heroes.decidir(turno))
                    .isEqualTo(new DecisionDeTurno("Ataque básico", 0, List.of()));
        }
    }

    @Test
    @DisplayName("encendida por omision y con el archivo en su ruta, el modelo entrenado decide sin mas configuracion")
    void conElArchivoSeEnciende() {
        try (AnnotationConfigApplicationContext ctx = contextoConLaConfiguracionDelServicio(
                "misiones.ia.modelo.ruta=" + ModeloVersionado.onnx())) {
            assertThat(ctx.getBeansOfType(ServicioDeHeroes.class)).hasSize(1);
            assertThat(delegadoDe(ctx)).isInstanceOf(DecisorConModelo.class);
        }
    }

    @Test
    @DisplayName("se apaga con la variable de siempre aunque el archivo este: decide la regla")
    void seApagaConLaBandera() {
        try (AnnotationConfigApplicationContext ctx = contextoConLaConfiguracionDelServicio(
                "misiones.ia.modelo.habilitado=false", "misiones.ia.modelo.ruta=" + ModeloVersionado.onnx())) {
            assertThat(delegadoDe(ctx)).isNotInstanceOf(DecisorConModelo.class);
        }
    }
}
