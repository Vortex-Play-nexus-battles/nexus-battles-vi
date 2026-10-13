package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.IngresoNoPermitido;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.NoEsElAnfitrion;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaNoEncontrada;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Arranque del combate — HU-SAL-004, RF-JUE-017.
 *
 * <p>Se prueba la coordinacion: quien puede, que se guarda, que se anuncia y en
 * que orden. Las reglas de si la sala puede empezar viven en {@code Sala} y se
 * prueban alli; aqui se comprueba que el caso de uso las respeta en vez de
 * duplicarlas.
 */
@DisplayName("IniciarPartida · arranque del combate (HU-SAL-004)")
class IniciarPartidaTest {

    private static final UUID ANFITRION = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID INVITADO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant AHORA = Instant.parse("2026-09-17T20:00:00Z");

    private final RepositorioDeSalasEnMemoria salas = new RepositorioDeSalasEnMemoria();
    private final RepositorioDePartidasEnMemoria partidas = new RepositorioDePartidasEnMemoria();
    private final CanalDePartidaEspia canal = new CanalDePartidaEspia();
    private final InventarioEnMemoria inventario = InventarioEnMemoria.conHeroe();

    /** Un identificador cualquiera, con el apodo que el inventario necesita. */
    private static JugadorAutenticado como(UUID id) {
        return new JugadorAutenticado(id, "jugador-" + id.toString().substring(0, 8));
    }

    private final IniciarPartida casoDeUso = new IniciarPartida(
            salas, partidas, canal, inventario, Clock.fixed(AHORA, ZoneOffset.UTC));

