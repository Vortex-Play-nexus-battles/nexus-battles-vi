package nexus.misiones.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import nexus.misiones.dominio.Ejecucion;
import nexus.misiones.dominio.EjecucionNoEncontrada;
import nexus.misiones.dominio.Epica;
import nexus.misiones.dominio.EpicaDeTabla20;
import nexus.misiones.dominio.EstadoDePaso;
import nexus.misiones.dominio.EstadoEjecucion;
import nexus.misiones.dominio.Misiones;
import nexus.misiones.dominio.ParametrosDeRecompensa;
import nexus.misiones.dominio.PasoDeLiquidacion;
import nexus.misiones.dominio.RecompensasDeEjecucion;
import nexus.misiones.dominio.simulacion.ResultadoDeMision;
import nexus.misiones.dominio.TransicionNoPermitida;
import nexus.misiones.dominio.simulacion.Combatiente;
import nexus.misiones.dominio.simulacion.EstadisticasDeCombate;
import nexus.misiones.dominio.simulacion.EventoDeCombate;
import nexus.misiones.dominio.simulacion.Formula;
import nexus.misiones.dominio.simulacion.TurnoParaDecidir;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Una mision de principio a fin, en memoria: matricula, el plazo vence, el
 * trabajo en segundo plano simula y liquida (heroe liberado con su
 * experiencia, creditos, epica, correo), y cada paso se reintenta sin repetir
 * nada si un servicio no responde (7.8.12, «sistema de rollback en caso de
 * errores»).
 */
class CicloDeUnaMisionTest {

    private static final Instant INICIO = Instant.parse("2026-09-25T10:00:00Z");
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
    private ParametrosDeMisiones parametros;
    private LiquidarEjecucion liquidar;
    private MatricularHeroe matricular;
    private TrabajoDeMisiones trabajo;
    private CancelarEjecucion cancelar;

    private static final EpicaDeTabla20 ARMAS_SEGURA = new EpicaDeTabla20("Guerrero Armas",
            new Epica("Segundo impulso", "Recupera 1d4 de vida", "+3 a la vida", "4481eb34-384a-3fa0-ba9a-1aac9562c38f"),
            100);

    @BeforeEach
    void preparar() {
        prepararCon(List.of());
    }

    private void prepararCon(List<EpicaDeTabla20> tabla20) {
        prepararCon(tabla20, 20);
    }

    private void prepararCon(List<EpicaDeTabla20> tabla20, int lote) {
        ejecuciones = new Dobles.Ejecuciones();
        inventario = new Dobles.Inventario().conHeroe("h-1", JUGADOR, "p-armas", true)
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
        parametros = new ParametrosDeMisiones(Duration.ofHours(1), Duration.ofSeconds(30), lote,
                true, true, null, null, new ParametrosDeRecompensa(Map.of(), Map.of(), false));
        // «tras-la-corta» y «otra-tras-la-corta» piden «prueba-corta» (la historia se desbloquea en orden, 7.8.2).
        catalogo = new Dobles.Catalogo(List.of(Misiones.templo(),
                Misiones.historia("prueba-corta", List.of()),
                Misiones.historiaTrasDe("tras-la-corta", "prueba-corta"),
                Misiones.historiaTrasDe("otra-tras-la-corta", "prueba-corta")), tabla20);
        matricular = new MatricularHeroe(catalogo, ejecuciones, new Dobles.Estrategias(), inventario, productos,
                heroes, parametros, reloj, () -> 7L);
        liquidar = new LiquidarEjecucion(ejecuciones, catalogo, inventario, libro, directorio,
                correo, avisos, parametros, reloj);
        trabajo = new TrabajoDeMisiones(ejecuciones, simulador(Set.of()), liquidar, parametros, reloj);
        cancelar = new CancelarEjecucion(ejecuciones, liquidar, reloj);
    }

    /** La simulacion de verdad, salvo para las ejecuciones «envenenadas», que heroes rechaza siempre. */
    private SimularEjecucion simulador(Set<UUID> envenenadas) {
        return new SimularEjecucion(catalogo, ejecuciones, eventos, heroes, motor,
                new PerfilDeCombateDelHeroe(inventario, productos, heroes),
                new RotacionesPorDefectoDeEnemigos(heroes), parametros, reloj) {
            @Override
            public Optional<Ejecucion> simular(Ejecucion ejecucion) {
                if (envenenadas.contains(ejecucion.id())) {
                    throw new RechazoDelServicio("heroes", 404, "No existe ese prototipo");
                }
                return super.simular(ejecucion);
            }
        };
    }

    private Ejecucion enviar(String mision) {
        return enviar(mision, "h-1");
    }

    private Ejecucion enviar(String mision, String heroe) {
        return matricular.matricular(JUGADOR,
                new SolicitudDeMatricula(mision, heroe, List.of(), null, null)).ejecucion();
    }

    @Test
    @DisplayName("antes de vencer el plazo no se simula nada y el heroe sigue en mision")
    void antesDeTiempo() {
        Ejecucion ejecucion = enviar("prueba-corta");
        ahora.set(INICIO.plus(Duration.ofMinutes(59)));

        trabajo.ejecutar();

        assertThat(ejecuciones.buscar(ejecucion.id()).orElseThrow().estado()).isEqualTo(EstadoEjecucion.EN_PROGRESO);
        assertThat(inventario.bloqueados).containsKey("h-1");
    }

