package nexus.misiones.configuracion;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import nexus.misiones.aplicacion.AvisosDeMisiones;
import nexus.misiones.aplicacion.CancelarEjecucion;
import nexus.misiones.aplicacion.CatalogoDeProductos;
import nexus.misiones.aplicacion.ConsultarEjecuciones;
import nexus.misiones.aplicacion.ConsultarMisiones;
import nexus.misiones.aplicacion.CorreoDeMisiones;
import nexus.misiones.aplicacion.EstrategiaDeEnemigos;
import nexus.misiones.aplicacion.DirectorioDeJugadores;
import nexus.misiones.aplicacion.EstrategiasPredefinidas;
import nexus.misiones.aplicacion.GestionarEstrategias;
import nexus.misiones.aplicacion.GestionarFavoritas;
import nexus.misiones.aplicacion.InventarioDeHeroes;
import nexus.misiones.aplicacion.LibroDeCreditos;
import nexus.misiones.aplicacion.LiquidarEjecucion;
import nexus.misiones.aplicacion.MatricularHeroe;
import nexus.misiones.aplicacion.ParametrosDeMisiones;
import nexus.misiones.aplicacion.PerfilDeCombateDelHeroe;
import nexus.misiones.aplicacion.RotacionesPorDefectoDeEnemigos;
import nexus.misiones.aplicacion.ServicioDeHeroes;
import nexus.misiones.aplicacion.SimularEjecucion;
import nexus.misiones.aplicacion.TrabajoDeMisiones;
import nexus.misiones.catalogo.CatalogoDeEstrategiasDesdeSemilla;
import nexus.misiones.catalogo.CatalogoDeMisionesDesdeSemilla;
import nexus.misiones.dominio.CatalogoDeEstrategiasDeEnemigos;
import nexus.misiones.dominio.CatalogoDeMisiones;
import nexus.misiones.dominio.Dificultad;
import nexus.misiones.dominio.Escalon;
import nexus.misiones.dominio.ParametrosDeRecompensa;
import nexus.misiones.dominio.RepositorioDeEjecuciones;
import nexus.misiones.dominio.RepositorioDeEventosDeCombate;
import nexus.misiones.dominio.RepositorioDeEstrategias;
import nexus.misiones.dominio.RepositorioDeFavoritas;
import nexus.misiones.dominio.simulacion.DecisorDeTurno;
import nexus.misiones.dominio.simulacion.MotorDeCombate;
import nexus.misiones.persistencia.RepositorioEjecucionesMongo;
import nexus.misiones.persistencia.RepositorioEventosDeCombateMongo;
import nexus.misiones.persistencia.RepositorioEstrategiasMongo;
import nexus.misiones.persistencia.RepositorioFavoritasMongo;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoOperations;

/**
 * Cableado del modulo.
 *
 * <p>Los casos de uso son clases de Java corrientes, sin anotaciones de Spring:
 * se prueban sin contexto y no quedan atados al framework. El precio es
 * declararlos aqui, y es barato (mismo criterio que salas-partidas).
 */
@Configuration
public class ConfiguracionDeMisiones {

    @Bean
    public Clock reloj() {
        return Clock.systemUTC();
    }

