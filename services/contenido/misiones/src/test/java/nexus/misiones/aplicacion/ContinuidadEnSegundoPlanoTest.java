package nexus.misiones.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import nexus.misiones.configuracion.TrabajoProgramado;
import nexus.misiones.dominio.Categoria;
import nexus.misiones.dominio.Dificultad;
import nexus.misiones.dominio.Ejecucion;
import nexus.misiones.dominio.Epica;
import nexus.misiones.dominio.EpicaDeTabla20;
import nexus.misiones.dominio.EstadoDePaso;
import nexus.misiones.dominio.EstadoEjecucion;
import nexus.misiones.dominio.GrupoDeEnemigos;
import nexus.misiones.dominio.Jefe;
import nexus.misiones.dominio.Mision;
import nexus.misiones.dominio.Misiones;
import nexus.misiones.dominio.Objetivo;
import nexus.misiones.dominio.Origen;
import nexus.misiones.dominio.ParametrosDeRecompensa;
import nexus.misiones.dominio.PasoDeLiquidacion;
import nexus.misiones.dominio.RecompensasDeMision;
import nexus.misiones.dominio.RepositorioDeEventosDeCombate;
import nexus.misiones.dominio.TipoDeObjetivo;
import nexus.misiones.dominio.simulacion.DecisorDeTurno;
import nexus.misiones.dominio.simulacion.EventoDeCombate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;

/**
 * HU-SIM-007 (#122), «Continuidad de misiones en segundo plano» (7.8.12, RNF-31 y RNF-32): una prueba por criterio
 * de aceptacion, con el reloj controlado y sin que nadie consulte nada, que es la evidencia automatica que pide el
 * docente. El nombre de cada metodo empieza por el criterio que demuestra.
 *
 * <p>Una mision no se simula a lo largo de su duracion: el tiempo solo gobierna CUANDO se puede ver el resultado, y
 * la simulacion entera corre en lote en cuanto el plazo vence ({@link SimularEjecucion}). «Continuar en segundo
 * plano» es, entonces, que el trabajo programado la tome sola cuando vence, que lo guardado sobreviva a quien deja de
 * mirar, que un fallo no la deje a medias y que el aviso de fin salga aunque nadie este conectado.
 */
class ContinuidadEnSegundoPlanoTest {

    private static final Instant INICIO = Instant.parse("2026-10-01T10:00:00Z");
    private static final String JUGADOR = "11111111-1111-4111-8111-111111111111";
    private static final Duration ARRIENDO = Ejecucion.ARRIENDO_DE_SIMULACION;
    /** La espera tras el primer fallo de la simulacion (la de #851: 30 s, 1, 2 y 4 min, con tope de 5). */
    private static final Duration ESPERA_BASE = Duration.ofSeconds(30);

    private static final EpicaDeTabla20 ARMAS_SEGURA = new EpicaDeTabla20("Guerrero Armas",
            new Epica("Segundo impulso", "Recupera 1d4 de vida", "+3 a la vida", "4481eb34-384a-3fa0-ba9a-1aac9562c38f"),
            100);
    private static final String COFRE = "6d4f0b4c-5a3e-3d1c-8e2a-0000000000c1";

    /** Un proceso que muere a mitad de la simulacion: un Error, que ningun {@code catch} de la aplicacion atrapa. */
    static final class ProcesoMuerto extends Error {
        ProcesoMuerto() {
            super("el proceso murio a mitad de la simulacion");
        }
    }

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
    private Dobles.Inventario inventario;
    private Dobles.Heroes heroes;
    private Dobles.Motor motor;
    private Dobles.Eventos eventos;
    private Dobles.Productos productos;
    private Dobles.Libro libro;
    private Dobles.Correo correo;
    private Dobles.Avisos avisos;
    private Dobles.Directorio directorio;
    private Dobles.Catalogo catalogo;
    private MatricularHeroe matricular;
    private SimularEjecucion simular;
    private LiquidarEjecucion liquidar;
    private TrabajoDeMisiones trabajo;
    private TrabajoProgramado programador;
    private CancelarEjecucion cancelar;
    private ConsultarEjecuciones consultar;
    private RepositorioDeEventosDeCombate eventosConFallo;
    /** Lo que ocurre cada vez que el decisor de turnos es consultado: la ventana para meter fallos a mitad. */
    private Runnable alDecidir = () -> { };

    private ListAppender<ILoggingEvent> bitacora;
    private Logger bitacoraDeAplicacion;

    @BeforeEach
    void preparar() {
        prepararCon(List.of(), 20, null);
        bitacora = new ListAppender<>();
        bitacora.start();
        bitacoraDeAplicacion = (Logger) LoggerFactory.getLogger("nexus.misiones.aplicacion");
        bitacoraDeAplicacion.setLevel(Level.INFO);
        bitacoraDeAplicacion.addAppender(bitacora);
    }

    @AfterEach
    void soltarBitacora() {
        bitacoraDeAplicacion.detachAppender(bitacora);
    }