    @Test
    @DisplayName("al vencer: completada, heroe liberado con su experiencia, creditos, correo y todo hecho")
    void cicloCompleto() {
        Ejecucion ejecucion = enviar("prueba-corta");
        ahora.set(INICIO.plus(Duration.ofHours(1)));

        trabajo.ejecutar();

        Ejecucion terminada = ejecuciones.buscar(ejecucion.id()).orElseThrow();
        assertThat(terminada.estado()).isEqualTo(EstadoEjecucion.COMPLETADA);
        assertThat(terminada.liquidacionPendiente()).isFalse();
        assertThat(terminada.recompensas().creditos()).isEqualTo(7);
        assertThat(inventario.bloqueados).isEmpty();
        assertThat(inventario.experienciaSumada.get("h-1")).isEqualTo(terminada.recompensas().experiencia())
                .isPositive();
        assertThat(libro.acreditado).containsEntry("mision-" + ejecucion.id(), 7);
        assertThat(correo.enviados).containsKey("mision-" + ejecucion.id() + "-correo");
        assertThat(correo.enviados.get("mision-" + ejecucion.id() + "-correo")).contains("con éxito");
        assertThat(terminada.nivelAlcanzado()).isNotNull();
    }

    @Test
    @DisplayName("D-42: los enemigos pelean en el nivel recomendado de la misión (7.8.13), no en el del héroe")
    void enemigosEnElNivelDeLaMision() {
        // El heroe es de nivel 1 y el Templo recomienda el 8.
        enviar("templo-olvidado");
        ahora.set(INICIO.plus(Duration.ofHours(12)));

        trabajo.ejecutar();

        // Guardianes y Espectros (y el jefe) se piden en el nivel 8; ninguno en el 1.
        assertThat(heroes.nivelesPedidos).contains("Guerrero Tanque@8", "Mago Fuego@8")
                .doesNotContain("Guerrero Tanque@1", "Mago Fuego@1");
    }

    @Test
    @DisplayName("D-42: una misión sin nivel recomendado (la provisional de DEV) pelea en el nivel del héroe")
    void sinNivelRecomendadoElDelHeroe() {
        enviar("prueba-corta");
        ahora.set(INICIO.plus(Duration.ofHours(1)));

        trabajo.ejecutar();

        // El jefe de prueba (Guerrero Tanque) en el nivel del heroe, 1.
        assertThat(heroes.nivelesPedidos).contains("Guerrero Tanque@1");
    }

    @Test
    @DisplayName("si el heroe cae la mision queda Fallida: sin creditos, con la experiencia de lo que derroto")
    void fallida() {
        motor.danoDeLosEnemigos = 1000;
        motor.danoDelHeroe = 0;
        Ejecucion ejecucion = enviar("prueba-corta");
        ahora.set(INICIO.plus(Duration.ofHours(1)));

        trabajo.ejecutar();

        Ejecucion terminada = ejecuciones.buscar(ejecucion.id()).orElseThrow();
        assertThat(terminada.estado()).isEqualTo(EstadoEjecucion.FALLIDA);
        assertThat(terminada.recompensas().creditos()).isZero();
        assertThat(libro.acreditado).isEmpty();
        assertThat(inventario.bloqueados).isEmpty();
        assertThat(correo.enviados.values()).anySatisfy(texto -> assertThat(texto).contains("derrotado"));
    }

    @Test
    @DisplayName("el Master de la Tabla 20 derrotado entrega su epica con la clave mision-{id}-epica")
    void epica() {
        prepararCon(List.of(ARMAS_SEGURA));
        heroes.vidaDeLosEnemigos = 5;
        Ejecucion ejecucion = enviar("prueba-corta");
        ahora.set(INICIO.plus(Duration.ofHours(1)));

        trabajo.ejecutar();

        Ejecucion terminada = ejecuciones.buscar(ejecucion.id()).orElseThrow();
        assertThat(terminada.recompensas().epicas()).extracting(e -> e.nombre()).containsExactly("Segundo impulso");
        assertThat(inventario.clavesDeEntrega).containsExactly("mision-" + ejecucion.id() + "-epica");
        assertThat(inventario.entregas.getFirst())
                .containsExactly(new InventarioDeHeroes.ProductoAEntregar("4481eb34-384a-3fa0-ba9a-1aac9562c38f", 1));
        assertThat(correo.enviados).containsKey("mision-" + ejecucion.id() + "-correo-epica");
    }