    /**
     * Lo que no es del documento, leido de variables de entorno (regla 10):
     * el reloj de las misiones, el ritmo del trabajo y las decisiones del PO
     * con su valor provisional (ver application.properties y el README).
     */
    @Bean
    public ParametrosDeMisiones parametrosDeMisiones(
            @Value("${misiones.segundos-por-hora:3600}") double segundosPorHora,
            @Value("${misiones.reintento-segundos:30}") long reintentoSegundos,
            @Value("${misiones.trabajo.lote:20}") int lote,
            @Value("${misiones.correo.activo:true}") boolean correoActivo,
            @Value("${misiones.avisos.activo:true}") boolean avisosActivos,
            @Value("${misiones.semilla-de-pruebas:}") String semillaDePruebas,
            @Value("${misiones.multiplicador-mitico:}") String multiplicadorMitico,
            @Value("${misiones.experiencia-por-completar.facil:0}") double xpFacil,
            @Value("${misiones.experiencia-por-completar.normal:0}") double xpNormal,
            @Value("${misiones.experiencia-por-completar.dificil:0}") double xpDificil,
            @Value("${misiones.experiencia-por-completar.extremo:0}") double xpExtremo,
            @Value("${misiones.creditos-por-escalon.heroico:}") String creditosHeroico,
            @Value("${misiones.creditos-por-escalon.legendario:}") String creditosLegendario,
            @Value("${misiones.creditos-por-escalon.mitico:}") String creditosMitico,
            @Value("${misiones.epica-exige-completar:false}") boolean epicaExigeCompletar) {
        Map<Dificultad, Double> experiencia = new EnumMap<>(Dificultad.class);
        experiencia.put(Dificultad.FACIL, xpFacil);
        experiencia.put(Dificultad.NORMAL, xpNormal);
        experiencia.put(Dificultad.DIFICIL, xpDificil);
        experiencia.put(Dificultad.EXTREMO, xpExtremo);
        Map<Escalon, Double> creditos = ParametrosDeRecompensa.sinMultiplicadores();
        ponerSiHay(creditos, Escalon.HEROICO, creditosHeroico);
        ponerSiHay(creditos, Escalon.LEGENDARIO, creditosLegendario);
        ponerSiHay(creditos, Escalon.MITICO, creditosMitico);
        return new ParametrosDeMisiones(
                Duration.ofMillis(Math.max(1, Math.round(segundosPorHora * 1000))),
                Duration.ofSeconds(reintentoSegundos),
                lote,
                correoActivo,
                avisosActivos,
                semillaDePruebas == null || semillaDePruebas.isBlank() ? null : Long.valueOf(semillaDePruebas.trim()),
                decimal(multiplicadorMitico),
                new ParametrosDeRecompensa(experiencia, creditos, epicaExigeCompletar));
    }

    @Bean
    public CatalogoDeMisionesDesdeSemilla catalogoDeMisiones(
            @Value("${misiones.semilla-provisional:false}") boolean conSemillaProvisional,
            @Value("${misiones.semilla-extra:}") String semillaExtra) {
        return CatalogoDeMisionesDesdeSemilla.cargar(conSemillaProvisional,
                semillaExtra == null || semillaExtra.isBlank() ? null : Path.of(semillaExtra.trim()));
    }

    @Bean
    public RepositorioDeEjecuciones repositorioDeEjecuciones(MongoOperations mongo) {
        return new RepositorioEjecucionesMongo(mongo);
    }

    /** Los turnos de combate de cada mision simulada (HU-SIM-003), en su propia coleccion. */
    @Bean
    public RepositorioDeEventosDeCombate repositorioDeEventosDeCombate(MongoOperations mongo) {
        return new RepositorioEventosDeCombateMongo(mongo);
    }

    @Bean
    public RepositorioDeEstrategias repositorioDeEstrategias(MongoOperations mongo) {
        return new RepositorioEstrategiasMongo(mongo);
    }

    @Bean
    public RepositorioDeFavoritas repositorioDeFavoritas(MongoOperations mongo) {
        return new RepositorioFavoritasMongo(mongo);
    }

    /**
     * La semilla de cada ejecucion: aleatoria de verdad (SecureRandom) en
     * juego real; la fija {@code MISIONES_SEMILLA_DE_PRUEBAS} en pruebas.
     */
    @Bean
    public MatricularHeroe matricularHeroe(CatalogoDeMisiones catalogo, RepositorioDeEjecuciones ejecuciones,
                                           RepositorioDeEstrategias estrategias, InventarioDeHeroes inventario,
                                           CatalogoDeProductos productos, ServicioDeHeroes heroes,
                                           ParametrosDeMisiones parametros, Clock reloj) {
        SecureRandom azar = new SecureRandom();
        return new MatricularHeroe(catalogo, ejecuciones, estrategias, inventario, productos, heroes, parametros,
                reloj, azar::nextLong);
    }

    /** Lo que el heroe lleva al combate: estadisticas con equipo, equipamiento y epicas (HU-SIM-003). */
    @Bean
    public PerfilDeCombateDelHeroe perfilDeCombateDelHeroe(InventarioDeHeroes inventario,
                                                           CatalogoDeProductos productos, ServicioDeHeroes heroes) {
        return new PerfilDeCombateDelHeroe(inventario, productos, heroes);
    }

    /**
     * Las estrategias predefinidas de los enemigos (HU-SIM-004), leidas de la semilla versionada. Un archivo
     * ilegible o una estrategia invalida no tumban el arranque: se anotan y ese enemigo juega la heuristica.
     */
    @Bean
    public CatalogoDeEstrategiasDeEnemigos catalogoDeEstrategiasDeEnemigos() {
        return CatalogoDeEstrategiasDesdeSemilla.cargar();
    }

