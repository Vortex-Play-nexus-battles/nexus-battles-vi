package nexus.misiones.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import nexus.misiones.catalogo.CatalogoDeEstrategiasDesdeSemilla;
import nexus.misiones.dominio.CatalogoDeEstrategiasDeEnemigos;
import nexus.misiones.dominio.Ejecucion;
import nexus.misiones.dominio.EstadoEjecucion;
import nexus.misiones.dominio.GrupoDeEnemigos;
import nexus.misiones.dominio.Jefe;
import nexus.misiones.dominio.Misiones;
import nexus.misiones.dominio.ParametrosDeRecompensa;
import nexus.misiones.dominio.simulacion.DecisionDeTurno;
import nexus.misiones.dominio.simulacion.DecisorDeTurno;
import nexus.misiones.dominio.simulacion.EventoDeCombate;
import nexus.misiones.dominio.simulacion.MotorDeCombate;
import nexus.misiones.dominio.simulacion.OrigenDeEstrategia;
import nexus.misiones.dominio.simulacion.TurnoParaDecidir;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * HU-SIM-004 vista desde la simulacion de una ejecucion, con las dependencias en memoria: de donde sale la estrategia
 * de cada enemigo (precedencia), que pasa si la predefinida no vale, y que los enemigos pelean con el mismo motor y
 * las mismas reglas que el resto del juego (criterio 2).
 */
class EnemigosConEstrategiaTest {

    private static final Instant INICIO = Instant.parse("2026-10-01T10:00:00Z");
    private static final String JUGADOR = "11111111-1111-4111-8111-111111111111";