    @Test
    @DisplayName("si ms-finanzas no responde, el credito se reintenta despues sin repetir lo ya hecho")
    void reintentos() {
        libro.fallar = Dobles.caido("ms-finanzas");
        Ejecucion ejecucion = enviar("prueba-corta");
        ahora.set(INICIO.plus(Duration.ofHours(1)));

        trabajo.ejecutar();

        Ejecucion pendiente = ejecuciones.buscar(ejecucion.id()).orElseThrow();
        assertThat(pendiente.estadoDe(PasoDeLiquidacion.LIBERACION)).isEqualTo(EstadoDePaso.HECHO);
        assertThat(pendiente.estadoDe(PasoDeLiquidacion.CREDITOS)).isEqualTo(EstadoDePaso.PENDIENTE);
        assertThat(pendiente.proximoIntento()).isEqualTo(INICIO.plus(Duration.ofHours(1)).plusSeconds(30));

        libro.fallar = null;
        trabajo.ejecutar();
        assertThat(ejecuciones.buscar(ejecucion.id()).orElseThrow().liquidacionPendiente()).isTrue();

        ahora.set(INICIO.plus(Duration.ofHours(1)).plusSeconds(31));
        trabajo.ejecutar();

        Ejecucion liquidada = ejecuciones.buscar(ejecucion.id()).orElseThrow();
        assertThat(liquidada.liquidacionPendiente()).isFalse();
        assertThat(libro.acreditado).hasSize(1);
        assertThat(inventario.llamadas.stream().filter(l -> l.startsWith("liberar"))).hasSize(1);
    }

    @Test
    @DisplayName("un rechazo definitivo no se reintenta: queda anotado con su motivo")
    void rechazoDefinitivo() {
        correo.fallar.set(new RechazoDelServicio("correo", 400, "correo invalido"));
        Ejecucion ejecucion = enviar("prueba-corta");
        ahora.set(INICIO.plus(Duration.ofHours(1)));

        trabajo.ejecutar();

        Ejecucion terminada = ejecuciones.buscar(ejecucion.id()).orElseThrow();
        assertThat(terminada.estadoDe(PasoDeLiquidacion.CORREO)).isEqualTo(EstadoDePaso.FALLIDO);
        assertThat(terminada.motivoDe(PasoDeLiquidacion.CORREO)).contains("400");
        assertThat(terminada.liquidacionPendiente()).isFalse();
    }

    @Test
    @DisplayName("sin contacto en identidad no hay a quien escribir: el correo queda fallido, lo demas hecho")
    void sinContacto() {
        directorio.contactos.clear();
        Ejecucion ejecucion = enviar("prueba-corta");
        ahora.set(INICIO.plus(Duration.ofHours(1)));

        trabajo.ejecutar();

        Ejecucion terminada = ejecuciones.buscar(ejecucion.id()).orElseThrow();
        assertThat(terminada.estadoDe(PasoDeLiquidacion.CORREO)).isEqualTo(EstadoDePaso.FALLIDO);
        assertThat(terminada.estadoDe(PasoDeLiquidacion.CREDITOS)).isEqualTo(EstadoDePaso.HECHO);
    }

    @Test
    @DisplayName("si heroes o el motor no responden, la simulacion se aplaza y se repite al cumplirse la espera")
    void simulacionReintentable() {
        heroes.fallarAlDecidir = Dobles.caido("heroes");
        Ejecucion ejecucion = enviar("prueba-corta");
        Instant vence = INICIO.plus(Duration.ofHours(1));
        ahora.set(vence);

        trabajo.ejecutar();
        Ejecucion aplazada = ejecuciones.buscar(ejecucion.id()).orElseThrow();
        assertThat(aplazada.estado()).isEqualTo(EstadoEjecucion.EN_PROGRESO);
        assertThat(aplazada.proximoIntento()).isEqualTo(vence.plusSeconds(30));
        assertThat(aplazada.ultimoError()).isNotBlank();

        heroes.fallarAlDecidir = null;
        trabajo.ejecutar();
        assertThat(ejecuciones.buscar(ejecucion.id()).orElseThrow().estado())
                .as("antes de la espera no se vuelve a intentar").isEqualTo(EstadoEjecucion.EN_PROGRESO);

        ahora.set(vence.plusSeconds(30));
        trabajo.ejecutar();
        Ejecucion terminada = ejecuciones.buscar(ejecucion.id()).orElseThrow();
        assertThat(terminada.estado()).isEqualTo(EstadoEjecucion.COMPLETADA);
        assertThat(terminada.liquidacionPendiente()).isFalse();
        assertThat(terminada.ultimoError()).as("el error de la simulacion ya no aplica").isNull();
    }

    @Test
    @DisplayName("cancelar: abandonada, heroe libre al momento, sin experiencia ni recompensas")
    void cancelacion() {
        Ejecucion ejecucion = enviar("templo-olvidado");
        ahora.set(INICIO.plus(Duration.ofHours(2)));

        Cancelacion cancelacion = cancelar.cancelar(JUGADOR, ejecucion.id());

        assertThat(cancelacion.ejecucion().estado()).isEqualTo(EstadoEjecucion.ABANDONADA);
        assertThat(cancelacion.heroeLiberado()).isTrue();
        assertThat(cancelacion.penalizacion()).contains("recompensas");
        assertThat(inventario.bloqueados).isEmpty();
        assertThat(inventario.llamadas).contains("liberar h-1 0.0");
        assertThat(libro.acreditado).isEmpty();
    }

