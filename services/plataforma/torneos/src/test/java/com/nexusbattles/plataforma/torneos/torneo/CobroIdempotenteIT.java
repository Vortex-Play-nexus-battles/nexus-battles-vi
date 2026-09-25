package com.nexusbattles.plataforma.torneos.torneo;

import com.nexusbattles.plataforma.torneos.torneo.TorneoRechazado.Motivo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * B10 — cobro, devolucion y premio idempotentes, contra PostgreSQL real y con
 * dobles CON ESTADO del libro, inventario y notificaciones ({@link Dobles}).
 *
 * <p>Las pruebas deliberadas de duplicacion que pide el encargo: dos inicios
 * simultaneos, reintento tras una caida a mitad (el libro cae entre cobros, y
 * el servicio muere despues de cobrar y antes de anotarlo), dos inscripciones
 * por el ultimo cupo, dos administradores creando a la vez. En todas se
 * afirma sobre el SALDO del jugador en el libro, no sobre cuantas llamadas se
 * hicieron: lo que no puede pasar es que alguien pague dos veces.
 */
@Testcontainers
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "torneos.operaciones.tarea-activa=false"})
@Import(Dobles.Configuracion.class)
@DisplayName("Torneos · cobro, devolucion y premio idempotentes (B10)")
class CobroIdempotenteIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    static final AtomicReference<Instant> AHORA = new AtomicReference<>(Instant.parse("2030-01-01T10:00:00Z"));
    static final int COSTO = 10;
    static final int SALDO = 100;

    @Autowired
    private TorneosService servicio;

    @Autowired
    private ProcesadorDeOperaciones procesador;

    @Autowired
    private OperacionRepository operaciones;

    @Autowired
    private PoliticaDePremio premios;

    @Autowired
    private TransactionTemplate transaccion;

    @Autowired
    private Dobles.LibroEnMemoria libro;

    @Autowired
    private Dobles.InventarioEnMemoria inventario;

    @Autowired
    private Dobles.AvisosEnMemoria avisos;

    @Autowired
    private Dobles.SancionesEnMemoria sanciones;

    @MockitoBean
    private Clock reloj;

    private static final Actor ADMIN = Actor.usuario(UUID.randomUUID(), "ADMINISTRADOR");

    @BeforeEach
    void preparar() {
        when(reloj.getZone()).thenReturn(ZoneOffset.UTC);
        when(reloj.instant()).thenAnswer(invocacion -> AHORA.get());
        libro.reiniciar();
        inventario.reiniciar();
        avisos.reiniciar();
        sanciones.reiniciar();
    }

    // ------------------------------------------------------------ apoyo

    private static OffsetDateTime ahora() {
        return OffsetDateTime.ofInstant(AHORA.get(), ZoneOffset.UTC);
    }

    private static void avanzar(long segundos) {
        AHORA.set(AHORA.get().plusSeconds(segundos));
    }

    /** Cada torneo 100 dias despues del anterior, para no chocar con la ventana de 91. */
    private UUID torneoNuevo(int costo) {
        avanzar(100L * 24 * 3600);
        return servicio.crear(ADMIN, new TorneosService.SolicitudDeTorneo("Copa " + UUID.randomUUID(),
                ahora().plusDays(7), costo)).torneo().id();
    }

    /** Un equipo inscrito cuyo capitan paga; devuelve el equipo y deja saldo al capitan. */
    private Equipo inscrito(UUID torneo) {
        Actor capitan = Actor.usuario(UUID.randomUUID(), "JUGADOR");
        libro.darSaldo(capitan.id(), SALDO);
        Equipo equipo = servicio.crearEquipo(capitan, torneo,
                new TorneosService.SolicitudDeEquipo("Equipo " + capitan.id().toString().substring(0, 6), "avatar",
                        UUID.randomUUID()));
        return servicio.inscribir(capitan, torneo, equipo.id());
    }

    private EstadoDeOperaciones.Pago pagoDe(UUID torneo, Equipo equipo) {
        TorneosService.TorneoCompleto t = servicio.obtener(torneo);
        Equipo actual = t.equipos().stream().filter(e -> e.id().equals(equipo.id())).findFirst().orElseThrow();
        return EstadoDeOperaciones.pago(actual, t.operaciones());
    }

    private List<Operacion> deTipo(UUID torneo, Operacion.Tipo tipo) {
        return operaciones.findByTorneoIdOrderByCreadaEnAsc(torneo).stream().filter(o -> o.tipo() == tipo).toList();
    }

    /** Juega los 14 encuentros a mano; gana siempre {@code favorito} si juega, si no el equipo A. */
    private TorneosService.TorneoCompleto jugarTodo(UUID torneo, UUID favorito) {
        TorneosService.TorneoCompleto actual = servicio.obtener(torneo);
        for (int numero = 1; numero <= 14; numero++) {
            Encuentro e = actual.encuentros().get(numero - 1);
            UUID ganador = favorito != null && e.participa(favorito) ? favorito : e.equipoA();
            actual = servicio.registrarResultado(ADMIN, torneo, numero,
                    new TorneosService.SolicitudDeResultado(ganador, null, "Incomparecencia del rival"));
        }
        return actual;
    }

    /** Lo que haria la tarea programada, vuelta tras vuelta, hasta que no quede nada que ya toque. */
    private void drenar() {
        for (int vuelta = 0; vuelta < 100 && procesador.procesarPendientes() > 0; vuelta++) {
            // otra vuelta
        }
    }

    private static <T> List<Future<T>> aLaVez(int hilos, Callable<T> tarea) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<T>> resultados = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            resultados.add(pool.submit(() -> {
                salida.await();
                return tarea.call();
            }));
        }
        salida.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        return resultados;
    }

    private static Throwable fallo(Future<?> futuro) {
        try {
            futuro.get();
            return null;
        } catch (Exception e) {
            return e.getCause();
        }
    }

    // ------------------------------------------------------------ inicio

    @Nested
    @DisplayName("Cobro al iniciar")
    class Cobro {

        @Test
        @DisplayName("dos inicios simultaneos: uno gana, el otro 409, un solo arbol y cada equipo paga UNA vez")
        void dosIniciosSimultaneos() throws Exception {
            UUID torneo = torneoNuevo(COSTO);
            List<Equipo> equipos = List.of(inscrito(torneo), inscrito(torneo), inscrito(torneo));

            List<Future<TorneosService.TorneoCompleto>> resultados = aLaVez(2, () -> servicio.iniciar(ADMIN, torneo));

            List<Throwable> fallos = resultados.stream().map(CobroIdempotenteIT::fallo).toList();
            assertThat(fallos.stream().filter(f -> f == null)).hasSize(1);
            assertThat(fallos.stream().filter(f -> f != null)).singleElement()
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.ESTADO_NO_PERMITE));

            TorneosService.TorneoCompleto t = servicio.obtener(torneo);
            assertThat(t.equipos()).hasSize(8);
            assertThat(t.encuentros()).hasSize(14);
            assertThat(deTipo(torneo, Operacion.Tipo.COBRO_INSCRIPCION)).hasSize(3)
                    .allSatisfy(op -> assertThat(op.estado()).isEqualTo(Operacion.Estado.HECHA));
            for (Equipo e : equipos) {
                assertThat(libro.bruto(e.pagadoPor())).as("cobrado una sola vez").isEqualTo(SALDO - COSTO);
                assertThat(libro.reservado(e.pagadoPor())).isZero();
                assertThat(pagoDe(torneo, e)).isEqualTo(EstadoDeOperaciones.Pago.COBRADO);
            }
            assertThat(libro.consumosEfectivos.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("el libro cae a mitad: el torneo arranca, el cobro que falto se reintenta y nadie paga dos veces")
        void libroCaeAMitad() {
            UUID torneo = torneoNuevo(COSTO);
            Equipo primero = inscrito(torneo);
            Equipo segundo = inscrito(torneo);
            Equipo tercero = inscrito(torneo);
            libro.consumosQueFallan.set(1); // falla el cobro del primero que se intente despues del inicio

            TorneosService.TorneoCompleto enCurso = servicio.iniciar(ADMIN, torneo);

            assertThat(enCurso.torneo().estado()).isEqualTo(Torneo.Estado.EN_CURSO);
            List<EstadoDeOperaciones.Pago> pagos = List.of(pagoDe(torneo, primero), pagoDe(torneo, segundo), pagoDe(torneo, tercero));
            assertThat(pagos).containsOnlyOnce(EstadoDeOperaciones.Pago.COBRO_PENDIENTE)
                    .filteredOn(p -> p == EstadoDeOperaciones.Pago.COBRADO).hasSize(2);
            Operacion pendiente = deTipo(torneo, Operacion.Tipo.COBRO_INSCRIPCION).stream()
                    .filter(o -> o.estado() == Operacion.Estado.REINTENTABLE).findFirst().orElseThrow();
            assertThat(pendiente.intentos()).isEqualTo(1);
            assertThat(pendiente.ultimoError()).contains("no respondio");

            // La tarea programada todavia no lo intenta: espera su turno (espera exponencial).
            drenar();
            assertThat(operaciones.findById(pendiente.id()).orElseThrow().estado()).isEqualTo(Operacion.Estado.REINTENTABLE);
            avanzar(60);
            drenar();
            // Y una segunda pasada no hace nada: ya esta cobrado.
            avanzar(3600);
            drenar();

            for (Equipo e : List.of(primero, segundo, tercero)) {
                assertThat(pagoDe(torneo, e)).isEqualTo(EstadoDeOperaciones.Pago.COBRADO);
                assertThat(libro.bruto(e.pagadoPor())).isEqualTo(SALDO - COSTO);
            }
            assertThat(libro.consumosEfectivos.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("el servicio muere despues de cobrar y antes de anotarlo: el plazo vence, se retoma y no cobra otra vez")
        void caidaDespuesDeCobrar() {
            UUID torneo = torneoNuevo(COSTO);
            Equipo equipo = inscrito(torneo);
            libro.caido = true;
            servicio.iniciar(ADMIN, torneo);
            assertThat(pagoDe(torneo, equipo)).isEqualTo(EstadoDeOperaciones.Pago.COBRO_PENDIENTE);

            // Lo que deja una caida real: el libro YA cobro la reserva, pero la
            // operacion quedo reclamada (EN_CURSO) y nadie anoto el resultado.
            libro.caido = false;
            libro.consumirPorFuera(equipo.reservaId());
            Operacion cobro = deTipo(torneo, Operacion.Tipo.COBRO_INSCRIPCION).getFirst();
            simularReclamadaPorUnaEjecucionMuerta(cobro);

            // Mientras el plazo no vence, nadie la toca.
            avanzar(60);
            drenar();
            assertThat(operaciones.findById(cobro.id()).orElseThrow().estado()).isEqualTo(Operacion.Estado.EN_CURSO);

            avanzar(3600);
            drenar();

            assertThat(pagoDe(torneo, equipo)).isEqualTo(EstadoDeOperaciones.Pago.COBRADO);
            assertThat(libro.bruto(equipo.pagadoPor())).as("la segunda llamada es idempotente").isEqualTo(SALDO - COSTO);
            assertThat(libro.consumosEfectivos.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("una reserva que el libro ya libero no se puede cobrar: REQUIERE_REVISION, sin reintentos infinitos")
        void reservaYaLiberada() {
            UUID torneo = torneoNuevo(COSTO);
            Equipo equipo = inscrito(torneo);
            libro.liberar(equipo.reservaId()); // alguien la libero por fuera

            servicio.iniciar(ADMIN, torneo);

            assertThat(pagoDe(torneo, equipo)).isEqualTo(EstadoDeOperaciones.Pago.REQUIERE_REVISION);
            Operacion cobro = deTipo(torneo, Operacion.Tipo.COBRO_INSCRIPCION).getFirst();
            assertThat(cobro.estado()).isEqualTo(Operacion.Estado.FALLIDA);
            assertThat(cobro.ultimoError()).contains("reserva-ya-liberada");
            assertThat(libro.bruto(equipo.pagadoPor())).isEqualTo(SALDO);
        }
    }

    // ------------------------------------------------------------ cancelacion

    @Nested
    @DisplayName("Devolucion al cancelar")
    class Devolucion {

        @Test
        @DisplayName("con el libro caido se cancela igual; la devolucion queda pendiente y la tarea la completa una vez")
        void libroCaidoAlCancelar() {
            UUID torneo = torneoNuevo(COSTO);
            Equipo uno = inscrito(torneo);
            Equipo dos = inscrito(torneo);
            libro.caido = true;

            TorneosService.TorneoCompleto cancelado = servicio.cancelar(ADMIN, torneo, "Falla del servidor");

            assertThat(cancelado.torneo().estado()).isEqualTo(Torneo.Estado.CANCELADO);
            assertThat(pagoDe(torneo, uno)).isEqualTo(EstadoDeOperaciones.Pago.DEVOLUCION_PENDIENTE);
            assertThat(libro.reservado(uno.pagadoPor())).isEqualTo(COSTO);

            libro.caido = false;
            avanzar(3600);
            drenar();

            for (Equipo e : List.of(uno, dos)) {
                assertThat(pagoDe(torneo, e)).isEqualTo(EstadoDeOperaciones.Pago.DEVUELTO);
                assertThat(libro.reservado(e.pagadoPor())).isZero();
                assertThat(libro.bruto(e.pagadoPor())).isEqualTo(SALDO);
            }
        }

        @Test
        @DisplayName("una reserva que ya estaba cobrada se devuelve acreditando, con la clave de la devolucion (una vez)")
        void reservaYaCobrada() {
            UUID torneo = torneoNuevo(COSTO);
            Equipo equipo = inscrito(torneo);
            // Lo que dejaba la version 1.1.0: el inicio cobro y fallo a mitad; el torneo seguia abierto.
            libro.consumirPorFuera(equipo.reservaId());
            assertThat(libro.bruto(equipo.pagadoPor())).isEqualTo(SALDO - COSTO);

            servicio.cancelar(ADMIN, torneo, "No se jugo");
            avanzar(3600);
            servicio.reintentarOperaciones(ADMIN, torneo);

            assertThat(pagoDe(torneo, equipo)).isEqualTo(EstadoDeOperaciones.Pago.DEVUELTO);
            assertThat(libro.bruto(equipo.pagadoPor())).isEqualTo(SALDO);
            assertThat(libro.acreditados).contains(Operacion.clave(torneo, equipo.pagadoPor(), "devolucion"));
        }

        @Test
        @DisplayName("cancelar avisa a cada integrante; al pagador le dice que su inscripcion se devuelve")
        void avisos() {
            UUID torneo = torneoNuevo(COSTO);
            Equipo equipo = inscrito(torneo);
            servicio.cancelar(ADMIN, torneo, "Sin quorum");
            procesador.procesarDelTorneo(torneo, ProcesadorDeOperaciones.TODAS);

            assertThat(avisos.de(equipo.pagadoPor()))
                    .anySatisfy(a -> assertThat(a.cuerpo()).contains("Sin quorum").contains("se devuelve"));
            UUID companero = equipo.integrantes().get(1);
            assertThat(avisos.de(companero))
                    .anySatisfy(a -> assertThat(a.cuerpo()).contains("Sin quorum").doesNotContain("se devuelve"));
        }
    }

    // ------------------------------------------------------------ inscripcion

    @Nested
    @DisplayName("Inscripcion concurrente")
    class InscripcionConcurrente {

        @Test
        @DisplayName("dos equipos por el ultimo cupo: entra uno, el otro 409 y su reserva se libera")
        void ultimoCupo() throws Exception {
            UUID torneo = torneoNuevo(COSTO);
            for (int i = 0; i < 7; i++) {
                inscrito(torneo);
            }
            Actor a = Actor.usuario(UUID.randomUUID(), "JUGADOR");
            Actor b = Actor.usuario(UUID.randomUUID(), "JUGADOR");
            libro.darSaldo(a.id(), SALDO);
            libro.darSaldo(b.id(), SALDO);
            Equipo ea = servicio.crearEquipo(a, torneo, new TorneosService.SolicitudDeEquipo("Los A", "av", UUID.randomUUID()));
            Equipo eb = servicio.crearEquipo(b, torneo, new TorneosService.SolicitudDeEquipo("Los B", "av", UUID.randomUUID()));

            List<Future<Equipo>> resultados = new ArrayList<>();
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch salida = new CountDownLatch(1);
            resultados.add(pool.submit(() -> { salida.await(); return servicio.inscribir(a, torneo, ea.id()); }));
            resultados.add(pool.submit(() -> { salida.await(); return servicio.inscribir(b, torneo, eb.id()); }));
            salida.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

            List<Throwable> fallos = resultados.stream().map(CobroIdempotenteIT::fallo).toList();
            assertThat(fallos.stream().filter(f -> f == null)).hasSize(1);
            assertThat(fallos.stream().filter(f -> f != null)).singleElement()
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.CUPO_AGOTADO));
            assertThat(servicio.obtener(torneo).inscritos()).isEqualTo(8);
            // Quien perdio el cupo no se queda con creditos apartados.
            assertThat(libro.reservado(a.id()) + libro.reservado(b.id())).isEqualTo(COSTO);
        }

        @Test
        @DisplayName("el companero reintenta: su reserva es suya, no la del otro integrante")
        void clavePorPagador() {
            UUID torneo = torneoNuevo(COSTO);
            Actor capitan = Actor.usuario(UUID.randomUUID(), "JUGADOR");
            UUID companero = UUID.randomUUID();
            libro.darSaldo(capitan.id(), SALDO);
            libro.darSaldo(companero, SALDO);
            Equipo equipo = servicio.crearEquipo(capitan, torneo,
                    new TorneosService.SolicitudDeEquipo("Los Dos", "avatar", companero));
            // El capitan reserva (su clave), pero la inscripcion no llega a confirmarse.
            libro.reservar(capitan.id(), COSTO, Operacion.clave(torneo, capitan.id(), "inscripcion"), "torneo-" + torneo);

            Equipo inscrito = servicio.inscribir(Actor.usuario(companero, "JUGADOR"), torneo, equipo.id());

            assertThat(inscrito.pagadoPor()).isEqualTo(companero);
            assertThat(libro.reservado(companero)).isEqualTo(COSTO);
        }

        @Test
        @DisplayName("la inscripcion confirmada avisa a los dos integrantes con su comprobante")
        void avisaAlInscribir() {
            UUID torneo = torneoNuevo(COSTO);
            Equipo equipo = inscrito(torneo);
            procesador.procesarDelTorneo(torneo, ProcesadorDeOperaciones.TODAS);
            for (UUID integrante : equipo.integrantes()) {
                assertThat(avisos.de(integrante)).singleElement().satisfies(a -> {
                    assertThat(a.titulo()).startsWith("Inscripción confirmada");
                    assertThat(a.cuerpo()).contains("posición 1").contains(COSTO + " créditos");
                    assertThat(a.id()).isEqualTo(Operacion.clave(torneo, integrante, "aviso-inscripcion"));
                });
            }
            // Una segunda pasada no duplica nada.
            procesador.procesarDelTorneo(torneo, ProcesadorDeOperaciones.TODAS);
            assertThat(avisos.de(equipo.integrantes().get(0))).hasSize(1);
            assertThat(avisos.de(equipo.integrantes().get(1))).hasSize(1);
        }
    }

    // ------------------------------------------------------------ ventana

    @Test
    @DisplayName("dos administradores crean a la vez: un solo torneo en la ventana de 91 dias")
    void ventanaSimultanea() throws Exception {
        avanzar(100L * 24 * 3600);
        List<Future<TorneosService.TorneoCompleto>> resultados = aLaVez(2, () -> servicio.crear(ADMIN,
                new TorneosService.SolicitudDeTorneo("Copa " + UUID.randomUUID(), ahora().plusDays(3), 0)));
        List<Throwable> fallos = resultados.stream().map(CobroIdempotenteIT::fallo).toList();
        assertThat(fallos.stream().filter(f -> f == null)).hasSize(1);
        assertThat(fallos.stream().filter(f -> f != null)).singleElement()
                .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.VENTANA_DE_91_DIAS));
    }

    // ------------------------------------------------------------ premio

    @Nested
    @DisplayName("Premio del campeon (RF-TOR-007)")
    class Premio {

        @Test
        @DisplayName("al jugarse la final, cada integrante recibe creditos (refId) y la epica (Idempotency-Key), una vez")
        void premioCompleto() {
            UUID torneo = torneoNuevo(COSTO);
            Equipo campeon = inscrito(torneo);
            servicio.iniciar(ADMIN, torneo);
            int antes = libro.bruto(campeon.pagadoPor());

            TorneosService.TorneoCompleto fin = jugarTodo(torneo, campeon.id());

            assertThat(fin.torneo().campeonEquipoId()).isEqualTo(campeon.id());
            EstadoDeOperaciones.Premio premio = EstadoDeOperaciones.premio(fin.torneo(), fin.premio(), fin.operaciones());
            assertThat(premio.estado()).isEqualTo(EstadoDeOperaciones.EstadoPremio.ENTREGADO);
            assertThat(premio.creditosPorIntegrante()).isEqualTo(premios.premio().creditosPorIntegrante());
            assertThat(premio.entregas()).hasSize(2).allSatisfy(e -> {
                assertThat(e.estado()).isEqualTo(EstadoDeOperaciones.EstadoEntrega.ENTREGADO);
                assertThat(e.creditosEntregados()).isTrue();
                assertThat(e.epicaEntregada()).isTrue();
            });
            for (UUID integrante : campeon.integrantes()) {
                assertThat(libro.acreditados).contains(Operacion.clave(torneo, integrante, "premio"));
                assertThat(inventario.porClave).containsKey(Operacion.clave(torneo, integrante, "epica"));
                assertThat(inventario.porClave.get(Operacion.clave(torneo, integrante, "epica")).productoId())
                        .isEqualTo(premios.premio().epicaProductoId());
            }
            assertThat(libro.bruto(campeon.pagadoPor())).isEqualTo(antes + premios.premio().creditosPorIntegrante());

            // Reintentar todo no entrega nada dos veces.
            avanzar(3600);
            servicio.reintentarOperaciones(ADMIN, torneo);
            drenar();
            assertThat(libro.bruto(campeon.pagadoPor())).isEqualTo(antes + premios.premio().creditosPorIntegrante());
            assertThat(inventario.delJugador(campeon.pagadoPor())).isEqualTo(1);
            // Y el aviso del premio llega a la bandeja.
            assertThat(avisos.de(campeon.pagadoPor()))
                    .anySatisfy(a -> assertThat(a.titulo()).startsWith("Premio del torneo"));
        }

        @Test
        @DisplayName("un integrante sancionado al premiar no recibe nada (CA-02); su companero si")
        void sancionado() {
            UUID torneo = torneoNuevo(0);
            Equipo campeon = inscrito(torneo);
            UUID sancionado = campeon.integrantes().get(1);
            sanciones.sancionados.add(sancionado);
            servicio.iniciar(ADMIN, torneo);

            TorneosService.TorneoCompleto fin = jugarTodo(torneo, campeon.id());

            EstadoDeOperaciones.Premio premio = EstadoDeOperaciones.premio(fin.torneo(), fin.premio(), fin.operaciones());
            assertThat(premio.estado()).isEqualTo(EstadoDeOperaciones.EstadoPremio.ENTREGADO);
            assertThat(premio.entregas()).filteredOn(e -> e.uid().equals(sancionado)).singleElement()
                    .satisfies(e -> {
                        assertThat(e.estado()).isEqualTo(EstadoDeOperaciones.EstadoEntrega.EXCLUIDO_POR_SANCION);
                        assertThat(e.creditosEntregados()).isFalse();
                    });
            assertThat(libro.bruto(sancionado)).isZero();
            assertThat(inventario.delJugador(sancionado)).isZero();
            assertThat(libro.bruto(campeon.pagadoPor())).isEqualTo(SALDO + premios.premio().creditosPorIntegrante());
        }

        @Test
        @DisplayName("inventario falla a mitad: los creditos no se repiten y la epica llega en el reintento (nunca a medias)")
        void epicaEnElReintento() {
            UUID torneo = torneoNuevo(0);
            Equipo campeon = inscrito(torneo);
            servicio.iniciar(ADMIN, torneo);
            inventario.entregasQueFallan.set(2);

            TorneosService.TorneoCompleto fin = jugarTodo(torneo, campeon.id());
            EstadoDeOperaciones.Premio aMedias = EstadoDeOperaciones.premio(fin.torneo(), fin.premio(), fin.operaciones());
            assertThat(aMedias.estado()).isEqualTo(EstadoDeOperaciones.EstadoPremio.PENDIENTE);
            assertThat(aMedias.entregas()).allSatisfy(e -> {
                assertThat(e.creditosEntregados()).isTrue();
                assertThat(e.epicaEntregada()).isFalse();
            });

            avanzar(3600);
            procesador.procesarDelTorneo(torneo, ProcesadorDeOperaciones.TODAS);

            TorneosService.TorneoCompleto despues = servicio.obtener(torneo);
            EstadoDeOperaciones.Premio completo = EstadoDeOperaciones.premio(despues.torneo(), despues.premio(), despues.operaciones());
            assertThat(completo.estado()).isEqualTo(EstadoDeOperaciones.EstadoPremio.ENTREGADO);
            assertThat(libro.bruto(campeon.pagadoPor())).isEqualTo(SALDO + premios.premio().creditosPorIntegrante());
        }

        @Test
        @DisplayName("una epica inexistente queda para revision; corregida, el reintento del administrador la entrega")
        void epicaInexistente() {
            UUID torneo = torneoNuevo(0);
            Equipo campeon = inscrito(torneo);
            servicio.iniciar(ADMIN, torneo);
            inventario.productoInexistente = true;

            TorneosService.TorneoCompleto fin = jugarTodo(torneo, campeon.id());
            assertThat(EstadoDeOperaciones.premio(fin.torneo(), fin.premio(), fin.operaciones()).estado())
                    .isEqualTo(EstadoDeOperaciones.EstadoPremio.REQUIERE_REVISION);

            inventario.productoInexistente = false;
            TorneosService.TorneoCompleto reintentado = servicio.reintentarOperaciones(ADMIN, torneo);
            assertThat(EstadoDeOperaciones.premio(reintentado.torneo(), reintentado.premio(), reintentado.operaciones()).estado())
                    .isEqualTo(EstadoDeOperaciones.EstadoPremio.ENTREGADO);
            assertThat(libro.bruto(campeon.pagadoPor())).isEqualTo(SALDO + premios.premio().creditosPorIntegrante());
            assertThatThrownBy(() -> servicio.reintentarOperaciones(Actor.usuario(UUID.randomUUID(), "JUGADOR"), torneo))
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.PERMISO_INSUFICIENTE));
        }

        @Test
        @DisplayName("sanciones no responde al premiar: no se decide a ciegas, se reintenta")
        void sancionesCaidas() {
            UUID torneo = torneoNuevo(0);
            Equipo campeon = inscrito(torneo);
            servicio.iniciar(ADMIN, torneo);
            sanciones.caido = true;

            TorneosService.TorneoCompleto fin = jugarTodo(torneo, campeon.id());
            assertThat(EstadoDeOperaciones.premio(fin.torneo(), fin.premio(), fin.operaciones()).estado())
                    .isEqualTo(EstadoDeOperaciones.EstadoPremio.PENDIENTE);
            assertThat(libro.bruto(campeon.pagadoPor())).isEqualTo(SALDO);

            sanciones.caido = false;
            avanzar(3600);
            procesador.procesarDelTorneo(torneo, ProcesadorDeOperaciones.TODAS);
            assertThat(libro.bruto(campeon.pagadoPor())).isEqualTo(SALDO + premios.premio().creditosPorIntegrante());
        }

        @Test
        @DisplayName("si gana la maquina no hay premio (NO_APLICA)")
        void ganaLaMaquina() {
            UUID torneo = torneoNuevo(0);
            Equipo humano = inscrito(torneo);
            servicio.iniciar(ADMIN, torneo);
            UUID maquina = servicio.obtener(torneo).equipos().stream().filter(Equipo::ia).findFirst().orElseThrow().id();

            TorneosService.TorneoCompleto fin = jugarTodo(torneo, maquina);

            assertThat(fin.torneo().campeonEquipoId()).isNotEqualTo(humano.id());
            assertThat(EstadoDeOperaciones.premio(fin.torneo(), fin.premio(), fin.operaciones()).estado())
                    .isEqualTo(EstadoDeOperaciones.EstadoPremio.NO_APLICA);
            assertThat(deTipo(torneo, Operacion.Tipo.PREMIO)).isEmpty();
        }

        @Test
        @DisplayName("con correo configurado cada aviso sale tambien por correo, con su propia clave")
        void conCorreo() {
            avisos.conCorreo = true;
            UUID torneo = torneoNuevo(0);
            Equipo equipo = inscrito(torneo);
            procesador.procesarDelTorneo(torneo, ProcesadorDeOperaciones.TODAS);
            assertThat(avisos.correos).containsExactlyInAnyOrder(
                    Operacion.clave(torneo, equipo.integrantes().get(0), "correo-inscripcion"),
                    Operacion.clave(torneo, equipo.integrantes().get(1), "correo-inscripcion"));
        }
    }

    /**
     * Deja la operacion como la dejaria una ejecucion que la reclamo y murio
     * antes de anotar nada: EN_CURSO, con su plazo corriendo. Se usa el mismo
     * {@code reclamar} que usa el procesador.
     */
    private void simularReclamadaPorUnaEjecucionMuerta(Operacion operacion) {
        OffsetDateTime cuando = ahora().plusSeconds(20);
        Integer cambiadas = transaccion.execute(estado -> operaciones.reclamar(operacion.id(),
                ProcesadorDeOperaciones.POR_ATENDER, Operacion.Estado.EN_CURSO, cuando, cuando.plusSeconds(90)));
        assertThat(cambiadas).isEqualTo(1);
    }
}