    /**
     * @param lote       cuantas ejecuciones atiende cada vuelta
     * @param eventosDe  si no es nulo, los turnos de esa ejecucion no se pueden guardar (una ejecucion «envenenada»)
     */
    private void prepararCon(List<EpicaDeTabla20> tabla20, int lote, UUID eventosDe) {
        ejecuciones = new Dobles.Ejecuciones();
        inventario = new Dobles.Inventario()
                .conHeroe("h-1", JUGADOR, "p-armas", true)
                .conHeroe("h-2", JUGADOR, "p-armas", true);
        productos = new Dobles.Productos();
        productos.prototipos.put("p-armas", "Guerrero Armas");
        heroes = new Dobles.Heroes();
        motor = new Dobles.Motor();
        eventos = new Dobles.Eventos();
        libro = new Dobles.Libro();
        correo = new Dobles.Correo();
        avisos = new Dobles.Avisos();
        directorio = new Dobles.Directorio();
        directorio.contactos.put(JUGADOR, new DirectorioDeJugadores.Contacto("vorn@ejemplo.com", "vorn"));
        ParametrosDeMisiones parametros = new ParametrosDeMisiones(Duration.ofHours(1), ESPERA_BASE, lote, true, true,
                null, null, new ParametrosDeRecompensa(Map.of(), Map.of(), false));
        catalogo = new Dobles.Catalogo(List.of(Misiones.templo(), Misiones.historia("prueba-corta", List.of()),
                Misiones.historia("prueba-dos", List.of()), unaConDeTodo(),
                Misiones.historiaTrasDe("tras-la-completa", "prueba-completa")), tabla20);
        matricular = new MatricularHeroe(catalogo, ejecuciones, new Dobles.Estrategias(), inventario, productos,
                heroes, parametros, reloj, () -> 7L);
        armar(parametros);
    }

    private void armar(ParametrosDeMisiones parametros) {
        DecisorDeTurno decisor = turno -> {
            alDecidir.run();
            return heroes.decidir(turno);
        };
        RepositorioDeEventosDeCombate paraSimular = new RepositorioDeEventosDeCombate() {
            @Override
            public void reemplazar(UUID ejecucionId, List<EventoDeCombate> turnos) {
                if (eventosConFallo != null) {
                    eventosConFallo.reemplazar(ejecucionId, turnos);
                    return;
                }
                eventos.reemplazar(ejecucionId, turnos);
            }

            @Override
            public List<EventoDeCombate> de(UUID ejecucionId) {
                return eventos.de(ejecucionId);
            }
        };
        simular = new SimularEjecucion(catalogo, ejecuciones, paraSimular, heroes, decisor, motor,
                new PerfilDeCombateDelHeroe(inventario, productos, heroes),
                new RotacionesPorDefectoDeEnemigos(heroes), parametros, reloj);
        liquidar = new LiquidarEjecucion(ejecuciones, catalogo, inventario, libro, directorio, correo, avisos,
                parametros, reloj);
        trabajo = new TrabajoDeMisiones(ejecuciones, simular, liquidar, parametros, reloj);
        programador = new TrabajoProgramado(trabajo);
        cancelar = new CancelarEjecucion(ejecuciones, liquidar, reloj);
        consultar = new ConsultarEjecuciones(catalogo, ejecuciones);
    }

    /** Una mision corta cuya recompensa lleva un objeto del catalogo: con ella aparecen los seis pasos de entrega. */
    private static Mision unaConDeTodo() {
        return new Mision("prueba-completa", Origen.PROVISIONAL_DEV, "Misión de todo", Categoria.HISTORIA,
                "Descripción", null, Dificultad.FACIL, 1, null, List.of(), "Narrativa", null,
                List.of(new Objetivo("Derrotar al jefe.", true, TipoDeObjetivo.DERROTAR_JEFE, null, null)),
                List.of(new GrupoDeEnemigos("Enemigo de prueba", 1, null, "Guerrero Armas", 4, 5, List.of())),
                new Jefe("Jefe de prueba", "Guerrero Tanque", 6, 5, null, List.of()),
                List.of(),
                new RecompensasDeMision(5,
                        List.of(new RecompensasDeMision.ObjetoDeRecompensa("Cofre de Bronce", "items comunes", 1,
                                COFRE)),
                        List.of(), List.of(), new RecompensasDeMision.PrimeraVez(2, List.of())),
                false, null, null);
    }

    private Ejecucion enviar(String mision, String heroe) {
        return matricular.matricular(JUGADOR, new SolicitudDeMatricula(mision, heroe, List.of(), null, null))
                .ejecucion();
    }

    private Ejecucion leer(Ejecucion ejecucion) {
        return ejecuciones.buscar(ejecucion.id()).orElseThrow();
    }

    private void pasarA(Duration desdeElInicio) {
        ahora.set(INICIO.plus(desdeElInicio));
    }

    private void esperar(Duration tiempo) {
        ahora.set(ahora.get().plus(tiempo));
    }