    @Test
    @DisplayName("cancelar con el inventario caido: abandonada igual, y la liberacion la termina el trabajo")
    void cancelacionConInventarioCaido() {
        Ejecucion ejecucion = enviar("templo-olvidado");
        inventario.fallarAlLiberar = Dobles.caido("inventario");

        Cancelacion cancelacion = cancelar.cancelar(JUGADOR, ejecucion.id());

        assertThat(cancelacion.heroeLiberado()).isFalse();
        inventario.fallarAlLiberar = null;
        ahora.set(INICIO.plusSeconds(31));
        trabajo.ejecutar();
        assertThat(inventario.bloqueados).isEmpty();
    }

    @Test
    @DisplayName("no se cancela lo ajeno ni lo terminado")
    void cancelacionesImposibles() {
        Ejecucion ejecucion = enviar("templo-olvidado");
        cancelar.cancelar(JUGADOR, ejecucion.id());

        assertThatThrownBy(() -> cancelar.cancelar("33333333-3333-4333-8333-333333333333", ejecucion.id()))
                .isInstanceOf(EjecucionNoEncontrada.class);
        assertThatThrownBy(() -> cancelar.cancelar(JUGADOR, ejecucion.id()))
                .isInstanceOf(TransicionNoPermitida.class);
    }

    @Test
    @DisplayName("la primera vez da su bonificacion y la segunda ya no")
    void primeraVezUnaSolaVez() {
        Ejecucion primera = enviar("prueba-corta");
        ahora.set(INICIO.plus(Duration.ofHours(1)));
        trabajo.ejecutar();

        ahora.set(INICIO.plus(Duration.ofHours(2)));
        Ejecucion segunda = enviar("prueba-corta");
        ahora.set(INICIO.plus(Duration.ofHours(3)));
        trabajo.ejecutar();

        assertThat(ejecuciones.buscar(primera.id()).orElseThrow().recompensas().creditos()).isEqualTo(7);
        assertThat(ejecuciones.buscar(segunda.id()).orElseThrow().recompensas().creditos()).isEqualTo(5);
    }

    // ------------------------------------------------------------- HU-SIM-003

    @Test
    @DisplayName("una mision de 19 encuentros se simula entera en una sola vuelta, sin esperar tiempo real")
    void simulacionEnLote() {
        Ejecucion ejecucion = enviar("templo-olvidado");
        ahora.set(INICIO.plus(Duration.ofHours(12)));

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> trabajo.ejecutar());

