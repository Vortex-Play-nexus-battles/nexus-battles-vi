package com.nexusbattles.plataforma.salaspartidas.persistencia;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadisticasDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.OrdenDeTurnos;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.PartidaModificadaConcurrentemente;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.PerfilDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDePartidas;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Turno;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistencia del combate contra una PostgreSQL de verdad — RF-JUE-017.
 *
 * <p>Igual que {@link RepositorioSalasJpaIT}, corre con
 * {@code ddl-auto=validate}: asi Hibernate compara el mapeo de
 * {@code PartidaEntidad} contra lo que creo la migracion V6. Si la entidad y la
 * migracion dejan de coincidir, falla aqui y no en el despliegue.
 *
 * <p>Lo que de verdad se prueba es lo que solo se ve contra una base real: que
 * el <b>orden de los turnos</b> sobrevive al viaje (lo garantiza la columna
 * {@code orden}, no la tabla), que la restriccion unica sobre {@code id_sala}
 * impide dos partidas de la misma sala, y que un participante sin heroe se
 * guarda sin heroe en vez de con media ficha.
 */
@Testcontainers
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Import({RepositorioPartidasJpa.class, RepositorioSalasJpa.class})
class RepositorioPartidasJpaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final UUID ANFITRION = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ANA = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BRUNO = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant AHORA = Instant.parse("2026-09-17T20:00:00Z");

    @Autowired
    private RepositorioDePartidas partidas;

    @Autowired
    private RepositorioDeSalas salas;

    /**
     * Acceso directo solo para forzar el volcado a la base en la prueba de la
     * restriccion unica. Con la transaccion que abre {@code @DataJpaTest}, un
     * {@code save} sin {@code flush} no llega a PostgreSQL dentro de la prueba
     * y la violacion apareceria al deshacer, fuera del {@code assertThrows}.
     */
    @Autowired
    private PartidasSpringData almacen;

    /** Para vaciar la cache de primer nivel y leer de verdad de la base (B7). */
    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    /**
     * Sala ya guardada con tres dentro. Hace falta guardarla de verdad: la
     * clave foranea {@code fk_partidas_sala} rechaza una partida cuya sala no
     * exista, y esa es exactamente la garantia que se quiere.
     */
    private Sala salaGuardada(int recompensa, boolean conIA) {
        Sala sala = Sala.crear(
                new ParametrosDeSala(6, Modalidad.HASTA_SEIS, recompensa, conIA, false, null),
                ANFITRION);
        sala.unirse(ANA);
        sala.unirse(BRUNO);
        sala.iniciarPartida(ANFITRION);
        return salas.guardar(sala);
    }

    @Test
    @DisplayName("guarda una partida y la recupera igual que se guardo")
    void guardaYRecupera() {
        Partida partida = Partida.iniciar(salaGuardada(320, false), AHORA);

        partidas.guardar(partida);
        Partida recuperada = partidas.buscarPorId(partida.id()).orElseThrow();

        assertAll(
                () -> assertEquals(partida.id(), recuperada.id()),
                () -> assertEquals(partida.idSala(), recuperada.idSala()),
                () -> assertEquals(EstadoPartida.EN_CURSO, recuperada.estado()),
                () -> assertEquals(320, recuperada.recompensaEnJuego()),
                () -> assertEquals(3, recuperada.participantes().size()),
                () -> assertEquals(ANFITRION, recuperada.turnoActual().idJugador()),
                () -> assertEquals(1, recuperada.turnoActual().numeroTurno()));
    }

    @Test
    @DisplayName("el orden de los turnos sobrevive al viaje: el anfitrion sigue primero")
    void conservaElOrdenDeLosTurnos() {
        // Sin la columna `orden` esto pasaria por casualidad casi siempre y
        // fallaria en produccion el dia que PostgreSQL devolviera otra cosa:
        // una tabla no garantiza el orden de lectura.
        Partida partida = Partida.iniciar(salaGuardada(0, false), AHORA);

        partidas.guardar(partida);
        List<UUID> orden = partidas.buscarPorId(partida.id()).orElseThrow()
                .participantes().stream()
                .map(ParticipanteDePartida::idJugador)
                .toList();

        assertEquals(
                partida.participantes().stream().map(ParticipanteDePartida::idJugador).toList(),
                orden);
    }

    @Test
    @DisplayName("el participante de la IA vuelve marcado como IA y sin creditos apostados")
    void conservaAlParticipanteDeLaIa() {
        Partida partida = Partida.iniciar(salaGuardada(200, true), AHORA);

        partidas.guardar(partida);
        List<ParticipanteDePartida> dentro =
                partidas.buscarPorId(partida.id()).orElseThrow().participantes();

        assertAll(
                () -> assertEquals(4, dentro.size()),
                () -> assertTrue(dentro.get(3).esIA(), "el ultimo es la maquina"),
                () -> assertEquals(0, dentro.get(3).creditosApostados()),
                () -> assertEquals(200, dentro.get(0).creditosApostados()));
    }

    @Test
    @DisplayName("un participante sin heroe vuelve sin heroe, no con media ficha")
    void sinHeroeVuelveSinHeroe() {
        Partida partida = Partida.iniciar(salaGuardada(0, false), AHORA);

        partidas.guardar(partida);

        assertTrue(partidas.buscarPorId(partida.id()).orElseThrow()
                .participantes().stream().allMatch(p -> p.heroe() == null));
    }

    @Test
    @DisplayName("un heroe completo sobrevive al viaje con su vida")
    void conHeroeVuelveElHeroe() {
        Sala sala = salaGuardada(0, false);
        HeroeDeCombate heroe = new HeroeDeCombate(
                "h-1", "Sombra de Vael", "https://cdn.local/h-1.png", 7, 140, 140);
        Partida partida = Partida.rehidratar(UUID.randomUUID(), sala.id(), EstadoPartida.EN_CURSO,
                List.of(new ParticipanteDePartida(ANFITRION, heroe, false, 1, 50),
                        ParticipanteDePartida.humano(ANA, 50)),
                Turno.primero(ANFITRION), 50, AHORA);

        partidas.guardar(partida);
        List<ParticipanteDePartida> dentro =
                partidas.buscarPorId(partida.id()).orElseThrow().participantes();

        assertAll(
                () -> assertEquals(heroe, dentro.get(0).heroe()),
                () -> assertEquals(1, dentro.get(0).equipo()),
                () -> assertNull(dentro.get(1).heroe()),
                () -> assertNull(dentro.get(1).equipo()));
    }

    @Test
    @DisplayName("el momento de inicio sobrevive al viaje de ida y vuelta")
    void conservaElMomentoDeInicio() {
        Partida partida = Partida.iniciar(salaGuardada(0, false), AHORA);

        partidas.guardar(partida);

        // TIMESTAMPTZ guarda microsegundos y el Instant de Java nanosegundos:
        // comparar al segundo evita un rojo que no dice nada.
        assertEquals(AHORA.getEpochSecond(),
                partidas.buscarPorId(partida.id()).orElseThrow().iniciadaEn().getEpochSecond());
    }

    @Test
    @DisplayName("buscar por sala encuentra la partida que salio de ella")
    void buscaPorSala() {
        Partida partida = Partida.iniciar(salaGuardada(0, false), AHORA);

        partidas.guardar(partida);

        assertEquals(partida.id(), partidas.buscarPorSala(partida.idSala()).orElseThrow().id());
    }

    @Test
    @DisplayName("una sala sin partida no devuelve ninguna, y una partida que no existe tampoco")
    void loQueNoExisteNoAparece() {
        assertAll(
                () -> assertTrue(partidas.buscarPorSala(UUID.randomUUID()).isEmpty()),
                () -> assertTrue(partidas.buscarPorId(UUID.randomUUID()).isEmpty()));
    }

    @Test
    @DisplayName("la base impide dos partidas para la misma sala, aunque el caso de uso falle")
    void unaSolaPartidaPorSala() {
        // La comprobacion de IniciarPartida evita el caso comun; esta
        // restriccion evita la carrera entre dos peticiones simultaneas del
        // mismo anfitrion, que la comprobacion sola no puede ver.
        Sala sala = salaGuardada(0, false);
        almacen.saveAndFlush(PartidaEntidad.desde(Partida.iniciar(sala, AHORA)));

        PartidaEntidad segunda = PartidaEntidad.desde(Partida.iniciar(sala, AHORA));

        assertThrows(DataIntegrityViolationException.class, () -> almacen.saveAndFlush(segunda));
    }

    @Test
    @DisplayName("el avance del turno se guarda: tras reiniciar le toca a quien le tocaba")
    void elTurnoGuardadoEsElQueVale() {
        Partida partida = Partida.iniciar(salaGuardada(0, false), AHORA);
        UUID segundoEnTurno = partida.participantes().get(1).idJugador();
        partidas.guardar(partida);

        Partida recuperada = partidas.buscarPorId(partida.id()).orElseThrow();
        recuperada.avanzarTurno();
        partidas.guardar(recuperada);

        Partida despues = partidas.buscarPorId(partida.id()).orElseThrow();
        assertAll(
                () -> assertEquals(segundoEnTurno, despues.turnoActual().idJugador()),
                () -> assertEquals(2, despues.turnoActual().numeroTurno()));
    }

    @Test
    @DisplayName("una partida terminada vuelve terminada")
    void conservaElFinal() {
        Partida partida = Partida.iniciar(salaGuardada(0, false), AHORA);
        partida.terminar();

        partidas.guardar(partida);

        assertEquals(EstadoPartida.FINALIZADA,
                partidas.buscarPorId(partida.id()).orElseThrow().estado());
    }

    // =========================================================================
    // B7 (V14): estado de combate, bloqueo optimista, historial y vencimientos
    // =========================================================================

    private static final EstadisticasDeCombate EN_NIVEL_4 = new EstadisticasDeCombate(40, 178, 45,
            new EstadisticasDeCombate.Formula(41, 1, 6), new EstadisticasDeCombate.Formula(0, 1, 4), null);

    /** Partida de dos con heroes reales guardada con su sala; Ana abre (orden de entrada). */
    private Partida conHeroesReales(Instant cuando) {
        HeroeDeCombate tanque = new HeroeDeCombate("h-1", "Muro", "Guerrero Tanque", null, 4, 178, 178, 45,
                new PerfilDeCombate(4, EN_NIVEL_4, List.of("Espada de una mano"), List.of("Golpe de defensa")));
        HeroeDeCombate mago = new HeroeDeCombate("h-2", "Escarcha", "Mago Hielo", null, 1, 40, 40, 10,
                PerfilDeCombate.delCatalogo(1));
        Sala sala = Sala.crear(new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, false, false, null),
                ANA, new FichaDeParticipante("Ana", tanque));
        sala.unirse(BRUNO, new FichaDeParticipante("Bruno", mago), null);
        sala.iniciarPartida(ANA);
        salas.guardar(sala);
        return Partida.iniciar(sala, cuando, OrdenDeTurnos.sorteado(20260927L), List.of());
    }

    @Test
    @DisplayName("B7: perfil, estado de combate, semilla, fin y vencimiento sobreviven al viaje")
    void elEstadoDeCombateSobreviveAlViaje() {
        Partida partida = conHeroesReales(AHORA);
        UUID primero = partida.turnoActual().idJugador();
        EstadoDeCombate estado = new EstadoDeCombate(6, 40, 3, java.util.Map.of("Golpe con escudo", 2),
                List.of(new EstadoDeCombate.Efecto("MANO_DE_PIEDRA", "Mano de piedra", "BONO_DEFENSA", 48, 1, true,
                        primero.toString())),
                new EstadoDeCombate.GolpeRecibido(BRUNO.toString(), 7), java.util.Map.of("Mano de piedra", 1),
                List.of(new EstadoDeCombate.AccionDisponible("Mano de piedra", "Mano de piedra", "DEFENSA", false, 4,
                        false, 1, 4, false, "En carga: 1 turno.")),
                EN_NIVEL_4);
        partida.aplicarCombate(primero, 150, 178, estado);
        partida.fijarVencimientoDelTurno(AHORA.plusSeconds(30));

        partidas.guardar(partida);
        // Sin la cache de primer nivel: lo que vuelve sale de las columnas de
        // PostgreSQL (V14), no de la entidad que se acaba de escribir.
        entityManager.clear();
        Partida recuperada = partidas.buscarPorId(partida.id()).orElseThrow();
        ParticipanteDePartida enCombate = recuperada.participante(primero).orElseThrow();
        ParticipanteDePartida ana = recuperada.participante(ANA).orElseThrow();

        assertAll(
                () -> assertEquals(estado, enCombate.combate(), "el estado del motor vuelve tal cual"),
                () -> assertEquals(150, enCombate.heroe().vidaActual()),
                () -> assertEquals(4, ana.heroe().perfil().nivel()),
                () -> assertEquals(List.of("Espada de una mano"), ana.heroe().perfil().equipamiento()),
                () -> assertEquals(List.of("Golpe de defensa"), ana.heroe().perfil().epicas()),
                () -> assertEquals(EN_NIVEL_4, ana.heroe().perfil().estadisticas()),
                () -> assertEquals(20260927L, recuperada.semillaDelOrden()),
                () -> assertEquals(AHORA.plusSeconds(30).getEpochSecond(), recuperada.turnoVenceEn().getEpochSecond()),
                () -> assertNull(recuperada.finalizadaEn()));

        recuperada.terminar(AHORA.plusSeconds(90));
        partidas.guardar(recuperada);
        Partida terminada = partidas.buscarPorId(partida.id()).orElseThrow();
        assertAll(
                () -> assertEquals(AHORA.plusSeconds(90).getEpochSecond(), terminada.finalizadaEn().getEpochSecond()),
                () -> assertNull(terminada.turnoVenceEn()));
    }

    @Test
    @DisplayName("B7: cada escritura avanza la version, y una sobre una lectura vieja sale partida-modificada sin pisar nada")
    void bloqueoOptimista() {
        Partida partida = conHeroesReales(AHORA);
        Partida guardada = partidas.guardar(partida);
        Partida lecturaUno = partidas.buscarPorId(partida.id()).orElseThrow();
        Partida lecturaDos = partidas.buscarPorId(partida.id()).orElseThrow();

        lecturaUno.avanzarTurno();
        Partida trasLaPrimera = partidas.guardar(lecturaUno);
        lecturaDos.avanzarTurno();

        assertAll(
                () -> assertEquals(guardada.version(), lecturaDos.version()),
                () -> assertEquals(guardada.version() + 1, trasLaPrimera.version(),
                        "guardar devuelve la marca nueva: es la que hay que usar para seguir"),
                () -> assertThrows(PartidaModificadaConcurrentemente.class, () -> partidas.guardar(lecturaDos)));
        Partida enBase = partidas.buscarPorId(partida.id()).orElseThrow();
        assertAll(
                () -> assertEquals(2, enBase.turnoActual().numeroTurno(), "un solo avance de turno"),
                () -> assertEquals(trasLaPrimera.version(), enBase.version()));
    }

    @Test
    @DisplayName("B7: el historial de un jugador, de la mas reciente a la mas antigua y paginado")
    void historialDelJugador() {
        Partida antigua = partidas.guardar(conHeroesReales(AHORA));
        Partida reciente = partidas.guardar(conHeroesReales(AHORA.plusSeconds(3600)));
        partidas.guardar(Partida.iniciar(salaGuardada(0, false), AHORA.plusSeconds(7200)));

        var deBruno = partidas.buscarPorJugador(BRUNO, 0, 16);
        var primeraDeUna = partidas.buscarPorJugador(BRUNO, 0, 1);
        var segundaDeUna = partidas.buscarPorJugador(BRUNO, 1, 1);

        assertAll(
                // Bruno esta en las dos partidas con heroes y tambien en la de
                // salaGuardada (entra como invitado): tres en total.
                () -> assertEquals(3, deBruno.totalElementos()),
                () -> assertEquals(reciente.id(), deBruno.contenido().get(1).id()),
                () -> assertEquals(antigua.id(), deBruno.contenido().get(2).id()),
                () -> assertEquals(3, primeraDeUna.totalPaginas()),
                () -> assertEquals(1, primeraDeUna.contenido().size()),
                () -> assertEquals(reciente.id(), segundaDeUna.contenido().get(0).id()),
                () -> assertEquals(0, partidas.buscarPorJugador(UUID.randomUUID(), 0, 16).totalElementos()));
    }

    @Test
    @DisplayName("B7: solo las partidas EN CURSO con el turno ya agotado se pasan por vencimiento")
    void turnosVencidos() {
        Partida vencida = conHeroesReales(AHORA);
        vencida.fijarVencimientoDelTurno(AHORA.minusSeconds(1));
        partidas.guardar(vencida);
        Partida porVencer = conHeroesReales(AHORA);
        porVencer.fijarVencimientoDelTurno(AHORA.plusSeconds(60));
        partidas.guardar(porVencer);
        Partida sinLimite = conHeroesReales(AHORA);
        partidas.guardar(sinLimite);
        Partida terminada = conHeroesReales(AHORA);
        terminada.fijarVencimientoDelTurno(AHORA.minusSeconds(1));
        terminada.terminar(AHORA);
        partidas.guardar(terminada);

        List<UUID> vencidas = partidas.conTurnoVencido(AHORA).stream().map(Partida::id).toList();

        assertEquals(List.of(vencida.id()), vencidas);
    }
}
