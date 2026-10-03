package nexus.misiones.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
    private Dobles.Directorio directorio;
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
        ejecuciones = new Dobles.Ejecuciones();
        inventario = new Dobles.Inventario().conHeroe("h-1", JUGADOR, "p-armas", true);
        productos = new Dobles.Productos();
        productos.prototipos.put("p-armas", "Guerrero Armas");
        heroes = new Dobles.Heroes();
        motor = new Dobles.Motor();
        eventos = new Dobles.Eventos();
        libro = new Dobles.Libro();
        correo = new Dobles.Correo();
        directorio = new Dobles.Directorio();
        directorio.contactos.put(JUGADOR, new DirectorioDeJugadores.Contacto("vorn@ejemplo.com", "vorn"));
        ParametrosDeMisiones parametros = new ParametrosDeMisiones(Duration.ofHours(1), Duration.ofSeconds(30), 20,
                true, null, null, new ParametrosDeRecompensa(Map.of(), Map.of(), false));
        Dobles.Catalogo catalogo = new Dobles.Catalogo(List.of(Misiones.templo(),
                Misiones.historia("prueba-corta", List.of())), tabla20);
        matricular = new MatricularHeroe(catalogo, ejecuciones, new Dobles.Estrategias(), inventario, productos,
                heroes, parametros, reloj, () -> 7L);
        SimularEjecucion simular = new SimularEjecucion(catalogo, ejecuciones, eventos, heroes, motor,
                new PerfilDeCombateDelHeroe(inventario, productos, heroes),
                new RotacionesPorDefectoDeEnemigos(heroes), parametros, reloj);
        LiquidarEjecucion liquidar = new LiquidarEjecucion(ejecuciones, catalogo, inventario, libro, directorio,
                correo, parametros, reloj);
        trabajo = new TrabajoDeMisiones(ejecuciones, simular, liquidar, parametros, reloj);
        cancelar = new CancelarEjecucion(ejecuciones, liquidar, reloj);
    }

    private Ejecucion enviar(String mision) {
        return matricular.matricular(JUGADOR,
                new SolicitudDeMatricula(mision, "h-1", List.of(), null, null)).ejecucion();
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
    @DisplayName("si heroes o el motor no responden, la simulacion espera a la siguiente vuelta")
    void simulacionReintentable() {
        heroes.fallarAlDecidir = Dobles.caido("heroes");
        Ejecucion ejecucion = enviar("prueba-corta");
        ahora.set(INICIO.plus(Duration.ofHours(1)));

        trabajo.ejecutar();
        assertThat(ejecuciones.buscar(ejecucion.id()).orElseThrow().estado()).isEqualTo(EstadoEjecucion.EN_PROGRESO);

        heroes.fallarAlDecidir = null;
        trabajo.ejecutar();
        assertThat(ejecuciones.buscar(ejecucion.id()).orElseThrow().estado()).isEqualTo(EstadoEjecucion.COMPLETADA);
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
}