        Ejecucion terminada = ejecuciones.buscar(ejecucion.id()).orElseThrow();
        assertThat(terminada.estado()).isEqualTo(EstadoEjecucion.COMPLETADA);
        assertThat(terminada.resultado().encuentrosCompletados()).isGreaterThanOrEqualTo(19);
        // El reloj no se movio mientras se simulaba: el tiempo de la mision solo decide CUANDO se puede ver.
        assertThat(ahora.get()).isEqualTo(INICIO.plus(Duration.ofHours(12)));
        // Y todo se resolvio con el motor de las batallas en linea: turnos y acciones, nunca golpes sueltos.
        assertThat(motor.llamadas).isNotEmpty().allMatch(l -> l.startsWith("turnos ") || l.startsWith("acciones "));
    }

    @Test
    @DisplayName("cada turno resuelto queda registrado en orden, con la ejecucion y la mision")
    void eventosDeLaEjecucion() {
        Ejecucion ejecucion = enviar("templo-olvidado");
        ahora.set(INICIO.plus(Duration.ofHours(12)));

        trabajo.ejecutar();

        List<EventoDeCombate> registrados = eventos.de(ejecucion.id());
        assertThat(registrados).isNotEmpty();
        assertThat(registrados).extracting(EventoDeCombate::secuencia)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, registrados.size()).boxed().toList());
        assertThat(registrados).allSatisfy(e -> {
            assertThat(e.ejecucionId()).isEqualTo(ejecucion.id());
            assertThat(e.misionId()).isEqualTo("templo-olvidado");
        });
        assertThat(registrados).extracting(EventoDeCombate::encuentro).contains(1, 19);
        // Los eventos son uno por turno de un combatiente: el motor recibio una accion por cada uno con jugada.
        long accionesPedidas = motor.llamadas.stream().filter(l -> l.startsWith("acciones ")).count();
        assertThat(registrados.stream().filter(e -> e.jugada() != null).count()).isEqualTo(accionesPedidas);
    }

    @Test
    @DisplayName("el heroe entra al motor con lo que lleva: estadisticas con equipo, equipo puesto y epicas")
    void elHeroeRealEntraAlMotor() {
        EstadisticasDeCombate esperadas = new EstadisticasDeCombate(12, 60, 14, new Formula(11, 1, 6),
                new Formula(3, 1, 4), null);
        inventario.estadisticasDelHeroe = new InventarioDeHeroes.EstadisticasDelHeroe(12, 60, 14,
                new Formula(11, 1, 6), new Formula(3, 1, 4), null);
        inventario.equipoDelHeroe = new InventarioDeHeroes.EquipoDelHeroe(List.of("p-espada", "p-armadura"),
                List.of("p-epica"));
        productos.nombres.put("p-espada", "Espada de una mano");
        productos.nombres.put("p-armadura", "Peto de cuero");
        productos.nombres.put("p-epica", "Golpe de defensa");
        Ejecucion ejecucion = enviar("prueba-corta");
        ahora.set(INICIO.plus(Duration.ofHours(1)));

        trabajo.ejecutar();

        Combatiente heroe = motor.recibidos.getFirst().stream().filter(c -> c.id().equals("heroe")).findFirst()
                .orElseThrow();
        assertThat(heroe.prototipo()).isEqualTo("Guerrero Armas");
        assertThat(heroe.nivel()).isEqualTo(1);
        assertThat(heroe.estadisticas()).isEqualTo(esperadas);
        assertThat(heroe.equipamiento()).containsExactly("Espada de una mano", "Peto de cuero");
        assertThat(heroe.epicas()).containsExactly("Golpe de defensa");
        assertThat(ejecuciones.buscar(ejecucion.id()).orElseThrow().estado()).isEqualTo(EstadoEjecucion.COMPLETADA);
    }

    @Test
    @DisplayName("si el motor no responde no se guarda nada; al reintentar los eventos se escriben una sola vez")
    void motorCaidoNoDejaEventos() {
        motor.fallar = Dobles.caido("motor-combate");
        Ejecucion ejecucion = enviar("prueba-corta");
        ahora.set(INICIO.plus(Duration.ofHours(1)));

        trabajo.ejecutar();

        assertThat(ejecuciones.buscar(ejecucion.id()).orElseThrow().estado()).isEqualTo(EstadoEjecucion.EN_PROGRESO);
        assertThat(eventos.escrituras).isZero();

        motor.fallar = null;
        ahora.set(INICIO.plus(Duration.ofHours(1)).plusSeconds(30));
        trabajo.ejecutar();

        assertThat(ejecuciones.buscar(ejecucion.id()).orElseThrow().estado()).isEqualTo(EstadoEjecucion.COMPLETADA);
        assertThat(eventos.escrituras).isEqualTo(1);
        assertThat(eventos.de(ejecucion.id())).isNotEmpty();
    }

    @Test
    @DisplayName("si no se pueden guardar los turnos tampoco se da por terminada la ejecucion: se reintenta")
    void sinEventosNoHayEjecucionTerminada() {
        eventos.fallarAlGuardar = Dobles.caido("mongo");
        Ejecucion ejecucion = enviar("prueba-corta");
        ahora.set(INICIO.plus(Duration.ofHours(1)));

        trabajo.ejecutar();

        assertThat(ejecuciones.buscar(ejecucion.id()).orElseThrow().estado()).isEqualTo(EstadoEjecucion.EN_PROGRESO);

        eventos.fallarAlGuardar = null;
        ahora.set(INICIO.plus(Duration.ofHours(1)).plusSeconds(30));
        trabajo.ejecutar();
        assertThat(ejecuciones.buscar(ejecucion.id()).orElseThrow().estado()).isEqualTo(EstadoEjecucion.COMPLETADA);
    }

    @Test
    @DisplayName("si el inventario ya no conoce al heroe, pelea con las estadisticas del catalogo en vez de quedarse sin simular")
    void heroeSinInventario() {
        Ejecucion ejecucion = enviar("prueba-corta");
        ahora.set(INICIO.plus(Duration.ofHours(1)));
        inventario.fallarAlPedirEstadisticas = new HeroeNoEncontrado();
        inventario.fallarAlPedirEquipo = new HeroeNoEncontrado();

        trabajo.ejecutar();

        Combatiente heroe = motor.recibidos.getFirst().stream().filter(c -> c.id().equals("heroe")).findFirst()
                .orElseThrow();
        assertThat(heroe.estadisticas()).isNull();
        assertThat(heroe.equipamiento()).isEmpty();
        assertThat(ejecuciones.buscar(ejecucion.id()).orElseThrow().estado()).isEqualTo(EstadoEjecucion.COMPLETADA);
    }

    @Test
    @DisplayName("los enemigos sin estrategia propia pelean con la rotacion por defecto de su prototipo")
    void enemigosConRotacionPorDefecto() {
        heroes.habilidadesValidas = List.of("Golpe con escudo", "Ataque básico");
        motor.danoDelHeroe = 1;
        Ejecucion ejecucion = enviar("prueba-corta");
        ahora.set(INICIO.plus(Duration.ofHours(1)));

        trabajo.ejecutar();

        List<TurnoParaDecidir> deEnemigos = heroes.decisiones.stream()
                .filter(t -> t.prototipo().equals("Guerrero Tanque")).toList();
        assertThat(deEnemigos).isNotEmpty();
        assertThat(deEnemigos).allSatisfy(t -> assertThat(t.rotaciones()).containsExactly(List.of("Golpe con escudo")));
        assertThat(ejecuciones.buscar(ejecucion.id()).orElseThrow().estado()).isNotEqualTo(EstadoEjecucion.EN_PROGRESO);
    }

    // ------------------------------------------------- avisos (RF-NOT-004)

    @Test
    @DisplayName("RF-NOT-004: al terminar, la bandeja del jugador recibe la finalizacion con el detalle de cada recompensa")
    void avisoDeFinalizacion() {
        Ejecucion ejecucion = enviar("prueba-corta");
        ahora.set(INICIO.plus(Duration.ofHours(1)));

        trabajo.ejecutar();

        Ejecucion terminada = ejecuciones.buscar(ejecucion.id()).orElseThrow();
        String id = "mision-" + ejecucion.id() + "-aviso";
        assertThat(avisos.enBandeja).containsKey(id);
        assertThat(avisos.enBandeja.get(id))
                .startsWith("Tu misión «Misión prueba-corta» terminó con éxito | ")
                .contains("Ganó 7 créditos", "puntos de experiencia", "El reporte completo está en Misiones");
        assertThat(avisos.destinatarios).containsEntry(id, JUGADOR);
        assertThat(avisos.creadas).as("la hora del hecho, igual en cada reintento").containsEntry(id,
                terminada.terminadaEn());
        assertThat(terminada.estadoDe(PasoDeLiquidacion.AVISO)).isEqualTo(EstadoDePaso.HECHO);
        assertThat(terminada.liquidacionPendiente()).isFalse();
    }

    @Test
    @DisplayName("RF-NOT-004: la primera vez que se completa avisa de las misiones que desbloquea; la segunda ya no")
    void avisoDeMisionesDesbloqueadas() {
        Ejecucion primera = enviar("prueba-corta");
        ahora.set(INICIO.plus(Duration.ofHours(1)));
        trabajo.ejecutar();

        assertThat(avisos.enBandeja).containsKeys(
                "mision-" + primera.id() + "-aviso-desbloqueo-tras-la-corta",
                "mision-" + primera.id() + "-aviso-desbloqueo-otra-tras-la-corta");
        assertThat(avisos.enBandeja.get("mision-" + primera.id() + "-aviso-desbloqueo-tras-la-corta"))
                .isEqualTo("Nueva misión disponible: «Misión tras-la-corta» | Completaste «Misión prueba-corta»: "
                        + "ya puedes enviar un héroe a «Misión tras-la-corta».");
        // Ni el Templo ni la propia misión se dan por desbloqueados: no la piden.
        assertThat(avisos.enBandeja.keySet()).noneMatch(k -> k.endsWith("-templo-olvidado")
                || k.endsWith("-aviso-desbloqueo-prueba-corta"));

        ahora.set(INICIO.plus(Duration.ofHours(2)));
        Ejecucion segunda = enviar("prueba-corta");
        ahora.set(INICIO.plus(Duration.ofHours(3)));
        trabajo.ejecutar();

        assertThat(avisos.enBandeja).containsKey("mision-" + segunda.id() + "-aviso");
        assertThat(avisos.enBandeja.keySet()).noneMatch(k -> k.startsWith("mision-" + segunda.id() + "-aviso-desbloqueo"));
        assertThat(ejecuciones.buscar(segunda.id()).orElseThrow().estadoDe(PasoDeLiquidacion.AVISO_DESBLOQUEO))
                .as("ni siquiera queda el paso: solo la primera vez desbloquea").isNull();
    }

    @Test
    @DisplayName("RF-NOT-004: una mision fallida avisa de su final y no desbloquea nada")
    void avisoDeMisionFallida() {
        motor.danoDeLosEnemigos = 1000;
        motor.danoDelHeroe = 0;
        Ejecucion ejecucion = enviar("prueba-corta");
        ahora.set(INICIO.plus(Duration.ofHours(1)));

        trabajo.ejecutar();

        assertThat(avisos.enBandeja.get("mision-" + ejecucion.id() + "-aviso"))
                .startsWith("Tu misión «Misión prueba-corta» terminó: Vorn fue derrotado | ");
        assertThat(avisos.enBandeja.keySet()).noneMatch(k -> k.contains("-aviso-desbloqueo-"));
    }

    @Test
    @DisplayName("RF-NOT-004: la epica de Master obtenida tiene su propio aviso")
    void avisoDeEpica() {
        prepararCon(List.of(ARMAS_SEGURA));
        heroes.vidaDeLosEnemigos = 5;
        Ejecucion ejecucion = enviar("prueba-corta");
        ahora.set(INICIO.plus(Duration.ofHours(1)));

        trabajo.ejecutar();

        assertThat(avisos.enBandeja.get("mision-" + ejecucion.id() + "-aviso-epica"))
                .startsWith("Obtuviste la épica «Segundo impulso» | ")
                .endsWith("Ya está en tu inventario.");
        assertThat(avisos.enBandeja.get("mision-" + ejecucion.id() + "-aviso"))
                .contains("Aprendió la épica «Segundo impulso».");
    }

    @Test
    @DisplayName("si la bandeja no responde, el aviso se reintenta despues sin repetir ninguna entrega")
    void avisoReintentable() {
        avisos.fallar.set(Dobles.caido("notificaciones"));
        Ejecucion ejecucion = enviar("prueba-corta");
        Instant vence = INICIO.plus(Duration.ofHours(1));
        ahora.set(vence);

        trabajo.ejecutar();

        Ejecucion pendiente = ejecuciones.buscar(ejecucion.id()).orElseThrow();
        assertThat(pendiente.estadoDe(PasoDeLiquidacion.LIBERACION)).isEqualTo(EstadoDePaso.HECHO);
        assertThat(pendiente.estadoDe(PasoDeLiquidacion.CREDITOS)).isEqualTo(EstadoDePaso.HECHO);
        assertThat(pendiente.estadoDe(PasoDeLiquidacion.AVISO)).isEqualTo(EstadoDePaso.PENDIENTE);
        assertThat(pendiente.proximoIntento()).isEqualTo(vence.plusSeconds(30));

        avisos.fallar.set(null);
        ahora.set(vence.plusSeconds(30));
        trabajo.ejecutar();

        Ejecucion liquidada = ejecuciones.buscar(ejecucion.id()).orElseThrow();
        assertThat(liquidada.liquidacionPendiente()).isFalse();
        assertThat(avisos.enBandeja).containsKey("mision-" + ejecucion.id() + "-aviso");
        assertThat(libro.acreditado).hasSize(1);
        assertThat(inventario.llamadas.stream().filter(l -> l.startsWith("liberar"))).hasSize(1);
        assertThat(correo.enviados).hasSize(1);
    }

    @Test
    @DisplayName("un rechazo definitivo de la bandeja no se reintenta ni toca lo ya entregado")
    void avisoRechazado() {
        avisos.fallar.set(new RechazoDelServicio("notificaciones", 400, "El titulo pasa de 200 caracteres"));
        Ejecucion ejecucion = enviar("prueba-corta");
        ahora.set(INICIO.plus(Duration.ofHours(1)));

        trabajo.ejecutar();

        Ejecucion terminada = ejecuciones.buscar(ejecucion.id()).orElseThrow();
        assertThat(terminada.estadoDe(PasoDeLiquidacion.AVISO)).isEqualTo(EstadoDePaso.FALLIDO);
        assertThat(terminada.motivoDe(PasoDeLiquidacion.AVISO)).contains("400");
        assertThat(terminada.estadoDe(PasoDeLiquidacion.CREDITOS)).isEqualTo(EstadoDePaso.HECHO);
        assertThat(terminada.liquidacionPendiente()).isFalse();
    }

    @Test
    @DisplayName("si la vuelta se corta a mitad de los avisos de desbloqueo, el reintento no duplica los ya dados")
    void avisosDeDesbloqueoSinDuplicar() {
        Ejecucion ejecucion = enviar("prueba-corta");
        Instant vence = INICIO.plus(Duration.ofHours(1));
        ahora.set(vence);
        // Llegan la finalizacion y el primer desbloqueo; el segundo encuentra la bandeja caida.
        avisos.fallar.set(Dobles.caido("notificaciones"));
        avisos.fallarDespuesDe = 2;

        trabajo.ejecutar();
        assertThat(avisos.enBandeja).hasSize(2);

        avisos.fallar.set(null);
        ahora.set(vence.plusSeconds(30));
        trabajo.ejecutar();

        String primero = "mision-" + ejecucion.id() + "-aviso-desbloqueo-tras-la-corta";
        assertThat(avisos.enBandeja).hasSize(3).containsKeys(primero,
                "mision-" + ejecucion.id() + "-aviso-desbloqueo-otra-tras-la-corta");
        assertThat(avisos.intentos.stream().filter(primero::equals))
                .as("se repitio la peticion, con el mismo id, y la bandeja no lo duplico").hasSize(2);
        assertThat(ejecuciones.buscar(ejecucion.id()).orElseThrow().liquidacionPendiente()).isFalse();
    }

    // ------------------------------------------------ endurecimiento (FASE 2)

    @Test
    @DisplayName("una ejecucion que no se puede simular se aplaza y deja pasar a las demas: no bloquea la cola")
    void simulacionImposibleNoBloqueaLaCola() {
        prepararCon(List.of(), 1);
        Ejecucion envenenada = enviar("prueba-corta", "h-1");
        Ejecucion sana = enviar("templo-olvidado", "h-2");
        trabajo = new TrabajoDeMisiones(ejecuciones, simulador(Set.of(envenenada.id())), liquidar, parametros, reloj);
        Instant despues = INICIO.plus(Duration.ofHours(12));
        ahora.set(despues);

        // Lote de uno: la envenenada vence antes, asi que es la primera de la cola.
        trabajo.ejecutar();
        trabajo.ejecutar();

        assertThat(ejecuciones.buscar(sana.id()).orElseThrow().estado())
                .as("la segunda vuelta ya no se la come la envenenada").isNotEqualTo(EstadoEjecucion.EN_PROGRESO);
        Ejecucion aplazada = ejecuciones.buscar(envenenada.id()).orElseThrow();
        assertThat(aplazada.estado()).isEqualTo(EstadoEjecucion.EN_PROGRESO);
        assertThat(aplazada.proximoIntento()).isEqualTo(despues.plusSeconds(30));
        assertThat(aplazada.ultimoError()).contains("404");
        assertThat(inventario.bloqueados).as("su heroe sigue en mision: el jugador la puede cancelar")
                .containsKey("h-1").doesNotContainKey("h-2");

        // Y el jugador la puede cancelar: el heroe vuelve.
        cancelar.cancelar(JUGADOR, envenenada.id());
        assertThat(inventario.bloqueados).isEmpty();
    }

    @Test
    @DisplayName("la espera entre intentos de simular crece y tiene un tope corto (cinco minutos)")
    void esperaDeLaSimulacionConTope() {
        Ejecucion envenenada = enviar("prueba-corta");
        trabajo = new TrabajoDeMisiones(ejecuciones, simulador(Set.of(envenenada.id())), liquidar, parametros, reloj);
        Instant momento = INICIO.plus(Duration.ofHours(1));
        List<Duration> esperas = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            ahora.set(momento);
            trabajo.ejecutar();
            Instant siguiente = ejecuciones.buscar(envenenada.id()).orElseThrow().proximoIntento();
            esperas.add(Duration.between(momento, siguiente));
            momento = siguiente;
        }

        assertThat(esperas).containsExactly(Duration.ofSeconds(30), Duration.ofSeconds(60), Duration.ofSeconds(120),
                Duration.ofSeconds(240), Ejecucion.ESPERA_MAXIMA_ANTES_DE_SIMULAR,
                Ejecucion.ESPERA_MAXIMA_ANTES_DE_SIMULAR);
    }

    @Test
    @DisplayName("reproducible: una simulacion cortada y repetida da el mismo resultado que una sin cortes (misma semilla)")
    void simulacionReproducible() {
        Ejecucion limpia = enviar("prueba-corta");
        ahora.set(INICIO.plus(Duration.ofHours(1)));
        trabajo.ejecutar();
        Ejecucion sinCortes = ejecuciones.buscar(limpia.id()).orElseThrow();

        prepararCon(List.of());
        ahora.set(INICIO);
        Ejecucion cortada = enviar("prueba-corta");
        ahora.set(INICIO.plus(Duration.ofHours(1)));
        motor.fallar = Dobles.caido("motor-combate");
        trabajo.ejecutar();
        motor.fallar = null;
        ahora.set(INICIO.plus(Duration.ofHours(1)).plusSeconds(30));
        trabajo.ejecutar();
        Ejecucion repetida = ejecuciones.buscar(cortada.id()).orElseThrow();

        assertThat(repetida.semilla()).isEqualTo(sinCortes.semilla());
        ResultadoDeMision resultado = repetida.resultado();
        assertThat(resultado).isEqualTo(sinCortes.resultado());
        RecompensasDeEjecucion recompensas = repetida.recompensas();
        assertThat(recompensas).isEqualTo(sinCortes.recompensas());
    }

    @Test
    @DisplayName("una mision completada se puede repetir y su experiencia se vuelve a sumar al heroe")
    void repetibleConExperienciaAcumulada() {
        Ejecucion primera = enviar("prueba-corta");
        ahora.set(INICIO.plus(Duration.ofHours(1)));
        trabajo.ejecutar();
        double tras1 = inventario.experienciaSumada.get("h-1");

        ahora.set(INICIO.plus(Duration.ofHours(2)));
        Ejecucion segunda = enviar("prueba-corta");
        ahora.set(INICIO.plus(Duration.ofHours(3)));
        trabajo.ejecutar();

        assertThat(segunda.id()).isNotEqualTo(primera.id());
        assertThat(ejecuciones.buscar(segunda.id()).orElseThrow().estado()).isEqualTo(EstadoEjecucion.COMPLETADA);
        assertThat(inventario.experienciaSumada.get("h-1"))
                .isEqualTo(tras1 + ejecuciones.buscar(segunda.id()).orElseThrow().recompensas().experiencia());
        assertThat(inventario.bloqueados).isEmpty();
    }

    @Test
    @DisplayName("con los avisos apagados no hay pasos de aviso: lo demas se liquida igual")
    void sinAvisos() {
        parametros = new ParametrosDeMisiones(Duration.ofHours(1), Duration.ofSeconds(30), 20,
                true, false, null, null, new ParametrosDeRecompensa(Map.of(), Map.of(), false));
        SimularEjecucion simular = new SimularEjecucion(catalogo, ejecuciones, eventos, heroes, motor,
                new PerfilDeCombateDelHeroe(inventario, productos, heroes),
                new RotacionesPorDefectoDeEnemigos(heroes), parametros, reloj);
        trabajo = new TrabajoDeMisiones(ejecuciones, simular, liquidar, parametros, reloj);
        Ejecucion ejecucion = enviar("prueba-corta");
        ahora.set(INICIO.plus(Duration.ofHours(1)));

        trabajo.ejecutar();

        Ejecucion terminada = ejecuciones.buscar(ejecucion.id()).orElseThrow();
        assertThat(terminada.pasos()).doesNotContainKeys(PasoDeLiquidacion.AVISO, PasoDeLiquidacion.AVISO_EPICA,
                PasoDeLiquidacion.AVISO_DESBLOQUEO);
        assertThat(avisos.enBandeja).isEmpty();
        assertThat(terminada.liquidacionPendiente()).isFalse();
    }
}