    private List<String> mensajesDeLaBitacora() {
        return bitacora.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    // =============================================================== criterio 1

    @Test
    @DisplayName("criterio 1: vencido el plazo, la vuelta programada la simula sola, sin que el jugador consulte nada")
    void criterio1_laMisionVencidaSeSimulaSolaCuandoElProgramadorDaSuVuelta() {
        Ejecucion ejecucion = enviar("prueba-corta", "h-1");

        for (Duration minutos : List.of(Duration.ofMinutes(10), Duration.ofMinutes(30), Duration.ofMinutes(59))) {
            pasarA(minutos);
            programador.darUnaVuelta();
            assertThat(leer(ejecucion).estado()).isEqualTo(EstadoEjecucion.EN_PROGRESO);
            assertThat(motor.llamadas).as("antes del plazo no se le pide nada al motor").isEmpty();
        }
        assertThat(inventario.bloqueados).containsKey("h-1");

        pasarA(Duration.ofHours(1));
        programador.darUnaVuelta();

        Ejecucion terminada = leer(ejecucion);
        assertThat(terminada.estado()).isEqualTo(EstadoEjecucion.COMPLETADA);
        assertThat(terminada.liquidacionPendiente()).isFalse();
        assertThat(inventario.bloqueados).isEmpty();
        assertThat(libro.acreditado).containsKey("mision-" + ejecucion.id());
        assertThat(eventos.de(ejecucion.id())).isNotEmpty();
    }

    @Test
    @DisplayName("criterio 1: varias misiones vencidas se simulan todas en la misma vuelta, una tras otra")
    void criterio1_variasVencidasSeAtiendenEnLaMismaVuelta() {
        Ejecucion primera = enviar("prueba-corta", "h-1");
        Ejecucion segunda = enviar("prueba-dos", "h-2");
        pasarA(Duration.ofHours(1));

        programador.darUnaVuelta();

        assertThat(leer(primera).estado()).isEqualTo(EstadoEjecucion.COMPLETADA);
        assertThat(leer(segunda).estado()).isEqualTo(EstadoEjecucion.COMPLETADA);
    }

    @Test
    @DisplayName("criterio 1: una ejecucion que siempre falla no deja sin atender a las demas aunque el lote sea de una")
    void criterio1_unaEnvenenadaNoDejaSinAtenderALasDemas() {
        Ejecucion envenenada = enviar("prueba-corta", "h-1");
        esperar(Duration.ofMinutes(1));
        Ejecucion sana = enviar("prueba-dos", "h-2");
        prepararConLaMismaBase(1, envenenada.id());
        pasarA(Duration.ofHours(1).plusMinutes(2));

        programador.darUnaVuelta();
        assertThat(leer(envenenada).estado()).isEqualTo(EstadoEjecucion.EN_PROGRESO);
        programador.darUnaVuelta();

        assertThat(leer(sana).estado())
                .as("la sana se atiende en la segunda vuelta, aunque la envenenada sea la primera de la fila")
                .isEqualTo(EstadoEjecucion.COMPLETADA);
        assertThat(leer(envenenada).estado()).isEqualTo(EstadoEjecucion.EN_PROGRESO);
        assertThat(inventario.bloqueados).containsKey("h-1").doesNotContainKey("h-2");
    }

    /** Vuelve a armar los casos de uso con otro lote y una ejecucion cuyos turnos no se pueden guardar. */
    private void prepararConLaMismaBase(int lote, UUID sinGuardarTurnos) {
        eventosConFallo = new RepositorioDeEventosDeCombate() {
            @Override
            public void reemplazar(UUID ejecucionId, List<EventoDeCombate> turnos) {
                if (ejecucionId.equals(sinGuardarTurnos)) {
                    throw Dobles.caido("mongo");
                }
                eventos.reemplazar(ejecucionId, turnos);
            }

            @Override
            public List<EventoDeCombate> de(UUID ejecucionId) {
                return eventos.de(ejecucionId);
            }
        };
        armar(new ParametrosDeMisiones(Duration.ofHours(1), ESPERA_BASE, lote, true, true, null, null,
                new ParametrosDeRecompensa(Map.of(), Map.of(), false)));
    }

    // =============================================================== criterio 2

    @Test
    @DisplayName("criterio 2: al volver, la mision en curso conserva su heroe, su plazo y su progreso")
    void criterio2_alVolverLaMisionEnCursoConservaSuEstado() {
        Ejecucion ejecucion = enviar("templo-olvidado", "h-1");

        pasarA(Duration.ofHours(3));
        programador.darUnaVuelta();
        List<EjecucionConMision> enCurso = consultar.enCurso(JUGADOR);

        assertThat(enCurso).hasSize(1);
        Ejecucion vista = enCurso.getFirst().ejecucion();
        assertThat(vista.id()).isEqualTo(ejecucion.id());
        assertThat(vista.estado()).isEqualTo(EstadoEjecucion.EN_PROGRESO);
        assertThat(vista.heroe().id()).isEqualTo("h-1");
        assertThat(vista.terminaEn()).isEqualTo(INICIO.plus(Duration.ofHours(12)));
        assertThat(vista.progreso(ahora.get())).isEqualTo(0.25);
        assertThat(inventario.bloqueados).containsKey("h-1");

        pasarA(Duration.ofHours(6));
        programador.darUnaVuelta();
        Ejecucion mitad = consultar.enCurso(JUGADOR).getFirst().ejecucion();
        assertThat(mitad.terminaEn()).isEqualTo(vista.terminaEn());
        assertThat(mitad.progreso(ahora.get())).isEqualTo(0.5);
    }

    @Test
    @DisplayName("criterio 2: la mision que termino mientras el jugador no miraba tiene su reporte y su historial al volver")
    void criterio2_laQueTerminoEnSegundoPlanoTieneSuReporteAlVolver() {
        Ejecucion ejecucion = enviar("prueba-corta", "h-1");
        pasarA(Duration.ofHours(1));
        programador.darUnaVuelta();

        EjecucionConMision reporte = consultar.reporte(JUGADOR, ejecucion.id());

        assertThat(reporte.ejecucion().estado()).isEqualTo(EstadoEjecucion.COMPLETADA);
        assertThat(reporte.ejecucion().resultado()).isNotNull();
        assertThat(reporte.ejecucion().recompensas().creditos()).isEqualTo(7);
        assertThat(consultar.enCurso(JUGADOR)).isEmpty();
        assertThat(consultar.historial(JUGADOR).terminadas()).extracting(t -> t.ejecucion().id())
                .containsExactly(ejecucion.id());
    }

    @Test
    @DisplayName("criterio 2: tras un fallo de la simulacion la mision sigue en curso, entera, y se puede consultar")
    void criterio2_trasUnFalloSigueEnCursoYConsultable() {
        Ejecucion ejecucion = enviar("prueba-corta", "h-1");
        heroes.fallarAlDecidir = Dobles.caido("heroes");
        pasarA(Duration.ofHours(1));

        programador.darUnaVuelta();

        List<EjecucionConMision> enCurso = consultar.enCurso(JUGADOR);
        assertThat(enCurso).extracting(t -> t.ejecucion().id()).containsExactly(ejecucion.id());
        Ejecucion vista = enCurso.getFirst().ejecucion();
        assertThat(vista.estado()).isEqualTo(EstadoEjecucion.EN_PROGRESO);
        assertThat(vista.resultado()).isNull();
        assertThat(vista.recompensas()).isNull();
        assertThat(vista.heroe().id()).isEqualTo("h-1");
        assertThat(vista.progreso(ahora.get())).isEqualTo(1.0);
        assertThat(inventario.bloqueados).containsKey("h-1");
    }

    // =============================================================== criterio 3

    @Test
    @DisplayName("criterio 3: si la simulacion falla, los turnos y la ejecucion quedan como estaban; al recuperarse se escribe una sola vez")
    void criterio3_unFalloAMitadNoDejaNadaGuardado() {
        Ejecucion ejecucion = enviar("prueba-corta", "h-1");
        motor.fallar = Dobles.caido("motor-combate");
        pasarA(Duration.ofHours(1));

        programador.darUnaVuelta();

        assertThat(leer(ejecucion).estado()).isEqualTo(EstadoEjecucion.EN_PROGRESO);
        assertThat(leer(ejecucion).pasos()).isEmpty();
        assertThat(eventos.escrituras).isZero();
        assertThat(inventario.bloqueados).containsKey("h-1");

        motor.fallar = null;
        esperar(ESPERA_BASE);
        programador.darUnaVuelta();

        assertThat(leer(ejecucion).estado()).isEqualTo(EstadoEjecucion.COMPLETADA);
        assertThat(eventos.escrituras).isEqualTo(1);
    }

    @Test
    @DisplayName("criterio 3: si se cae al guardar la ejecucion con los turnos ya escritos, el reintento los reemplaza, no los mezcla")
    void criterio3_unaCaidaDespuesDeEscribirLosTurnosNoMezclaDosIntentos() {
        Ejecucion ejecucion = enviar("prueba-corta", "h-1");
        pasarA(Duration.ofHours(1));
        ejecuciones.fallarUnaVezSi = e -> e.estado() != EstadoEjecucion.EN_PROGRESO;

        programador.darUnaVuelta();

        assertThat(leer(ejecucion).estado()).isEqualTo(EstadoEjecucion.EN_PROGRESO);
        List<EventoDeCombate> delPrimerIntento = eventos.de(ejecucion.id());
        assertThat(delPrimerIntento).isNotEmpty();

        esperar(ESPERA_BASE);
        programador.darUnaVuelta();

        assertThat(leer(ejecucion).estado()).isEqualTo(EstadoEjecucion.COMPLETADA);
        assertThat(eventos.escrituras).isEqualTo(2);
        assertThat(eventos.de(ejecucion.id())).hasSameSizeAs(delPrimerIntento);
    }

    @Test
    @DisplayName("criterio 3: un fallo que se repite se aplaza con espera exponencial y deja anotado el error")
    void criterio3_unFalloQueSeRepiteSeAplazaConEsperaExponencial() {
        Ejecucion ejecucion = enviar("prueba-corta", "h-1");
        heroes.fallarAlDecidir = Dobles.caido("heroes");
        pasarA(Duration.ofHours(1));
        int llamadasAlPrimerIntento;

        programador.darUnaVuelta();
        llamadasAlPrimerIntento = heroes.decisiones.size();
        assertThat(llamadasAlPrimerIntento).isPositive();

        esperar(ESPERA_BASE.minusSeconds(1));
        programador.darUnaVuelta();
        assertThat(heroes.decisiones).as("dentro de la espera no se vuelve a intentar").hasSize(llamadasAlPrimerIntento);

        esperar(Duration.ofSeconds(1));
        programador.darUnaVuelta();
        int alSegundoIntento = heroes.decisiones.size();
        assertThat(alSegundoIntento).isGreaterThan(llamadasAlPrimerIntento);

        esperar(ESPERA_BASE.multipliedBy(2).minusSeconds(1));
        programador.darUnaVuelta();
        assertThat(heroes.decisiones).as("la segunda espera es el doble").hasSize(alSegundoIntento);

        esperar(Duration.ofSeconds(1));
        programador.darUnaVuelta();
        assertThat(heroes.decisiones.size()).isGreaterThan(alSegundoIntento);
        assertThat(leer(ejecucion).estado()).isEqualTo(EstadoEjecucion.EN_PROGRESO);
    }

    @Test
    @DisplayName("criterio 3: la espera entre intentos de una simulacion que falla es la de siempre (30 s, 1, 2 y 4 min) y nunca pasa de cinco minutos")
    void criterio3_laEsperaTieneElTopeDeCincoMinutos() {
        Ejecucion ejecucion = enviar("prueba-corta", "h-1");
        heroes.fallarAlDecidir = Dobles.caido("heroes");
        pasarA(Duration.ofHours(1));
        List<Duration> esperas = new ArrayList<>();

        for (int intento = 1; intento <= 8; intento++) {
            Instant antes = ahora.get();
            programador.darUnaVuelta();
            esperas.add(Duration.between(antes, leer(ejecucion).proximoIntento()));
            ahora.set(leer(ejecucion).proximoIntento());
        }

        assertThat(esperas).containsExactly(Duration.ofSeconds(30), Duration.ofMinutes(1), Duration.ofMinutes(2),
                Duration.ofMinutes(4), Duration.ofMinutes(5), Duration.ofMinutes(5), Duration.ofMinutes(5),
                Duration.ofMinutes(5));
        assertThat(Ejecucion.ESPERA_MAXIMA_ANTES_DE_SIMULAR).isEqualTo(Duration.ofMinutes(5));
        assertThat(leer(ejecucion).ultimoError()).isNotBlank();
    }

    @Test
    @DisplayName("criterio 3: la reserva y el aplazamiento conviven: tras un fallo manda la espera corta, no el arriendo de cinco minutos")
    void criterio3_tras_un_fallo_manda_la_espera_y_no_el_arriendo() {
        Ejecucion ejecucion = enviar("prueba-corta", "h-1");
        heroes.fallarAlDecidir = Dobles.caido("heroes");
        pasarA(Duration.ofHours(1));

        programador.darUnaVuelta();

        Ejecucion aplazada = leer(ejecucion);
        assertThat(aplazada.intentosDeSimulacion()).isEqualTo(1);
        assertThat(aplazada.proximoIntento()).isEqualTo(ahora.get().plus(ESPERA_BASE));
        assertThat(aplazada.proximoIntento()).isBefore(ahora.get().plus(ARRIENDO));
    }

    @Test
    @DisplayName("criterio 3: la mision envenenada se recupera sola en cuanto el servicio vuelve, y el heroe se libera")
    void criterio3_laEnvenenadaSeRecuperaCuandoElServicioVuelve() {
        Ejecucion ejecucion = enviar("prueba-corta", "h-1");
        heroes.fallarAlDecidir = Dobles.caido("heroes");
        pasarA(Duration.ofHours(1));
        for (int i = 0; i < 5; i++) {
            programador.darUnaVuelta();
            esperar(Duration.ofHours(1));
        }
        assertThat(leer(ejecucion).estado()).isEqualTo(EstadoEjecucion.EN_PROGRESO);
        assertThat(inventario.bloqueados).containsKey("h-1");

        heroes.fallarAlDecidir = null;
        programador.darUnaVuelta();

        assertThat(leer(ejecucion).estado()).isEqualTo(EstadoEjecucion.COMPLETADA);
        assertThat(inventario.bloqueados).isEmpty();
    }

    @Test
    @DisplayName("criterio 3: dos barridos que leyeron lo mismo simulan la ejecucion una sola vez")
    void criterio3_dosBarridosQueLeyeronLoMismoSimulanUnaVez() {
        enviar("prueba-corta", "h-1");
        pasarA(Duration.ofHours(1));
        Ejecucion delBarridoA = ejecuciones.vencidas(ahora.get(), 20).getFirst();
        Ejecucion delBarridoB = ejecuciones.vencidas(ahora.get(), 20).getFirst();

        Optional<Ejecucion> a = simular.simular(delBarridoA);
        Optional<Ejecucion> b = simular.simular(delBarridoB);

        assertThat(a).isPresent();
        assertThat(b).as("el segundo barrido ya la encuentra tomada o terminada").isEmpty();
        assertThat(eventos.escrituras).isEqualTo(1);
        assertThat(leer(delBarridoA).estado()).isEqualTo(EstadoEjecucion.COMPLETADA);
    }

    @Test
    @DisplayName("criterio 3: ocho hilos sobre la misma ejecucion vencida la simulan una sola vez")
    void criterio3_ochoHilosLaSimulanUnaSolaVez() throws Exception {
        Ejecucion ejecucion = enviar("prueba-corta", "h-1");
        pasarA(Duration.ofHours(1));
        ExecutorService hilos = Executors.newFixedThreadPool(8);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Optional<Ejecucion>>> resultados = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Ejecucion copia = leer(ejecucion);
            resultados.add(hilos.submit(() -> {
                salida.await();
                return simular.simular(copia);
            }));
        }
        salida.countDown();

        int simuladas = 0;
        for (Future<Optional<Ejecucion>> resultado : resultados) {
            try {
                if (resultado.get(30, TimeUnit.SECONDS).isPresent()) {
                    simuladas++;
                }
            } catch (ExecutionException perdioLaCarrera) {
                // Perder la carrera tambien es valido, pero solo una de las ocho gana.
            }
        }
        hilos.shutdownNow();

        assertThat(simuladas).isEqualTo(1);
        assertThat(eventos.escrituras).isEqualTo(1);
        assertThat(leer(ejecucion).estado()).isEqualTo(EstadoEjecucion.COMPLETADA);
    }