    private Sala salaConInvitado() {
        Sala sala = Sala.crear(
                new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, false, false, null), ANFITRION);
        sala.unirse(INVITADO);
        return salas.guardar(sala);
    }

    @Test
    @DisplayName("el anfitrion arranca el combate y la sala queda en juego")
    void arrancaYLaSalaQuedaEnJuego() {
        Sala sala = salaConInvitado();

        Partida partida = casoDeUso.ejecutar(sala.id(), como(ANFITRION));

        assertAll(
                () -> assertEquals(sala.id(), partida.idSala()),
                () -> assertEquals(AHORA, partida.iniciadaEn()),
                () -> assertEquals(EstadoSala.EN_JUEGO,
                        salas.buscarPorId(sala.id()).orElseThrow().estado()));
    }

    @Test
    @DisplayName("la partida queda guardada antes de anunciarse")
    void guardaAntesDeAnunciar() {
        Sala sala = salaConInvitado();

        Partida partida = casoDeUso.ejecutar(sala.id(), como(ANFITRION));

        assertAll(
                () -> assertTrue(partidas.buscarPorId(partida.id()).isPresent()),
                () -> assertEquals(1, canal.anuncios.size()),
                () -> assertEquals("inicio", canal.anuncios.get(0).tipo()),
                () -> assertEquals(partida.id(), canal.anuncios.get(0).partida().id()));
    }

    @Test
    @DisplayName("pulsar dos veces devuelve la misma partida, no un error ni una segunda")
    void esIdempotente() {
        Sala sala = salaConInvitado();

        Partida primera = casoDeUso.ejecutar(sala.id(), como(ANFITRION));
        Partida segunda = casoDeUso.ejecutar(sala.id(), como(ANFITRION));

        assertAll(
                () -> assertEquals(primera.id(), segunda.id()),
                // Y no se vuelve a anunciar: el canal ya lo dijo una vez.
                () -> assertEquals(1, canal.anuncios.size()));
    }

    @Test
    @DisplayName("un invitado no puede iniciar la partida de otro")
    void soloElAnfitrion() {
        Sala sala = salaConInvitado();

        assertThrows(NoEsElAnfitrion.class, () -> casoDeUso.ejecutar(sala.id(), como(INVITADO)));
    }

    @Test
    @DisplayName("una sala sin rival ni heroe de la IA no arranca")
    void sinRivalNoArranca() {
        Sala solo = salas.guardar(Sala.crear(
                new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, false, false, null), ANFITRION));

        assertThrows(IngresoNoPermitido.class, () -> casoDeUso.ejecutar(solo.id(), como(ANFITRION)));
    }

    @Test
    @DisplayName("una sala con heroe de la IA arranca aunque este sola")
    void conIaArrancaSola() {
        Sala conIa = salas.guardar(Sala.crear(
                new ParametrosDeSala(2, Modalidad.CONTRA_IA, 0, true, false, null), ANFITRION));

        Partida partida = casoDeUso.ejecutar(conIa.id(), como(ANFITRION));

        assertEquals(2, partida.participantes().size());
    }

    @Test
    @DisplayName("una sala que no existe es 404, y no se anuncia nada")
    void salaInexistente() {
        assertThrows(SalaNoEncontrada.class,
                () -> casoDeUso.ejecutar(UUID.randomUUID(), como(ANFITRION)));
        assertTrue(canal.anuncios.isEmpty());
    }

    @Test
    @DisplayName("si la sala rechaza empezar, no se guarda ninguna partida")
    void sinEfectosCuandoRechaza() {
        Sala solo = salas.guardar(Sala.crear(
                new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, false, false, null), ANFITRION));

        assertThrows(IngresoNoPermitido.class, () -> casoDeUso.ejecutar(solo.id(), como(ANFITRION)));
        assertTrue(partidas.buscarPorSala(solo.id()).isEmpty());
    }

    // =====================================================================
    // B7: orden sorteado, heroe de la maquina, combate preparado por el motor
    // =====================================================================

    private final MotorDeCombateSimulado motor = new MotorDeCombateSimulado();
    private final java.util.List<UUID> despuesDeEmpezar = new java.util.ArrayList<>();

    private IniciarPartida conSemilla(long semilla, HeroesDeLaMaquina maquina, Integer segundosPorTurno) {
        return new IniciarPartida(salas, partidas, canal, inventario, Clock.fixed(AHORA, ZoneOffset.UTC),
                motor, maquina, () -> semilla, () -> segundosPorTurno, despuesDeEmpezar::add);
    }

    private static com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate heroe(String id) {
        return new com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate(id, "Heroe " + id,
                "Guerrero Tanque", null, 1, 44, 44, 11);
    }

    private Sala salaDeSeis() {
        Sala sala = Sala.crear(new ParametrosDeSala(6, Modalidad.HASTA_SEIS, 0, false, false, null), ANFITRION,
                new com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante("Ana", heroe("h-ana")));
        for (int i = 2; i <= 6; i++) {
            UUID otro = UUID.fromString("0000000" + i + "-0000-0000-0000-000000000000");
            sala.unirse(otro, new com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante(
                    "J" + i, heroe("h-" + i)), null);
        }
        return salas.guardar(sala);
    }

    private static java.util.List<UUID> orden(Partida partida) {
        return partida.participantes().stream()
                .map(com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida::idJugador).toList();
    }

    @Test
    @DisplayName("§6.1.3: el orden de los turnos se sortea con una semilla que la partida guarda")
    void elOrdenSeSortea() {
        Sala sala = salaDeSeis();

        Partida partida = conSemilla(42L, null, null).ejecutar(sala.id(), como(ANFITRION));

        java.util.List<UUID> esperado = com.nexusbattles.plataforma.salaspartidas.dominio.OrdenDeTurnos
                .sorteado(42L).aplicar(orden(Partida.iniciar(sala, AHORA)));
        assertAll(
                () -> assertEquals(42L, partida.semillaDelOrden()),
                () -> assertEquals(esperado, orden(partida), "con la misma semilla, el mismo orden: auditable"),
                () -> assertEquals(orden(partida).get(0), partida.turnoActual().idJugador(),
                        "abre el primero del sorteo"),
                () -> assertEquals(6, new java.util.HashSet<>(orden(partida)).size(), "todos, una vez"));
    }

    @Test
    @DisplayName("§6.1.3: el sorteo reparte el primer turno: en muchas partidas abren jugadores distintos")
    void elPrimerTurnoVaria() {
        java.util.Set<UUID> abrieron = new java.util.HashSet<>();
        for (long semilla = 0; semilla < 60; semilla++) {
            Sala sala = salaDeSeis();
            abrieron.add(conSemilla(semilla, null, null).ejecutar(sala.id(), como(ANFITRION))
                    .turnoActual().idJugador());
        }
        assertEquals(6, abrieron.size(), "con 60 sorteos, los seis abrieron alguna vez");
    }

    @Test
    @DisplayName("el motor prepara el combate: todos a vida completa, con su poder, antes de anunciar")
    void elMotorPreparaElCombate() {
        Sala sala = salaDeSeis();

        Partida partida = conSemilla(1L, null, null).ejecutar(sala.id(), como(ANFITRION));

        assertAll(
                () -> assertEquals(java.util.List.of(partida.turnoActual().idJugador() + " a vida completa"),
                        motor.turnos),
                () -> assertTrue(partida.participantes().stream().allMatch(p -> p.combate() != null),
                        "cada uno con su estado de combate"),
                () -> assertEquals(java.util.List.of("inicio"), canal.tipos()),
                () -> assertEquals(java.util.List.of(partida.id()), despuesDeEmpezar,
                        "y despues el gancho, por si abre la maquina"));
    }

    @Test
    @DisplayName("si abre la maquina, juega antes de anunciar el inicio; el aviso y la respuesta traen la partida tras su turno")
    void laMaquinaAbreAntesDelAviso() {
        Sala sala = salaDeSeis();
        java.util.List<Integer> anunciosAlJugarLaMaquina = new java.util.ArrayList<>();
        // El gancho hace lo que hace EjecutarAccion cuando abre la maquina: lee
        // la partida guardada, juega (aqui, pasa el turno) y la vuelve a guardar.
        IniciarPartida abreLaMaquina = new IniciarPartida(salas, partidas, canal, inventario,
                Clock.fixed(AHORA, ZoneOffset.UTC), motor, null, () -> 1L, () -> null, idPartida -> {
                    anunciosAlJugarLaMaquina.add(canal.anuncios.size());
                    Partida enCurso = partidas.buscarPorId(idPartida).orElseThrow();
                    enCurso.avanzarTurno();
                    partidas.guardar(enCurso);
                });

        Partida devuelta = abreLaMaquina.ejecutar(sala.id(), como(ANFITRION));

        Partida guardada = partidas.buscarPorId(devuelta.id()).orElseThrow();
        Partida anunciada = canal.anuncios.get(canal.anuncios.size() - 1).partida();
        assertAll(
                // Quien espera se suscribe al tema de la partida DESPUES del
                // aviso de inicio: lo que la maquina anunciara antes no le
                // llegaria. Por eso juega antes de que haya ningun aviso.
                () -> assertEquals(java.util.List.of(0), anunciosAlJugarLaMaquina,
                        "la maquina juega antes de que se anuncie nada"),
                () -> assertEquals(java.util.List.of("inicio"), canal.tipos()),
                () -> assertEquals(guardada.turnoActual(), devuelta.turnoActual(),
                        "la respuesta trae el turno de despues de la maquina, no el 1"),
                () -> assertEquals(guardada.version(), devuelta.version()),
                () -> assertEquals(guardada.turnoActual(), anunciada.turnoActual(),
                        "el aviso de inicio tambien"));
    }

    @Test
    @DisplayName("si el motor no responde al empezar, la partida empieza igual, sin estado de combate")
    void sinMotorEmpiezaIgual() {
        Sala sala = salaDeSeis();
        motor.falloAlEmpezarTurno = new com.nexusbattles.plataforma.salaspartidas.dominio.MotorNoDisponible("apagado");

        Partida partida = conSemilla(1L, null, null).ejecutar(sala.id(), como(ANFITRION));

        assertAll(
                () -> assertTrue(partidas.buscarPorId(partida.id()).isPresent()),
                () -> assertTrue(partida.participantes().stream().allMatch(p -> p.combate() == null)));
    }

    @Test
    @DisplayName("D-B7-11: la maquina recibe un heroe del catalogo en el nivel del anfitrion")
    void laMaquinaConHeroeDelCatalogo() {
        Sala conIa = salas.guardar(Sala.crear(
                new ParametrosDeSala(2, Modalidad.CONTRA_IA, 0, true, false, null), ANFITRION,
                new com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante("Ana", heroe("h-ana"))));
        java.util.List<Integer> niveles = new java.util.ArrayList<>();
        HeroesDeLaMaquina catalogo = (cuantos, nivel) -> {
            niveles.add(nivel);
            return java.util.List.of(heroe("ia-mago"));
        };

        Partida partida = conSemilla(3L, catalogo, null).ejecutar(conIa.id(), como(ANFITRION));

        assertAll(
                () -> assertEquals(java.util.List.of(1), niveles),
                () -> assertTrue(partida.participantes().stream()
                        .anyMatch(p -> p.esIA() && "ia-mago".equals(p.heroe().id()))));
    }

    @Test
    @DisplayName("si el catalogo falla al sortear el heroe de la maquina, combate con un rival de su prototipo, no con una copia")
    void catalogoCaidoRivalDelMismoPrototipo() {
        Sala conIa = salas.guardar(Sala.crear(
                new ParametrosDeSala(2, Modalidad.CONTRA_IA, 0, true, false, null), ANFITRION,
                new com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante("Ana", heroe("h-ana"))));
        HeroesDeLaMaquina caido = (cuantos, nivel) -> {
            throw new IllegalStateException("catalogo apagado");
        };

        Partida partida = conSemilla(3L, caido, null).ejecutar(conIa.id(), como(ANFITRION));

        // Auditoría del 4-oct: antes era una copia exacta del heroe de Ana
        // (mismo id y nombre, su equipo): el registro decia «Heroe h-ana golpea
        // a Heroe h-ana (tu)». Ahora es un rival propio del mismo prototipo.
        var deLaMaquina = partida.participantes().stream().filter(p -> p.esIA()).findFirst().orElseThrow().heroe();
        assertAll(
                () -> assertNotEquals("h-ana", deLaMaquina.id()),
                () -> assertEquals("Guerrero Tanque", deLaMaquina.nombre()),
                () -> assertEquals("Guerrero Tanque", deLaMaquina.prototipo()),
                () -> assertEquals(1, deLaMaquina.nivelDeCombate()),
                () -> assertEquals(44, deLaMaquina.vidaMaxima()));
    }

    @Test
    @DisplayName("D-B7-14: con tiempo por turno, el primero vence a esa distancia; sin valor, nunca")
    void tiempoDelPrimerTurno() {
        Partida conLimite = conSemilla(1L, null, 20).ejecutar(salaDeSeis().id(), como(ANFITRION));
        Partida sinLimite = conSemilla(1L, null, null).ejecutar(salaDeSeis().id(), como(ANFITRION));

        assertAll(
                () -> assertEquals(AHORA.plusSeconds(20), conLimite.turnoVenceEn()),
                () -> assertEquals(null, sinLimite.turnoVenceEn()));
    }
}
