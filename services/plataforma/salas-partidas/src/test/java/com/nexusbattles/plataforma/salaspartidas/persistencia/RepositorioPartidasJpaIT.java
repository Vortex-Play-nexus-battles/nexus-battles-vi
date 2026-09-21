package com.nexusbattles.plataforma.salaspartidas.persistencia;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida;
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
}