    @Test
    @DisplayName("criterio 3: si el proceso muere simulando, nadie la repite hasta que vence el arriendo; entonces la retoma otra vuelta")
    void criterio3_siElProcesoMuereLaRetomaOtraVueltaAlVencerElArriendo() {
        Ejecucion ejecucion = enviar("prueba-corta", "h-1");
        pasarA(Duration.ofHours(1));
        alDecidir = () -> {
            throw new ProcesoMuerto();
        };

        assertThatThrownBy(() -> programador.darUnaVuelta()).isInstanceOf(ProcesoMuerto.class);
        assertThat(leer(ejecucion).estado()).isEqualTo(EstadoEjecucion.EN_PROGRESO);
        assertThat(eventos.escrituras).isZero();
        alDecidir = () -> { };
        int decisionesAntes = heroes.decisiones.size();

        esperar(ARRIENDO.minusSeconds(1));
        programador.darUnaVuelta();
        assertThat(heroes.decisiones).as("otra instancia la tiene reservada: no se repite").hasSize(decisionesAntes);
        assertThat(leer(ejecucion).estado()).isEqualTo(EstadoEjecucion.EN_PROGRESO);

        esperar(Duration.ofSeconds(1));
        programador.darUnaVuelta();
        assertThat(leer(ejecucion).estado()).isEqualTo(EstadoEjecucion.COMPLETADA);
        assertThat(eventos.escrituras).isEqualTo(1);
    }

