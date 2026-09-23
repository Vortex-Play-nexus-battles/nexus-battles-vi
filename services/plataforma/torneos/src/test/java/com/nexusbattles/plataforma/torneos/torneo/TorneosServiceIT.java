package com.nexusbattles.plataforma.torneos.torneo;

import com.nexusbattles.plataforma.torneos.torneo.TorneoRechazado.Motivo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Los casos de uso contra PostgreSQL real, con el libro, la lista negra y
 * las sanciones simulados en el borde del servicio.
 */
@Testcontainers
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@DisplayName("Torneos · casos de uso (HU-TOR-001..005, HU-ADM-005)")
class TorneosServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    /** Reloj movible: la ventana de 91 dias se prueba sin esperar 91 dias. */
    static final AtomicReference<Instant> AHORA = new AtomicReference<>(Instant.parse("2026-10-01T10:00:00Z"));

    @Autowired
    private TorneosService servicio;

    @MockitoBean
    private LibroDeCreditos libro;

    @MockitoBean
    private FiltroDeNombres filtro;

    @MockitoBean
    private ConsultaDeSanciones sanciones;

    /** Reemplaza el reloj del sistema por uno que obedece a AHORA. */
    @MockitoBean
    private Clock reloj;

    @BeforeEach
    void relojMovible() {
        when(reloj.getZone()).thenReturn(ZoneOffset.UTC);
        when(reloj.instant()).thenAnswer(invocacion -> AHORA.get());
    }

    private static final Actor ADMIN = Actor.usuario(UUID.randomUUID(), "ADMINISTRADOR");
    private static final Actor JUGADORA = Actor.usuario(UUID.randomUUID(), "JUGADOR");
    private static final Actor JUGADOR2 = Actor.usuario(UUID.randomUUID(), "JUGADOR");
    private static final Actor JUGADOR3 = Actor.usuario(UUID.randomUUID(), "JUGADOR");
    private static final Actor SALAS = Actor.servicio("salas-partidas");

    private OffsetDateTime ahora() {
        return OffsetDateTime.ofInstant(AHORA.get(), ZoneOffset.UTC);
    }

    /** Cada prueba se aleja 100 dias de la anterior para no chocar con la ventana. */
    private TorneosService.TorneoCompleto torneoNuevo(int costo) {
        AHORA.set(AHORA.get().plusSeconds(100L * 24 * 3600));
        when(filtro.aprobado(anyString())).thenReturn(true);
        when(sanciones.sancionado(any())).thenReturn(false);
        return servicio.crear(ADMIN, new TorneosService.SolicitudDeTorneo("Copa " + UUID.randomUUID(),
                ahora().plusDays(7), costo));
    }

    @Nested
    @DisplayName("HU-TOR-001 · creacion")
    class Creacion {

        @Test
        @DisplayName("un administrador crea el torneo en inscripciones abiertas con 8 cupos y 0 inscritos")
        void crea() {
            TorneosService.TorneoCompleto t = torneoNuevo(10);
            assertThat(t.torneo().estado()).isEqualTo(Torneo.Estado.INSCRIPCIONES_ABIERTAS);
            assertThat(t.torneo().costoInscripcion()).isEqualTo(10);
            assertThat(t.inscritos()).isZero();
            assertThat(t.encuentros()).isEmpty();
            assertThat(servicio.listar()).extracting(x -> x.torneo().id()).contains(t.torneo().id());
        }

        @Test
        @DisplayName("un segundo torneo dentro de 91 dias se rechaza con la fecha en que se podra (CA-02)")
        void ventana() {
            TorneosService.TorneoCompleto primero = torneoNuevo(0);
            AHORA.set(AHORA.get().plusSeconds(30L * 24 * 3600));
            assertThatThrownBy(() -> servicio.crear(ADMIN, new TorneosService.SolicitudDeTorneo("Otro", ahora().plusDays(1), 0)))
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> {
                        assertThat(ex.motivo()).isEqualTo(Motivo.VENTANA_DE_91_DIAS);
                        assertThat(ex.proximaFechaPosible()).isEqualTo(primero.torneo().creadoEn().plusDays(91));
                    });
            // Un torneo cancelado no cuenta para la ventana.
            servicio.cancelar(ADMIN, primero.torneo().id(), "Sin quorum");
            assertThat(servicio.crear(ADMIN, new TorneosService.SolicitudDeTorneo("Otro", ahora().plusDays(1), 0))
                    .torneo().estado()).isEqualTo(Torneo.Estado.INSCRIPCIONES_ABIERTAS);
        }

        @Test
        @DisplayName("sin privilegio 403 (CA-03, D-21); sin nombre 400")
        void permisos() {
            assertThatThrownBy(() -> servicio.crear(JUGADORA, new TorneosService.SolicitudDeTorneo("Copa", ahora(), 0)))
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.PERMISO_INSUFICIENTE));
            assertThatThrownBy(() -> servicio.crear(SALAS, new TorneosService.SolicitudDeTorneo("Copa", ahora(), 0)))
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.PERMISO_INSUFICIENTE));
            assertThatThrownBy(() -> servicio.crear(ADMIN, new TorneosService.SolicitudDeTorneo("x", ahora(), 0)))
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.SOLICITUD_INVALIDA));
            assertThatThrownBy(() -> servicio.crear(ADMIN, new TorneosService.SolicitudDeTorneo("Copa", ahora(), -1)))
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.SOLICITUD_INVALIDA));
        }

        @Test
        @DisplayName("cancelar antes del inicio devuelve las reservas y deja el motivo (CA-04)")
        void cancelar() {
            TorneosService.TorneoCompleto t = torneoNuevo(25);
            UUID reserva = UUID.randomUUID();
            when(libro.reservar(eq(JUGADORA.id()), eq(25), anyString(), anyString())).thenReturn(reserva);
            Equipo equipo = servicio.crearEquipo(JUGADORA, t.torneo().id(),
                    new TorneosService.SolicitudDeEquipo("Los Valientes", "avatar-1", JUGADOR2.id()));
            servicio.inscribir(JUGADORA, t.torneo().id(), equipo.id());

            assertThatThrownBy(() -> servicio.cancelar(ADMIN, t.torneo().id(), " "))
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.SOLICITUD_INVALIDA));
            TorneosService.TorneoCompleto cancelado = servicio.cancelar(ADMIN, t.torneo().id(), "Falla del servidor");
            assertThat(cancelado.torneo().estado()).isEqualTo(Torneo.Estado.CANCELADO);
            assertThat(cancelado.torneo().motivoCancelacion()).isEqualTo("Falla del servidor");
            verify(libro).liberar(reserva);
            assertThatThrownBy(() -> servicio.cancelar(ADMIN, t.torneo().id(), "otra vez"))
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.ESTADO_NO_PERMITE));
        }
    }

    @Nested
    @DisplayName("HU-TOR-003 · equipos")
    class Equipos {

        @Test
        @DisplayName("nombre y avatar pasan por la lista negra; el rechazo no registra nada")
        void listaNegra() {
            TorneosService.TorneoCompleto t = torneoNuevo(0);
            when(filtro.aprobado("Los Groseros")).thenReturn(false);
            assertThatThrownBy(() -> servicio.crearEquipo(JUGADORA, t.torneo().id(),
                    new TorneosService.SolicitudDeEquipo("Los Groseros", "avatar-1", JUGADOR2.id())))
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.NOMBRE_RECHAZADO));
            when(filtro.aprobado("feo")).thenReturn(false);
            assertThatThrownBy(() -> servicio.crearEquipo(JUGADORA, t.torneo().id(),
                    new TorneosService.SolicitudDeEquipo("Los Buenos", "feo", JUGADOR2.id())))
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.NOMBRE_RECHAZADO));
            assertThat(servicio.obtener(t.torneo().id()).equipos()).isEmpty();
        }

        @Test
        @DisplayName("un jugador no esta en dos equipos del mismo torneo; el capitan sustituye antes de inscribirse")
        void unSoloEquipoPorJugador() {
            TorneosService.TorneoCompleto t = torneoNuevo(0);
            Equipo equipo = servicio.crearEquipo(JUGADORA, t.torneo().id(),
                    new TorneosService.SolicitudDeEquipo("Los Valientes", "avatar-1", JUGADOR2.id()));
            assertThat(equipo.integrantes()).containsExactly(JUGADORA.id(), JUGADOR2.id());
            assertThat(equipo.inscrito()).isFalse();

            assertThatThrownBy(() -> servicio.crearEquipo(JUGADOR3, t.torneo().id(),
                    new TorneosService.SolicitudDeEquipo("Los Otros", "avatar-2", JUGADOR2.id())))
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.JUGADOR_YA_EN_EQUIPO));
            assertThatThrownBy(() -> servicio.crearEquipo(JUGADORA, t.torneo().id(),
                    new TorneosService.SolicitudDeEquipo("Yo solo", "avatar-2", JUGADORA.id())))
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.SOLICITUD_INVALIDA));

            assertThatThrownBy(() -> servicio.sustituirCompanero(JUGADOR2, t.torneo().id(), equipo.id(), JUGADOR3.id()))
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.PERMISO_INSUFICIENTE));
            Equipo cambiado = servicio.sustituirCompanero(JUGADORA, t.torneo().id(), equipo.id(), JUGADOR3.id());
            assertThat(cambiado.integrantes()).containsExactly(JUGADORA.id(), JUGADOR3.id());

            servicio.inscribir(JUGADORA, t.torneo().id(), equipo.id());
            assertThatThrownBy(() -> servicio.sustituirCompanero(JUGADORA, t.torneo().id(), equipo.id(), JUGADOR2.id()))
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.ESTADO_NO_PERMITE));
        }
    }

    @Nested
    @DisplayName("HU-TOR-002 · inscripcion y pago")
    class Inscripcion {

        @Test
        @DisplayName("inscribir reserva el costo en el libro con clave idempotente y da posicion; gratuito no toca el libro")
        void reserva() {
            TorneosService.TorneoCompleto t = torneoNuevo(30);
            UUID reserva = UUID.randomUUID();
            Equipo equipo = servicio.crearEquipo(JUGADORA, t.torneo().id(),
                    new TorneosService.SolicitudDeEquipo("Los Valientes", "avatar-1", JUGADOR2.id()));
            when(libro.reservar(eq(JUGADOR2.id()), eq(30), eq("torneo-" + t.torneo().id() + "-equipo-" + equipo.id()),
                    eq("torneo-" + t.torneo().id()))).thenReturn(reserva);

            assertThatThrownBy(() -> servicio.inscribir(JUGADOR3, t.torneo().id(), equipo.id()))
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.PERMISO_INSUFICIENTE));
            Equipo inscrito = servicio.inscribir(JUGADOR2, t.torneo().id(), equipo.id());
            assertThat(inscrito.inscrito()).isTrue();
            assertThat(inscrito.posicion()).isEqualTo(1);
            assertThat(inscrito.pagadoPor()).isEqualTo(JUGADOR2.id());
            assertThat(inscrito.reservaId()).isEqualTo(reserva);
            assertThatThrownBy(() -> servicio.inscribir(JUGADORA, t.torneo().id(), equipo.id()))
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.YA_INSCRITO));

            TorneosService.TorneoCompleto gratis = torneoNuevo(0);
            Equipo otro = servicio.crearEquipo(JUGADORA, gratis.torneo().id(),
                    new TorneosService.SolicitudDeEquipo("Los Gratis", "avatar-1", JUGADOR2.id()));
            servicio.inscribir(JUGADORA, gratis.torneo().id(), otro.id());
            verify(libro, never()).reservar(any(), eq(0), anyString(), anyString());
        }

        @Test
        @DisplayName("sin creditos, sancionado o con el libro caido: rechazo con motivo y sin cobro (CA-02)")
        void rechazos() {
            TorneosService.TorneoCompleto t = torneoNuevo(30);
            Equipo equipo = servicio.crearEquipo(JUGADORA, t.torneo().id(),
                    new TorneosService.SolicitudDeEquipo("Los Valientes", "avatar-1", JUGADOR2.id()));
            when(libro.reservar(any(), anyInt(), anyString(), anyString()))
                    .thenThrow(new TorneoRechazado(Motivo.CREDITOS_INSUFICIENTES, "no alcanza"));
            assertThatThrownBy(() -> servicio.inscribir(JUGADORA, t.torneo().id(), equipo.id()))
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.CREDITOS_INSUFICIENTES));
            assertThat(servicio.obtener(t.torneo().id()).inscritos()).isZero();

            when(sanciones.sancionado(JUGADOR2.id())).thenReturn(true);
            assertThatThrownBy(() -> servicio.inscribir(JUGADORA, t.torneo().id(), equipo.id()))
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.INTEGRANTE_SANCIONADO));
        }

        @Test
        @DisplayName("el noveno equipo no cabe: CUPO_AGOTADO")
        void cupo() {
            TorneosService.TorneoCompleto t = torneoNuevo(0);
            for (int i = 0; i < 8; i++) {
                Actor capitan = Actor.usuario(UUID.randomUUID(), "JUGADOR");
                Equipo e = servicio.crearEquipo(capitan, t.torneo().id(),
                        new TorneosService.SolicitudDeEquipo("Equipo " + i, "avatar", UUID.randomUUID()));
                servicio.inscribir(capitan, t.torneo().id(), e.id());
            }
            Actor noveno = Actor.usuario(UUID.randomUUID(), "JUGADOR");
            Equipo e = servicio.crearEquipo(noveno, t.torneo().id(),
                    new TorneosService.SolicitudDeEquipo("Equipo 9", "avatar", UUID.randomUUID()));
            assertThatThrownBy(() -> servicio.inscribir(noveno, t.torneo().id(), e.id()))
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.CUPO_AGOTADO));
        }
    }

    @Nested
    @DisplayName("HU-TOR-004/005 · inicio, maquina y arbol · HU-ADM-005 · resultados")
    class ArbolYResultados {

        @Test
        @DisplayName("iniciar cobra las reservas, completa con la maquina hasta 8 y deja 1-4 listos; sin equipos no arranca")
        void iniciar() {
            TorneosService.TorneoCompleto vacio = torneoNuevo(0);
            assertThatThrownBy(() -> servicio.iniciar(ADMIN, vacio.torneo().id()))
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.SIN_EQUIPOS));

            TorneosService.TorneoCompleto t = torneoNuevo(10);
            UUID reserva = UUID.randomUUID();
            when(libro.reservar(any(), eq(10), anyString(), anyString())).thenReturn(reserva);
            Equipo equipo = servicio.crearEquipo(JUGADORA, t.torneo().id(),
                    new TorneosService.SolicitudDeEquipo("Los Valientes", "avatar-1", JUGADOR2.id()));
            servicio.inscribir(JUGADORA, t.torneo().id(), equipo.id());
            assertThatThrownBy(() -> servicio.iniciar(JUGADORA, t.torneo().id()))
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.PERMISO_INSUFICIENTE));

            TorneosService.TorneoCompleto enCurso = servicio.iniciar(ADMIN, t.torneo().id());
            verify(libro).consumir(reserva);
            assertThat(enCurso.torneo().estado()).isEqualTo(Torneo.Estado.EN_CURSO);
            assertThat(enCurso.equipos()).hasSize(8);
            assertThat(enCurso.equipos().stream().filter(Equipo::ia).count()).isEqualTo(7);
            assertThat(enCurso.equipos().stream().map(Equipo::posicion)).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8);
            assertThat(enCurso.encuentros()).hasSize(14);
            assertThat(enCurso.encuentros().stream().filter(Encuentro::listo).count()).isEqualTo(4);
            assertThat(enCurso.encuentros().get(0).equipoA()).isEqualTo(equipo.id());

            assertThatThrownBy(() -> servicio.crearEquipo(JUGADOR3, t.torneo().id(),
                    new TorneosService.SolicitudDeEquipo("Tarde", "avatar", UUID.randomUUID())))
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.ESTADO_NO_PERMITE));
            assertThatThrownBy(() -> servicio.iniciar(ADMIN, t.torneo().id()))
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.ESTADO_NO_PERMITE));
        }

        @Test
        @DisplayName("el resultado lo registra un servicio o un administrador con motivo; el arbol avanza hasta el campeon")
        void resultados() {
            TorneosService.TorneoCompleto t = torneoNuevo(0);
            Equipo equipo = servicio.crearEquipo(JUGADORA, t.torneo().id(),
                    new TorneosService.SolicitudDeEquipo("Los Valientes", "avatar-1", JUGADOR2.id()));
            servicio.inscribir(JUGADORA, t.torneo().id(), equipo.id());
            UUID id = t.torneo().id();
            TorneosService.TorneoCompleto enCurso = servicio.iniciar(ADMIN, id);
            List<Encuentro> arbol = enCurso.encuentros();

            assertThatThrownBy(() -> servicio.registrarResultado(JUGADORA, id, 1,
                    new TorneosService.SolicitudDeResultado(equipo.id(), null, null)))
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.PERMISO_INSUFICIENTE));
            assertThatThrownBy(() -> servicio.registrarResultado(ADMIN, id, 1,
                    new TorneosService.SolicitudDeResultado(equipo.id(), null, "")))
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.SOLICITUD_INVALIDA));
            assertThatThrownBy(() -> servicio.registrarResultado(SALAS, id, 5,
                    new TorneosService.SolicitudDeResultado(equipo.id(), null, null)))
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.ENCUENTRO_NO_LISTO));
            assertThatThrownBy(() -> servicio.registrarResultado(SALAS, id, 1,
                    new TorneosService.SolicitudDeResultado(arbol.get(1).equipoA(), null, null)))
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.GANADOR_NO_PARTICIPA));

            // 1.1.0: salas-partidas manda el uid del jugador en pie, no el equipo (que no conoce).
            assertThatThrownBy(() -> servicio.registrarResultado(SALAS, id, 1,
                    new TorneosService.SolicitudDeResultado(null, null, null, null)))
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.SOLICITUD_INVALIDA));
            assertThatThrownBy(() -> servicio.registrarResultado(SALAS, id, 1,
                    new TorneosService.SolicitudDeResultado(null, UUID.randomUUID(), null, null)))
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.GANADOR_NO_PARTICIPA));
            UUID partida = UUID.randomUUID();
            TorneosService.TorneoCompleto tras1 = servicio.registrarResultado(SALAS, id, 1,
                    new TorneosService.SolicitudDeResultado(null, JUGADOR2.id(), partida, null));
            Encuentro e1 = tras1.encuentros().get(0);
            assertThat(e1.estado()).isEqualTo(Encuentro.Estado.JUGADO);
            assertThat(e1.registradoPor()).isEqualTo("salas-partidas");
            assertThat(e1.partidaId()).isEqualTo(partida);
            assertThat(e1.ganador()).as("el uid del companero resuelve al equipo").isEqualTo(equipo.id());
            assertThat(tras1.encuentros().get(4).equipoA()).isEqualTo(equipo.id());

            // El administrador resuelve a mano el resto con motivo (incomparecencia): siempre gana el equipo A.
            TorneosService.TorneoCompleto actual = tras1;
            for (int numero = 2; numero <= 14; numero++) {
                Encuentro e = actual.encuentros().get(numero - 1);
                assertThat(e.listo()).as("encuentro %d listo", numero).isTrue();
                actual = servicio.registrarResultado(ADMIN, id, numero,
                        new TorneosService.SolicitudDeResultado(e.equipoA(), null, "Incomparecencia del rival"));
            }
            assertThat(actual.torneo().estado()).isEqualTo(Torneo.Estado.FINALIZADO);
            assertThat(actual.torneo().campeonEquipoId()).isEqualTo(equipo.id());
            assertThat(actual.encuentros().get(13).motivo()).isEqualTo("Incomparecencia del rival");
            assertThat(actual.encuentros().get(13).registradoPor()).isEqualTo(ADMIN.id().toString());
            assertThatThrownBy(() -> servicio.registrarResultado(SALAS, id, 14,
                    new TorneosService.SolicitudDeResultado(equipo.id(), null, null)))
                    .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(Motivo.ESTADO_NO_PERMITE));
        }
    }
}