    /**
     * La estrategia de los enemigos que la mision no trae escrita: la predefinida de su prototipo y nivel y, si no
     * hay una que heroes acepte, la heuristica por defecto.
     */
    @Bean
    public EstrategiaDeEnemigos estrategiaDeEnemigos(CatalogoDeEstrategiasDeEnemigos catalogo,
                                                     ServicioDeHeroes heroes) {
        return new EstrategiasPredefinidas(catalogo, heroes, new RotacionesPorDefectoDeEnemigos(heroes));
    }

    @Bean
    public SimularEjecucion simularEjecucion(CatalogoDeMisiones catalogo, RepositorioDeEjecuciones ejecuciones,
                                             RepositorioDeEventosDeCombate eventos, ServicioDeHeroes heroes,
                                             @Qualifier("decisorDeTurnoConfigurado") DecisorDeTurno decisor,
                                             MotorDeCombate motor, PerfilDeCombateDelHeroe perfiles,
                                             EstrategiaDeEnemigos enemigos, ParametrosDeMisiones parametros,
                                             Clock reloj) {
        return new SimularEjecucion(catalogo, ejecuciones, eventos, heroes, decisor, motor, perfiles, enemigos,
                parametros, reloj);
    }

    @Bean
    public LiquidarEjecucion liquidarEjecucion(RepositorioDeEjecuciones ejecuciones, CatalogoDeMisiones catalogo,
                                               InventarioDeHeroes inventario, LibroDeCreditos libro,
                                               DirectorioDeJugadores directorio, CorreoDeMisiones correo,
                                               AvisosDeMisiones avisos, ParametrosDeMisiones parametros,
                                               Clock reloj) {
        return new LiquidarEjecucion(ejecuciones, catalogo, inventario, libro, directorio, correo, avisos,
                parametros, reloj);
    }

    @Bean
    public CancelarEjecucion cancelarEjecucion(RepositorioDeEjecuciones ejecuciones, LiquidarEjecucion liquidar,
                                               Clock reloj) {
        return new CancelarEjecucion(ejecuciones, liquidar, reloj);
    }

    @Bean
    public TrabajoDeMisiones trabajoDeMisiones(RepositorioDeEjecuciones ejecuciones, SimularEjecucion simular,
                                               LiquidarEjecucion liquidar, ParametrosDeMisiones parametros,
                                               Clock reloj) {
        return new TrabajoDeMisiones(ejecuciones, simular, liquidar, parametros, reloj);
    }

    @Bean
    public ConsultarMisiones consultarMisiones(CatalogoDeMisiones catalogo, RepositorioDeEjecuciones ejecuciones,
                                               RepositorioDeFavoritas favoritas, Clock reloj) {
        return new ConsultarMisiones(catalogo, ejecuciones, favoritas, reloj);
    }

    @Bean
    public ConsultarEjecuciones consultarEjecuciones(CatalogoDeMisiones catalogo,
                                                     RepositorioDeEjecuciones ejecuciones) {
        return new ConsultarEjecuciones(catalogo, ejecuciones);
    }

    @Bean
    public GestionarFavoritas gestionarFavoritas(CatalogoDeMisiones catalogo, RepositorioDeFavoritas favoritas,
                                                 Clock reloj) {
        return new GestionarFavoritas(catalogo, favoritas, reloj);
    }

    @Bean
    public GestionarEstrategias gestionarEstrategias(RepositorioDeEstrategias estrategias,
                                                     InventarioDeHeroes inventario, CatalogoDeProductos productos,
                                                     ServicioDeHeroes heroes, Clock reloj) {
        return new GestionarEstrategias(estrategias, inventario, productos, heroes, reloj);
    }

    private static void ponerSiHay(Map<Escalon, Double> destino, Escalon escalon, String valor) {
        Double numero = decimal(valor);
        if (numero != null) {
            destino.put(escalon, numero);
        }
    }

    /** Un decimal de configuracion; vacio = no fijado (la decision del PO sigue abierta). */
    static Double decimal(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        double numero = Double.parseDouble(valor.trim().replace(',', '.'));
        if (!(numero > 0)) {
            throw new IllegalStateException("Un multiplicador de configuracion debe ser positivo: " + valor);
        }
        return numero;
    }
}