    private final AtomicReference<Instant> ahora = new AtomicReference<>(INICIO);
    private final Clock reloj = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora.get();
        }
    };

    private Dobles.Ejecuciones ejecuciones;
    private Dobles.Heroes heroes;
    private Dobles.Motor motor;
    private Dobles.Eventos eventos;
    private Dobles.Inventario inventario;
    private Dobles.Productos productos;
    private ParametrosDeMisiones parametros;
    private MatricularHeroe matricular;
    private final List<TurnoParaDecidir> decisiones = new ArrayList<>();

    @BeforeEach
    void preparar() {
        ejecuciones = new Dobles.Ejecuciones();
        inventario = new Dobles.Inventario().conHeroe("h-1", JUGADOR, "p-armas", true);
        productos = new Dobles.Productos();
        productos.prototipos.put("p-armas", "Guerrero Armas");
        heroes = new Dobles.Heroes();
        // Cada enemigo aguanta dos golpes del heroe: actua al menos una vez antes de caer.
        heroes.vidaDeLosEnemigos = 60;
        motor = new Dobles.Motor();
        motor.danoDelHeroe = 30;
        eventos = new Dobles.Eventos();
        parametros = new ParametrosDeMisiones(Duration.ofHours(1), Duration.ofSeconds(30), 20, true, null, null,
                new ParametrosDeRecompensa(Map.of(), Map.of(), false));
    }

    private static final List<List<String>> LA_DE_LA_MISION = List.of(List.of("Lanza de los dioses"));

    private static final Jefe JEFE = new Jefe("El Guardián Eterno", "Guerrero Tanque", 60, 5, null, List.of());

    private static List<GrupoDeEnemigos> enemigos() {
        return List.of(
                new GrupoDeEnemigos("Guardia armado", 1, null, "Guerrero Armas", 60, 5, LA_DE_LA_MISION),
                new GrupoDeEnemigos("Espectro", 1, null, "Mago Fuego", 60, 5, List.of()),
                new GrupoDeEnemigos("Sombra", 1, null, "Pícaro Veneno", 60, 5, List.of()));
    }

    /** Simula la ejecucion de {@code mision} y devuelve los turnos guardados. */
    private List<EventoDeCombate> simular(EstrategiaDeEnemigos estrategia, nexus.misiones.dominio.Mision mision) {
        Dobles.Catalogo catalogo = new Dobles.Catalogo(List.of(mision), List.of());
        matricular = new MatricularHeroe(catalogo, ejecuciones, new Dobles.Estrategias(), inventario, productos,
                heroes, parametros, reloj, () -> 7L);
        Ejecucion ejecucion = matricular.matricular(JUGADOR,
                new SolicitudDeMatricula(mision.id(), "h-1", List.of(), null, null)).ejecucion();
        ahora.set(INICIO.plus(Duration.ofHours(2)));
        // Un solo decisor para los dos lados, observado: asi se ve que enemigos y heroe consultan lo mismo. Con
        // rotaciones juega el primer paso de la primera (la regla de heroes real vive en otro servicio); sin ellas,
        // lo que haria heroes.
        DecisorDeTurno espia = turno -> {
            decisiones.add(turno);
            return turno.rotaciones().isEmpty() ? heroes.decidir(turno)
                    : new DecisionDeTurno(turno.rotaciones().getFirst().getFirst(), 0, turno.cursores());
        };
        SimularEjecucion simular = new SimularEjecucion(catalogo, ejecuciones, eventos, heroes, espia, motor,
                new PerfilDeCombateDelHeroe(inventario, productos, heroes), estrategia, parametros, reloj);

        Ejecucion terminada = simular.simular(ejecuciones.buscar(ejecucion.id()).orElseThrow()).orElseThrow();

        assertThat(terminada.estado()).isNotEqualTo(EstadoEjecucion.EN_PROGRESO);
        return eventos.de(ejecucion.id());
    }

    private static List<EventoDeCombate> deEnemigo(List<EventoDeCombate> todos, String nombre) {
        return todos.stream().filter(e -> e.actor().lado() != EventoDeCombate.Lado.HEROE
                && e.actor().nombre().equals(nombre) && e.jugada() != null).toList();
    }

    private EstrategiasPredefinidas predefinidas() {
        return new EstrategiasPredefinidas(CatalogoDeEstrategiasDesdeSemilla.cargar(), heroes,
                new RotacionesPorDefectoDeEnemigos(heroes));
    }

    // ---------------------------------------------------------------- precedencia

    @Test
    @DisplayName("primero la rotacion que trae la mision, luego la predefinida del prototipo y nivel, y por ultimo la heuristica")
    void precedencia() {
        heroes.habilidadesValidas = List.of("Flor de loto", "Ataque básico");
        // Solo el mago de fuego tiene predefinida: el picaro cae a la heuristica.
        CatalogoDeEstrategiasDeEnemigos soloMago = (prototipo, nivel) ->
                CatalogoDeEstrategiasDesdeSemilla.cargar().para(prototipo, nivel)
                        .filter(e -> e.prototipo().equals("Mago Fuego") || e.prototipo().equals("Guerrero Armas")
                                || e.prototipo().equals("Guerrero Tanque"));
        EstrategiasPredefinidas estrategia = new EstrategiasPredefinidas(soloMago, heroes,
                new RotacionesPorDefectoDeEnemigos(heroes));

        List<EventoDeCombate> turnos = simular(estrategia, Misiones.conEnemigos("precedencia", enemigos(), JEFE));

        // El guardia trae su rotacion escrita en la mision, aunque su prototipo tenga predefinida.
        assertThat(deEnemigo(turnos, "Guardia armado")).isNotEmpty().allSatisfy(e -> {
            assertThat(e.jugada().estrategia()).isEqualTo(OrigenDeEstrategia.MISION);
            assertThat(e.jugada().estrategiaId()).isNull();
        });
        // El espectro no trae nada: la predefinida de Mago Fuego en el nivel del heroe (1).
        assertThat(deEnemigo(turnos, "Espectro")).isNotEmpty().allSatisfy(e -> {
            assertThat(e.jugada().estrategia()).isEqualTo(OrigenDeEstrategia.PREDEFINIDA);
            assertThat(e.jugada().estrategiaId()).isEqualTo("mago-fuego-n1");
        });
        // El jefe tampoco: la predefinida del tanque.
        assertThat(deEnemigo(turnos, "El Guardián Eterno")).isNotEmpty().allSatisfy(e -> {
            assertThat(e.jugada().estrategia()).isEqualTo(OrigenDeEstrategia.PREDEFINIDA);
            assertThat(e.jugada().estrategiaId()).isEqualTo("guerrero-tanque-n1");
        });
        // La sombra no tiene predefinida: la heuristica, que no tiene id.
        assertThat(deEnemigo(turnos, "Sombra")).isNotEmpty().allSatisfy(e -> {
            assertThat(e.jugada().estrategia()).isEqualTo(OrigenDeEstrategia.HEURISTICA);
            assertThat(e.jugada().estrategiaId()).isNull();
        });
    }

    @Test
    @DisplayName("el decisor recibe las rotaciones de cada origen: la de la mision, la predefinida o la heuristica")
    void elDecisorRecibeLasRotaciones() {
        heroes.habilidadesValidas = List.of("Flor de loto", "Ataque básico");
        CatalogoDeEstrategiasDeEnemigos soloMago = (prototipo, nivel) ->
                CatalogoDeEstrategiasDesdeSemilla.cargar().para(prototipo, nivel)
                        .filter(e -> e.prototipo().equals("Mago Fuego"));

        simular(new EstrategiasPredefinidas(soloMago, heroes, new RotacionesPorDefectoDeEnemigos(heroes)),
                Misiones.conEnemigos("rotaciones", enemigos(), JEFE));

        assertThat(rotacionesPedidasPor("Guerrero Armas")).isEqualTo(LA_DE_LA_MISION);
        assertThat(rotacionesPedidasPor("Mago Fuego")).containsExactly(List.of("Misiles de magma"));
        assertThat(rotacionesPedidasPor("Pícaro Veneno")).containsExactly(List.of("Flor de loto"));
    }

    /** Las rotaciones con las que se pregunto por un prototipo (el heroe es Guerrero Armas con estrategia vacia). */
    private List<List<String>> rotacionesPedidasPor(String prototipo) {
        List<List<List<String>>> distintas = decisiones.stream().filter(t -> t.prototipo().equals(prototipo))
                .map(TurnoParaDecidir::rotaciones).filter(r -> !r.isEmpty()).distinct().toList();
        assertThat(distintas).as("rotaciones con las que se pregunto por " + prototipo).hasSize(1);
        return distintas.getFirst();
    }

    // ---------------------------------------------------------------- una predefinida invalida no rompe nada

    @Test
    @DisplayName("si heroes no acepta la predefinida, el enemigo pelea con la heuristica y la simulacion termina sin error")
    void predefinidaInvalida() {
        heroes.motivoDeRechazo = "La rotación 1 usa una habilidad que Mago Fuego no posee en nivel 1: Misiles de magma.";
        heroes.rechazarSoloLasEscritas = true;
        heroes.habilidadesValidas = List.of("Misiles de magma", "Ataque básico");

        List<EventoDeCombate> turnos = simular(predefinidas(),
                Misiones.conEnemigos("invalida", enemigos(), JEFE));

        assertThat(deEnemigo(turnos, "Espectro")).isNotEmpty().allSatisfy(e -> {
            assertThat(e.jugada().estrategia()).isEqualTo(OrigenDeEstrategia.HEURISTICA);
            assertThat(e.jugada().estrategiaId()).isNull();
        });
        assertThat(rotacionesPedidasPor("Mago Fuego")).containsExactly(List.of("Misiles de magma"));
    }

    @Test
    @DisplayName("un catalogo de estrategias vacio (archivo danado) deja a todos los enemigos con la heuristica")
    void catalogoVacio() {
        heroes.habilidadesValidas = List.of("Golpe con escudo", "Ataque básico");
        EstrategiasPredefinidas sinCatalogo = new EstrategiasPredefinidas((prototipo, nivel) -> Optional.empty(),
                heroes, new RotacionesPorDefectoDeEnemigos(heroes));

        List<EventoDeCombate> turnos = simular(sinCatalogo, Misiones.conEnemigos("vacio", enemigos(), JEFE));

        assertThat(deEnemigo(turnos, "El Guardián Eterno")).isNotEmpty()
                .allSatisfy(e -> assertThat(e.jugada().estrategia()).isEqualTo(OrigenDeEstrategia.HEURISTICA));
    }

    @Test
    @DisplayName("un enemigo sin ninguna estrategia (ni escrita, ni predefinida, ni habilidades) juega ataque basico y no deja traza")
    void sinEstrategia() {
        heroes.habilidadesValidas = List.of("Ataque básico");
        EstrategiasPredefinidas sinCatalogo = new EstrategiasPredefinidas((prototipo, nivel) -> Optional.empty(),
                heroes, new RotacionesPorDefectoDeEnemigos(heroes));

        List<EventoDeCombate> turnos = simular(sinCatalogo, Misiones.conEnemigos("nada", enemigos(), JEFE));

        assertThat(deEnemigo(turnos, "Espectro")).isNotEmpty().allSatisfy(e -> {
            assertThat(e.jugada().ejecutada()).isEqualTo("Ataque básico");
            assertThat(e.jugada().estrategia()).isNull();
            assertThat(e.jugada().estrategiaId()).isNull();
        });
    }

    // ---------------------------------------------------------------- criterio 2: las mismas reglas de combate

    @Test
    @DisplayName("los enemigos actuan por combate/turnos y combate/acciones del motor, como el heroe, y nunca por combate/ataques")
    void enemigosPorElMotorDeLasBatallas() {
        simular(predefinidas(), Misiones.conEnemigos("motor", enemigos(), JEFE));

        // El motor de misiones solo sabe hacer dos cosas, las mismas de salas-partidas: empezar un turno y
        // resolver una accion. No hay otra puerta (el /ataques de la 1.1.0 ya no se usa).
        Set<String> operaciones = new TreeSet<>();
        for (Method m : MotorDeCombate.class.getDeclaredMethods()) {
            operaciones.add(m.getName());
        }
        assertThat(operaciones).containsExactlyInAnyOrder("iniciarTurno", "resolverAccion");

        Map<String, Integer> porLlamada = new LinkedHashMap<>();
        motor.llamadas.forEach(l -> porLlamada.merge(l.split(" ")[0] + " " + l.split(" ")[1], 1, Integer::sum));
        assertThat(porLlamada.keySet()).containsExactlyInAnyOrder("turnos heroe", "turnos rival", "acciones heroe",
                "acciones rival");
        assertThat(motor.llamadas).allSatisfy(l -> assertThat(l).matches("(turnos|acciones) (heroe|rival).*"));
        // Y la accion del enemigo es la de su estrategia: los Misiles de magma del mago, pedidos al motor.
        assertThat(motor.llamadas).anyMatch(l -> l.equals("acciones rival Misiles de magma"));
    }

    @Test
    @DisplayName("la jugada de un enemigo se decide con la misma regla que la del heroe: el mismo decisor, con el contexto del duelo")
    void elMismoDecisorParaTodos() {
        simular(predefinidas(), Misiones.conEnemigos("decisor", enemigos(), JEFE));

        assertThat(decisiones).extracting(TurnoParaDecidir::prototipo)
                .contains("Guerrero Armas", "Mago Fuego", "Guerrero Tanque");
        assertThat(decisiones).allSatisfy(t -> {
            assertThat(t.contexto()).isNotNull();
            assertThat(t.contexto().prototipoDelOponente()).isNotBlank();
        });
        // Los turnos del enemigo llevan las rotaciones y los cursores de su estrategia como los del heroe.
        assertThat(decisiones.stream().filter(t -> t.prototipo().equals("Mago Fuego")))
                .allSatisfy(t -> assertThat(t.rotaciones()).containsExactly(List.of("Misiles de magma")));
    }
}