    @Test
    @DisplayName("criterio 3: si el arriendo se vence mientras se simula, no se escriben turnos que otra instancia podria pisar")
    void criterio3_siElArriendoSeVenceSimulandoNoSePisanLosTurnos() {
        Ejecucion ejecucion = enviar("prueba-corta", "h-1");
        pasarA(Duration.ofHours(1));
        AtomicReference<Boolean> yaPaso = new AtomicReference<>(false);
        alDecidir = () -> {
            if (!yaPaso.getAndSet(true)) {
                esperar(ARRIENDO.plusSeconds(1));
            }
        };

        programador.darUnaVuelta();

        assertThat(leer(ejecucion).estado()).isEqualTo(EstadoEjecucion.EN_PROGRESO);
        assertThat(eventos.escrituras).isZero();

        programador.darUnaVuelta();
        assertThat(leer(ejecucion).estado()).isEqualTo(EstadoEjecucion.COMPLETADA);
        assertThat(eventos.escrituras).isEqualTo(1);
    }

    @Test
    @DisplayName("criterio 3: si el jugador cancela mientras se simula, queda Abandonada, con el heroe libre y sin turnos huerfanos")
    void criterio3_cancelarSimulandoNoDejaTurnosHuerfanos() {
        Ejecucion ejecucion = enviar("prueba-corta", "h-1");
        pasarA(Duration.ofHours(1));
        AtomicReference<Boolean> yaPaso = new AtomicReference<>(false);
        alDecidir = () -> {
            if (!yaPaso.getAndSet(true)) {
                cancelar.cancelar(JUGADOR, ejecucion.id());
            }
        };

        programador.darUnaVuelta();

        Ejecucion abandonada = leer(ejecucion);
        assertThat(abandonada.estado()).isEqualTo(EstadoEjecucion.ABANDONADA);
        assertThat(abandonada.recompensas()).isNull();
        assertThat(inventario.bloqueados).isEmpty();
        assertThat(eventos.de(ejecucion.id())).as("los turnos de una mision abandonada no se quedan").isEmpty();
        assertThat(libro.acreditado).isEmpty();
    }

