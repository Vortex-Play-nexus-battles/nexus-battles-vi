package nexus.misiones.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import nexus.misiones.dominio.Ejecucion;
import nexus.misiones.dominio.Epica;
import nexus.misiones.dominio.Escalon;
import nexus.misiones.dominio.EstadoEjecucion;
import nexus.misiones.dominio.GrupoDeEnemigos;
import nexus.misiones.dominio.HeroeEnMision;
import nexus.misiones.dominio.Jefe;
import nexus.misiones.dominio.MasterDeMision;
import nexus.misiones.dominio.Mision;
import nexus.misiones.dominio.Misiones;
import nexus.misiones.dominio.PasoDeLiquidacion;
import nexus.misiones.dominio.ParametrosDeRecompensa;
import nexus.misiones.dominio.RecompensasDeEjecucion;
import nexus.misiones.dominio.simulacion.Combatiente;
import nexus.misiones.dominio.simulacion.EstadisticasDeCombate;
import nexus.misiones.dominio.simulacion.EventoDeCombate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * HU-SIM-006 (#121), «Master reforzado y obtencion de su epica»: una prueba por criterio de aceptacion, corrida contra
 * la simulacion de una ejecucion con las estadisticas reales de la Tabla 6 (nivel como factor multiplicador) y el
 * motor en memoria. Es la evidencia automatica que el docente pide por criterio.
 *
 * <p>Los regulares son un Guerrero Armas, un Guerrero Tanque y un Mago Fuego; el jefe, un Pícaro Machete; el Master, un
 * Pícaro Veneno, el prototipo de menos vida y defensa de los que pelean. Cada prototipo aparece una sola vez en su
 * papel para poder decir quien es quien por lo que el motor recibio.
 */
class MasterReforzadoTest {

    private static final Instant INICIO = Instant.parse("2026-10-01T10:00:00Z");
    private static final String JUGADOR = "11111111-1111-4111-8111-111111111111";

    private static final String MASTER = "Pícaro Veneno";
    private static final List<String> REGULARES = List.of("Guerrero Armas", "Guerrero Tanque", "Mago Fuego");

    private static final Epica VELO = Misiones.VELO_DE_SOMBRAS;
    private static final Epica FRIO = new Epica("Frío concentrado", "-1 de poder al oponente",
            "No recibe ningún daño en el siguiente turno", "978446ae-c979-349b-b620-f4215852ab5f");

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

    private HeroesDeTabla6 heroes;
    private Dobles.Motor motor;
    private Dobles.Ejecuciones ejecuciones;
    private Dobles.Eventos eventos;
    private Dobles.Inventario inventario;
    private Dobles.Productos productos;
    private ParametrosDeMisiones parametros;

    @BeforeEach
    void preparar() {
        heroes = new HeroesDeTabla6();
        motor = new Dobles.Motor();
        // El heroe gana todos los duelos de un golpe: lo que se mira es contra quien pelea, no como le va.
        motor.danoDelHeroe = 100_000;
        motor.danoDeLosEnemigos = 0;
        ejecuciones = new Dobles.Ejecuciones();
        eventos = new Dobles.Eventos();
        inventario = new Dobles.Inventario();
        productos = new Dobles.Productos();
        parametros = new ParametrosDeMisiones(Duration.ofHours(1), Duration.ofSeconds(30), 20, true, null, null,
                new ParametrosDeRecompensa(Map.of(), Map.of(), false));
    }

    // ------------------------------------------------------------------ la mision y su simulacion

    private static Mision mision(MasterDeMision... masters) {
        return Misiones.conEnemigosYMasters("master-reforzado", List.of(
                new GrupoDeEnemigos("Sombras Corrompidas", 2, null, "Guerrero Armas", null, null, List.of()),
                new GrupoDeEnemigos("Guardianes de Piedra", 2, null, "Guerrero Tanque", null, null, List.of()),
                new GrupoDeEnemigos("Espectros Ancestrales", 1, null, "Mago Fuego", null, null, List.of())),
                new Jefe("El Guardián Eterno", "Pícaro Machete", 100, 5, null, List.of()), List.of(masters));
    }

    private static MasterDeMision sombra() {
        return new MasterDeMision("Sombra del Olvido", MASTER, 1.0, VELO);
    }

    private Ejecucion simular(int nivelDelHeroe, Escalon escalon, Mision mision) {
        return simular(nivelDelHeroe, escalon, mision, List.of());
    }

    private Ejecucion simular(int nivelDelHeroe, Escalon escalon, Mision mision,
                              List<nexus.misiones.dominio.EpicaDeTabla20> tabla20) {
        HeroeEnMision heroe = new HeroeEnMision("h-1", "Vorn", "Guerrero Armas", "p-armas", nivelDelHeroe, 0,
                8 * nivelDelHeroe, 44 * nivelDelHeroe, 11 * nivelDelHeroe);
        Ejecucion nueva = Ejecucion.nueva(UUID.randomUUID(), mision.id(), JUGADOR, heroe, List.of(), escalon, INICIO,
                Duration.ofHours(1), 7L, null);
        ejecuciones.guardar(nueva);
        ahora.set(INICIO.plus(Duration.ofHours(2)));
        Dobles.Catalogo catalogo = new Dobles.Catalogo(List.of(mision), tabla20);
        SimularEjecucion simular = new SimularEjecucion(catalogo, ejecuciones, eventos, heroes, motor,
                new PerfilDeCombateDelHeroe(inventario, productos, heroes), (prototipo, nivel) -> List.of(),
                parametros, reloj);
        Ejecucion terminada = simular.simular(ejecuciones.buscar(nueva.id()).orElseThrow()).orElseThrow();
        assertThat(terminada.estado()).isNotEqualTo(EstadoEjecucion.EN_PROGRESO);
        return terminada;
    }

    /** Lo que el motor recibio de cada rival, por prototipo (el primer encuentro de cada uno). */
    private Map<String, Combatiente> rivalesQueLlegaronAlMotor() {
        Map<String, Combatiente> porPrototipo = new LinkedHashMap<>();
        motor.recibidos.stream().flatMap(List::stream)
                .filter(c -> c.id().equals(Dobles.Motor.RIVAL))
                .forEach(c -> porPrototipo.putIfAbsent(c.prototipo(), c));
        return porPrototipo;
    }

    // ------------------------------------------------------------------ criterio 1

    @Test
    @DisplayName("Criterio 1: sus estadisticas superan las de los regulares de la mision, en cada nivel del heroe y escalon")
    void criterio1_estadisticasSuperiores() {
        for (Escalon escalon : List.of(Escalon.NORMAL, Escalon.HEROICO, Escalon.LEGENDARIO)) {
            for (int nivel = HeroeEnMision.NIVEL_MINIMO; nivel <= HeroeEnMision.NIVEL_MAXIMO; nivel++) {
                preparar();
                simular(nivel, escalon, mision(sombra()));
                Map<String, Combatiente> rivales = rivalesQueLlegaronAlMotor();
                EstadisticasDeCombate master = rivales.get(MASTER).estadisticas();
                String donde = "héroe de nivel " + nivel + ", escalón " + escalon;

                for (String regular : REGULARES) {
                    EstadisticasDeCombate contra = rivales.get(regular).estadisticas();
                    assertThat(master.vida()).as("vida frente a " + regular + ", " + donde).isGreaterThan(contra.vida());
                    assertThat(master.defensa()).as("defensa frente a " + regular + ", " + donde)
                            .isGreaterThan(contra.defensa());
                    assertThat(master.ataque().esperado()).as("ataque frente a " + regular + ", " + donde)
                            .isGreaterThan(contra.ataque().esperado());
                    assertThat(master.dano().esperado()).as("daño frente a " + regular + ", " + donde)
                            .isGreaterThan(contra.dano().esperado());
                }
            }
        }
    }

    // ------------------------------------------------------------------ criterio 2

    @Test
    @DisplayName("Criterio 2: aparece dos niveles por encima del heroe (RG-107), con las estadisticas de la vista de heroes en ese nivel")
    void criterio2_dosNivelesPorEncima() {
        for (int nivel = 1; nivel <= 6; nivel++) {
            preparar();
            Ejecucion terminada = simular(nivel, Escalon.NORMAL, mision(sombra()));

            Combatiente master = rivalesQueLlegaronAlMotor().get(MASTER);
            assertThat(master.nivel()).as("nivel del Master frente a un héroe de nivel " + nivel).isEqualTo(nivel + 2);
            assertThat(heroes.consultas).contains(MASTER + "@" + (nivel + 2));
            // El evento identifica al Master (lado y nivel) tanto cuando juega el como cuando el heroe lo tiene enfrente.
            List<EventoDeCombate.Actor> masters = eventos.de(terminada.id()).stream()
                    .flatMap(e -> java.util.stream.Stream.of(e.actor(), e.oponente()))
                    .filter(a -> a.lado() == EventoDeCombate.Lado.MASTER).toList();
            assertThat(masters).isNotEmpty().allSatisfy(a -> {
                assertThat(a.nivel()).isEqualTo(master.nivel());
                assertThat(a.prototipo()).isEqualTo(MASTER);
            });
        }
    }

    @Test
    @DisplayName("Criterio 2 (decision provisional del PO): el nivel maximo es 8, asi que contra un heroe de nivel 7 u 8 el Master es de nivel 8")
    void criterio2_tope8() {
        for (int nivel : List.of(7, 8)) {
            preparar();
            simular(nivel, Escalon.NORMAL, mision(sombra()));

            assertThat(rivalesQueLlegaronAlMotor().get(MASTER).nivel()).isEqualTo(HeroeEnMision.NIVEL_MAXIMO);
            // El servicio de heroes y el motor solo conocen del 1 al 8: nunca se les pide un nivel que no existe.
            assertThat(heroes.consultas).doesNotContain(MASTER + "@9", MASTER + "@10");
        }
    }

    @Test
    @DisplayName("Criterio 2: posee una habilidad epica exclusiva, la suya")
    void criterio2_poseeSuEpica() {
        Ejecucion terminada = simular(3, Escalon.NORMAL, mision(sombra()));

        assertThat(terminada.resultado().masters()).singleElement().satisfies(master -> {
            assertThat(master.nombre()).isEqualTo("Sombra del Olvido");
            assertThat(master.epica()).isEqualTo(VELO);
        });
    }

    @Test
    @DisplayName("Criterio 2: la epica del Master entra en el perfil que va al motor y la juega; los demas no llevan ninguna")
    void criterio2_laLlevaAlCombate() {
        motor.danoDelHeroe = 60;
        motor.danoDeLosEnemigos = 0;

        Ejecucion terminada = simular(3, Escalon.NORMAL, mision(new MasterDeMision("Hija de la Escarcha",
                "Mago Hielo", 1.0, FRIO)));

        Map<String, Combatiente> rivales = rivalesQueLlegaronAlMotor();
        assertThat(rivales.get("Mago Hielo").epicas()).containsExactly("Frío concentrado");
        REGULARES.forEach(regular -> assertThat(rivales.get(regular).epicas()).isEmpty());
        assertThat(motor.accionesPedidas).contains("Frío concentrado");
        assertThat(eventos.de(terminada.id()))
                .filteredOn(e -> e.actor().lado() == EventoDeCombate.Lado.MASTER && e.jugada() != null)
                .extracting(e -> e.jugada().ejecutada())
                .contains("Frío concentrado");
    }

    // ------------------------------------------------------------------ criterio 3

    private LiquidarEjecucion liquidador(Mision mision, Dobles.Libro libro) {
        Dobles.Directorio directorio = new Dobles.Directorio();
        directorio.contactos.put(JUGADOR, new DirectorioDeJugadores.Contacto("jugador@nexus.test", "Jugador"));
        return new LiquidarEjecucion(ejecuciones, new Dobles.Catalogo(List.of(mision), List.of()), inventario, libro,
                directorio, new Dobles.Correo(), parametros, reloj);
    }

    @Test
    @DisplayName("Criterio 3: al derrotar al Master el jugador obtiene su epica, al 100 %, una sola vez por ejecucion")
    void criterio3_laEpicaSeEntrega() {
        MasterDeMision hija = new MasterDeMision("Hija de la Escarcha", "Mago Hielo", 1.0, FRIO);
        Mision mision = mision(hija);
        Ejecucion terminada = simular(2, Escalon.NORMAL, mision);

        assertThat(terminada.resultado().masters()).singleElement().satisfies(m -> assertThat(m.derrotado()).isTrue());
        assertThat(terminada.recompensas().epicas()).singleElement().satisfies(epica -> {
            assertThat(epica.nombre()).isEqualTo("Frío concentrado");
            assertThat(epica.master()).isEqualTo("Hija de la Escarcha");
            assertThat(epica.entregable()).isTrue();
        });

        LiquidarEjecucion liquidar = liquidador(mision, new Dobles.Libro());
        Ejecucion liquidada = liquidar.liquidar(terminada);
        liquidar.liquidar(liquidada);

        String clave = "mision-" + terminada.id() + "-epica";
        assertThat(inventario.clavesDeEntrega).containsOnlyOnce(clave);
        assertThat(inventario.entregas).anySatisfy(entrega -> assertThat(entrega).containsExactly(
                new InventarioDeHeroes.ProductoAEntregar("978446ae-c979-349b-b620-f4215852ab5f", 1)));
        assertThat(liquidada.estadoDe(PasoDeLiquidacion.EPICA)).isEqualTo(nexus.misiones.dominio.EstadoDePaso.HECHO);
    }

    @Test
    @DisplayName("Criterio 3: un Master que aparece y no cae no entrega la epica")
    void criterio3_sinVictoriaNoHayEpica() {
        motor.danoDelHeroe = 0;
        motor.danoDeLosEnemigos = 100_000;

        Ejecucion terminada = simular(3, Escalon.NORMAL, mision(new MasterDeMision("Hija de la Escarcha",
                "Mago Hielo", 1.0, FRIO)));

        assertThat(terminada.estado()).isEqualTo(EstadoEjecucion.FALLIDA);
        assertThat(terminada.recompensas().epicas()).isEmpty();
        assertThat(terminada.pasos()).doesNotContainKey(PasoDeLiquidacion.EPICA);
    }

    @Test
    @DisplayName("Criterio 3: una epica fuera del catalogo oficial (la del ejemplo del documento) queda en la coleccion y se informa")
    void criterio3_epicaSinProducto() {
        Ejecucion terminada = simular(2, Escalon.NORMAL, mision(sombra()));

        RecompensasDeEjecucion recompensas = terminada.recompensas();
        assertThat(recompensas.epicas()).singleElement().satisfies(epica -> {
            assertThat(epica.nombre()).isEqualTo("Velo de Sombras");
            assertThat(epica.entregable()).isFalse();
        });
        assertThat(recompensas.sinEntregar()).anySatisfy(s -> assertThat(s.nombre()).contains("Velo de Sombras"));
        assertThat(terminada.pasos()).doesNotContainKey(PasoDeLiquidacion.EPICA);
    }

    // ------------------------------------------------------------------ criterio 4

    @Test
    @DisplayName("Criterio 4: sin Master en la mision ni fila de la Tabla 20 que le toque al heroe, no aparece ninguno ni se obtiene epica")
    void criterio4_sinMasterNoHayEpica() {
        Ejecucion terminada = simular(5, Escalon.NORMAL, mision());

        assertThat(terminada.resultado().masters()).isEmpty();
        assertThat(rivalesQueLlegaronAlMotor().keySet()).doesNotContain(MASTER);
        assertThat(eventos.de(terminada.id()))
                .noneMatch(e -> e.actor().lado() == EventoDeCombate.Lado.MASTER);
        assertThat(terminada.recompensas().epicas()).isEmpty();
        assertThat(terminada.pasos()).doesNotContainKey(PasoDeLiquidacion.EPICA);
        assertThat(inventario.clavesDeEntrega).isEmpty();
    }
}