    /** Los seis pasos de entrega de una mision que gana creditos, botin, una epica y escribe al jugador. */
    private Ejecucion misionConTodosLosPasos() {
        prepararCon(List.of(ARMAS_SEGURA), 20, null);
        heroes.vidaDeLosEnemigos = 5;
        return enviar("prueba-completa", "h-1");
    }

    @ParameterizedTest(name = "criterio 3: sin respuesta en el paso {0} la mision queda terminada y se completa despues sin repetir lo hecho")
    @EnumSource(PasoDeLiquidacion.class)
    void criterio3_unFalloEnCadaPasoDeLaLiquidacionSeRecupera(PasoDeLiquidacion paso) {
        Ejecucion ejecucion = misionConTodosLosPasos();
        String referencia = "mision-" + ejecucion.id();
        fallar(paso, referencia);
        pasarA(Duration.ofHours(1));

        programador.darUnaVuelta();

        Ejecucion tras = leer(ejecucion);
        assertThat(tras.estado()).isIn(EstadoEjecucion.COMPLETADA, EstadoEjecucion.FALLIDA);
        assertThat(tras.resultado()).isNotNull();
        assertThat(tras.recompensas()).isNotNull();
        assertThat(tras.pasos().keySet()).containsExactlyInAnyOrder(PasoDeLiquidacion.values());
        assertThat(tras.estadoDe(paso)).isEqualTo(EstadoDePaso.PENDIENTE);
        for (PasoDeLiquidacion anterior : PasoDeLiquidacion.values()) {
            if (anterior.ordinal() < paso.ordinal()) {
                assertThat(tras.estadoDe(anterior)).as("lo anterior a %s ya esta hecho", paso)
                        .isEqualTo(EstadoDePaso.HECHO);
            }
        }
        assertThat(tras.liquidacionPendiente()).isTrue();

        dejarDeFallar();
        esperar(ESPERA_BASE.plusSeconds(1));
        programador.darUnaVuelta();

        Ejecucion lista = leer(ejecucion);
        assertThat(lista.liquidacionPendiente()).isFalse();
        assertThat(lista.pasos().values()).containsOnly(EstadoDePaso.HECHO);
        assertThat(inventario.bloqueados).isEmpty();
        assertThat(libro.acreditado).containsOnlyKeys(referencia);
        assertThat(inventario.clavesDeEntrega).containsExactlyInAnyOrder(referencia + "-botin", referencia + "-epica");
        assertThat(correo.enviados).containsOnlyKeys(referencia + "-correo", referencia + "-correo-epica");
        assertThat(avisos.enBandeja.keySet()).containsExactlyInAnyOrder(referencia + "-aviso",
                referencia + "-aviso-epica", referencia + "-aviso-desbloqueo-tras-la-completa");
        // Lo que ya estaba hecho antes del fallo no se repitio: un solo intento exitoso de cada cosa.
        assertThat(inventario.llamadas.stream().filter(l -> l.startsWith("liberar")))
                .hasSize(paso == PasoDeLiquidacion.LIBERACION ? 2 : 1);
        assertThat(libro.intentos).hasSize(paso == PasoDeLiquidacion.CREDITOS ? 2 : 1);
    }

    private void fallar(PasoDeLiquidacion paso, String referencia) {
        switch (paso) {
            case LIBERACION -> inventario.fallarAlLiberar = Dobles.caido("inventario");
            case CREDITOS -> libro.fallar = Dobles.caido("ms-finanzas");
            case BOTIN -> inventario.fallarEntregaSi = clave -> clave.equals(referencia + "-botin");
            case EPICA -> inventario.fallarEntregaSi = clave -> clave.equals(referencia + "-epica");
            case CORREO -> correo.fallarClaveSi = clave -> clave.equals(referencia + "-correo");
            case CORREO_EPICA -> correo.fallarClaveSi = clave -> clave.equals(referencia + "-correo-epica");
            case AVISO -> avisos.fallarIdSi = id -> id.equals(referencia + "-aviso");
            case AVISO_EPICA -> avisos.fallarIdSi = id -> id.equals(referencia + "-aviso-epica");
            case AVISO_DESBLOQUEO -> avisos.fallarIdSi = id -> id.startsWith(referencia + "-aviso-desbloqueo");
        }
    }

    private void dejarDeFallar() {
        inventario.fallarAlLiberar = null;
        inventario.fallarEntregaSi = null;
        libro.fallar = null;
        correo.fallarClaveSi = null;
        avisos.fallarIdSi = null;
    }

    @Test
    @DisplayName("criterio 3: si el proceso cae con todas las entregas hechas pero sin anotarlas, el reintento usa las mismas claves y no duplica")
    void criterio3_unaCaidaSinAnotarLasEntregasNoLasDuplica() {
        Ejecucion ejecucion = misionConTodosLosPasos();
        String referencia = "mision-" + ejecucion.id();
        pasarA(Duration.ofHours(1));
        // Se simula y se guarda, y en esa misma vuelta se hacen TODAS las entregas; pero el guardado que las anota
        // (el unico con la liquidacion terminada) no llega a Mongo: el proceso «murio» justo antes.
        ejecuciones.fallarUnaVezSi = e -> e.estado() != EstadoEjecucion.EN_PROGRESO && !e.liquidacionPendiente();

        programador.darUnaVuelta();

        assertThat(leer(ejecucion).liquidacionPendiente()).as("nada quedo anotado como hecho").isTrue();
        assertThat(correo.enviados).containsOnlyKeys(referencia + "-correo", referencia + "-correo-epica");

        programador.darUnaVuelta();

        Ejecucion lista = leer(ejecucion);
        assertThat(lista.liquidacionPendiente()).isFalse();
        assertThat(lista.pasos().values()).containsOnly(EstadoDePaso.HECHO);
        // El segundo pase repitio los mismos pedidos con las mismas claves, y quien los recibe no duplica nada.
        assertThat(libro.intentos).containsExactly(referencia, referencia);
        assertThat(libro.acreditado).containsOnlyKeys(referencia);
        assertThat(correo.intentos).containsExactlyInAnyOrder(referencia + "-correo", referencia + "-correo",
                referencia + "-correo-epica", referencia + "-correo-epica");
        assertThat(correo.enviados).hasSize(2);
        // Y la bandeja: los mismos ids otra vez; el id repetido es el 409 que el cliente da por entregado.
        assertThat(avisos.enBandeja.keySet()).containsExactlyInAnyOrder(referencia + "-aviso",
                referencia + "-aviso-epica", referencia + "-aviso-desbloqueo-tras-la-completa");
        avisos.enBandeja.keySet().forEach(id -> assertThat(avisos.intentos.stream().filter(id::equals).count())
                .as("intentos de %s", id).isEqualTo(2));
        assertThat(inventario.clavesDeEntrega).containsExactlyInAnyOrder(referencia + "-botin", referencia + "-epica");
        assertThat(inventario.experienciaSumada.get("h-1")).as("la experiencia se suma una sola vez")
                .isEqualTo(lista.recompensas().experiencia());
    }

    // =============================================================== criterio 4

    @Test
    @DisplayName("criterio 4: la mision que termina en segundo plano genera su aviso al jugador, sin que nadie consulte")
    void criterio4_alTerminarEnSegundoPlanoSeNotificaAlJugador() {
        Ejecucion ejecucion = enviar("prueba-corta", "h-1");
        pasarA(Duration.ofHours(1));

        programador.darUnaVuelta();

        String clave = "mision-" + ejecucion.id() + "-correo";
        assertThat(correo.enviados).containsOnlyKeys(clave);
        assertThat(correo.enviados.get(clave)).contains("terminó con éxito");
        assertThat(leer(ejecucion).estadoDe(PasoDeLiquidacion.CORREO)).isEqualTo(EstadoDePaso.HECHO);
        // Y el aviso en la bandeja (RF-NOT-004, #851), con id estable, en la misma vuelta.
        String idDelAviso = "mision-" + ejecucion.id() + "-aviso";
        assertThat(avisos.enBandeja).containsOnlyKeys(idDelAviso);
        assertThat(avisos.enBandeja.get(idDelAviso)).startsWith("Tu misión «Misión prueba-corta» terminó con éxito");
        assertThat(avisos.destinatarios).containsEntry(idDelAviso, JUGADOR);
        assertThat(leer(ejecucion).estadoDe(PasoDeLiquidacion.AVISO)).isEqualTo(EstadoDePaso.HECHO);
    }

    @Test
    @DisplayName("criterio 4: si el heroe cae, el aviso tambien sale y lo dice")
    void criterio4_laFallidaTambienNotifica() {
        Ejecucion ejecucion = enviar("prueba-corta", "h-1");
        motor.danoDeLosEnemigos = 1000;
        motor.danoDelHeroe = 0;
        pasarA(Duration.ofHours(1));

        programador.darUnaVuelta();

        assertThat(leer(ejecucion).estado()).isEqualTo(EstadoEjecucion.FALLIDA);
        assertThat(correo.enviados.get("mision-" + ejecucion.id() + "-correo")).contains("derrotado");
        assertThat(avisos.enBandeja.get("mision-" + ejecucion.id() + "-aviso")).contains("derrotado");
    }

    @Test
    @DisplayName("criterio 4: si el correo no sale a la primera, el aviso no se pierde y sale una sola vez con la misma clave")
    void criterio4_elAvisoNoSePierdeNiSeDuplicaSiHayReintento() {
        Ejecucion ejecucion = enviar("prueba-corta", "h-1");
        correo.fallar.set(Dobles.caido("correo"));
        pasarA(Duration.ofHours(1));

        programador.darUnaVuelta();
        assertThat(correo.enviados).isEmpty();
        assertThat(leer(ejecucion).estadoDe(PasoDeLiquidacion.CORREO)).isEqualTo(EstadoDePaso.PENDIENTE);

        esperar(ESPERA_BASE.plusSeconds(1));
        programador.darUnaVuelta();
        assertThat(correo.enviados).isEmpty();

        correo.fallar.set(null);
        esperar(ESPERA_BASE.multipliedBy(2).plusSeconds(1));
        programador.darUnaVuelta();
        programador.darUnaVuelta();

        String clave = "mision-" + ejecucion.id() + "-correo";
        assertThat(correo.enviados).containsOnlyKeys(clave);
        assertThat(correo.intentos).as("todos los intentos usaron la misma clave").containsOnly(clave);
        assertThat(correo.intentos).hasSize(3);
        assertThat(leer(ejecucion).estadoDe(PasoDeLiquidacion.CORREO)).isEqualTo(EstadoDePaso.HECHO);
    }

    @Test
    @DisplayName("criterio 4: si la bandeja no contesta, el aviso tampoco se pierde ni se duplica: se reintenta con el mismo id hasta entregarse")
    void criterio4_elAvisoDeLaBandejaNoSePierdeNiSeDuplicaSiHayReintento() {
        Ejecucion ejecucion = enviar("prueba-corta", "h-1");
        avisos.fallar.set(Dobles.caido("notificaciones"));
        pasarA(Duration.ofHours(1));

        programador.darUnaVuelta();
        assertThat(avisos.enBandeja).isEmpty();
        assertThat(leer(ejecucion).estadoDe(PasoDeLiquidacion.AVISO)).isEqualTo(EstadoDePaso.PENDIENTE);
        assertThat(correo.enviados).as("el correo no espera a la bandeja").hasSize(1);

        esperar(ESPERA_BASE.plusSeconds(1));
        programador.darUnaVuelta();
        assertThat(avisos.enBandeja).isEmpty();

        avisos.fallar.set(null);
        esperar(ESPERA_BASE.multipliedBy(2).plusSeconds(1));
        programador.darUnaVuelta();
        programador.darUnaVuelta();

        String id = "mision-" + ejecucion.id() + "-aviso";
        assertThat(avisos.enBandeja).containsOnlyKeys(id);
        assertThat(avisos.intentos).as("todos los intentos usaron el mismo id").containsOnly(id).hasSize(3);
        assertThat(correo.enviados).hasSize(1);
        assertThat(leer(ejecucion).liquidacionPendiente()).isFalse();
    }

    @Test
    @DisplayName("criterio 4: el aviso se decide al terminar, junto con el resultado: no hay un momento en que la mision este terminada y sin aviso por enviar")
    void criterio4_elAvisoNaceConElFinDeLaMision() {
        Ejecucion ejecucion = enviar("prueba-corta", "h-1");
        pasarA(Duration.ofHours(1));
        // Los servicios de entrega estan caidos: la mision termina, y el aviso queda ya anotado como pendiente.
        correo.fallar.set(Dobles.caido("correo"));
        libro.fallar = Dobles.caido("ms-finanzas");
        inventario.fallarAlLiberar = Dobles.caido("inventario");

        programador.darUnaVuelta();

        Ejecucion terminada = leer(ejecucion);
        assertThat(terminada.estado()).isEqualTo(EstadoEjecucion.COMPLETADA);
        assertThat(terminada.estadoDe(PasoDeLiquidacion.CORREO)).isEqualTo(EstadoDePaso.PENDIENTE);
        assertThat(terminada.estadoDe(PasoDeLiquidacion.AVISO)).isEqualTo(EstadoDePaso.PENDIENTE);
    }

    // ============================================================ trazabilidad

    @Test
    @DisplayName("trazabilidad: la bitacora registra el fallo con su intento y la recuperacion de la simulacion")
    void bitacora_registraFalloYRecuperacionDeLaSimulacion() {
        Ejecucion ejecucion = enviar("prueba-corta", "h-1");
        heroes.fallarAlDecidir = Dobles.caido("heroes");
        pasarA(Duration.ofHours(1));

        programador.darUnaVuelta();

        assertThat(mensajesDeLaBitacora()).anySatisfy(m -> assertThat(m)
                .contains(ejecucion.id().toString()).containsIgnoringCase("reintent"));

        heroes.fallarAlDecidir = null;
        esperar(ESPERA_BASE);
        programador.darUnaVuelta();

        assertThat(mensajesDeLaBitacora()).anySatisfy(m -> assertThat(m)
                .contains(ejecucion.id().toString()).containsIgnoringCase("recuper"));
    }

    @Test
    @DisplayName("trazabilidad: la bitacora registra la recuperacion de una liquidacion reintentada")
    void bitacora_registraLaRecuperacionDeLaLiquidacion() {
        Ejecucion ejecucion = enviar("prueba-corta", "h-1");
        libro.fallar = Dobles.caido("ms-finanzas");
        pasarA(Duration.ofHours(1));
        programador.darUnaVuelta();

        libro.fallar = null;
        esperar(ESPERA_BASE.plusSeconds(1));
        programador.darUnaVuelta();

        assertThat(mensajesDeLaBitacora()).anySatisfy(m -> assertThat(m)
                .contains(ejecucion.id().toString()).containsIgnoringCase("recuper"));
    }

    @Test
    @DisplayName("el barrido de vencidas que falla no impide liquidar lo pendiente en esa misma vuelta")
    void unBarridoQueFallaNoImpideLiquidar() {
        Ejecucion ejecucion = enviar("prueba-corta", "h-1");
        libro.fallar = Dobles.caido("ms-finanzas");
        pasarA(Duration.ofHours(1));
        programador.darUnaVuelta();
        assertThat(leer(ejecucion).liquidacionPendiente()).isTrue();

        libro.fallar = null;
        ejecuciones.fallarAlConsultarVencidas = new IllegalStateException("la consulta de vencidas no contesta");
        esperar(ESPERA_BASE.plusSeconds(1));
        programador.darUnaVuelta();

        assertThat(leer(ejecucion).liquidacionPendiente()).isFalse();
        assertThat(libro.acreditado).containsKey("mision-" + ejecucion.id());
    }
}
