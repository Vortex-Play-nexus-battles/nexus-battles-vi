package com.nexusbattles.plataforma.salaspartidas.persistencia;

import com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.RecompensaDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeRecompensas;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDePartidas;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
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
 * La memoria de la recompensa por jugar contra una PostgreSQL de verdad — HU-JUE-012, CA-05.
 *
 * <p>Con {@code ddl-auto=validate}: si {@code RecompensaEntidad} y la
 * migracion V11 dejan de coincidir, falla aqui y no en produccion.
 */
@Testcontainers
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Import({RepositorioRecompensasJpa.class, RepositorioSalasJpa.class, RepositorioPartidasJpa.class})
class RepositorioRecompensasJpaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRUNO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant T0 = Instant.parse("2026-09-21T10:00:00Z");

    @Autowired
    private RepositorioDeRecompensas recompensas;

    @Autowired
    private RepositorioDeSalas salas;

    @Autowired
    private RepositorioDePartidas partidas;

    /** Una partida real en la base, porque la recompensa la referencia por clave foranea. */
    private Partida partidaGuardada() {
        Sala sala = Sala.crear(new ParametrosDeSala(6, Modalidad.HASTA_SEIS, 100, false, false, null), ANA,
                new FichaDeParticipante("Ana", new HeroeDeCombate("h-a", "Arquero", null, 5, 100, 100)));
        sala.unirse(BRUNO, new FichaDeParticipante("Bruno", new HeroeDeCombate("h-b", "Centinela", null, 5, 100, 100)), null);
        sala.iniciarPartida(ANA);
        salas.guardar(sala);
        return partidas.guardar(Partida.iniciar(sala, T0));
    }

    @Test
    @DisplayName("una recompensa pendiente se guarda con su motivo y se recupera igual")
    void guardaYRecupera() {
        Partida partida = partidaGuardada();
        RecompensaDePartida recompensa = RecompensaDePartida.nueva(partida.id(), partida.idSala(), T0);
        recompensa.fallo("el libro no respondio", T0.plusSeconds(1));

        recompensas.guardar(recompensa);
        RecompensaDePartida recuperada = recompensas.buscarPorPartida(partida.id()).orElseThrow();

        assertAll(
                () -> assertEquals(partida.id(), recuperada.idPartida()),
                () -> assertEquals(partida.idSala(), recuperada.idSala()),
                () -> assertEquals(RecompensaDePartida.Estado.PENDIENTE, recuperada.estado()),
                () -> assertEquals(1, recuperada.intentos()),
                () -> assertEquals("el libro no respondio", recuperada.ultimoError()),
                () -> assertEquals(T0, recuperada.creadaEn()),
                () -> assertEquals(T0.plusSeconds(1), recuperada.actualizadaEn()));
    }

    @Test
    @DisplayName("cerrarla actualiza la misma fila: una recompensa por partida")
    void cerrarActualizaLaFila() {
        Partida partida = partidaGuardada();
        RecompensaDePartida recompensa = RecompensaDePartida.nueva(partida.id(), partida.idSala(), T0);
        recompensa.fallo("caido", T0);
        recompensas.guardar(recompensa);

        RecompensaDePartida releida = recompensas.buscarPorPartida(partida.id()).orElseThrow();
        releida.acreditada(T0.plusSeconds(60));
        recompensas.guardar(releida);

        RecompensaDePartida cerrada = recompensas.buscarPorPartida(partida.id()).orElseThrow();
        assertAll(
                () -> assertEquals(RecompensaDePartida.Estado.ACREDITADA, cerrada.estado()),
                () -> assertEquals(2, cerrada.intentos()),
                () -> assertNull(cerrada.ultimoError()),
                () -> assertTrue(recompensas.pendientes(10).isEmpty()));
    }

    @Test
    @DisplayName("las pendientes salen de la mas antigua a la mas nueva, sin las ya acreditadas, hasta el maximo")
    void pendientesEnOrden() {
        Partida vieja = partidaGuardada();
        Partida nueva = partidaGuardada();
        Partida cerrada = partidaGuardada();
        RecompensaDePartida v = RecompensaDePartida.nueva(vieja.id(), vieja.idSala(), T0.minusSeconds(3_600));
        v.fallo("x", T0);
        RecompensaDePartida n = RecompensaDePartida.nueva(nueva.id(), nueva.idSala(), T0);
        n.fallo("x", T0);
        RecompensaDePartida c = RecompensaDePartida.nueva(cerrada.id(), cerrada.idSala(), T0.minusSeconds(7_200));
        c.acreditada(T0);
        recompensas.guardar(n);
        recompensas.guardar(c);
        recompensas.guardar(v);

        List<RecompensaDePartida> todas = recompensas.pendientes(10);
        List<RecompensaDePartida> soloUna = recompensas.pendientes(1);

        assertAll(
                () -> assertEquals(List.of(vieja.id(), nueva.id()),
                        todas.stream().map(RecompensaDePartida::idPartida).toList()),
                () -> assertEquals(List.of(vieja.id()),
                        soloUna.stream().map(RecompensaDePartida::idPartida).toList()));
    }

    @Test
    @DisplayName("no se puede anotar una recompensa de una partida que no existe (fk_recompensas_partida)")
    void exigePartidaReal() {
        RecompensaDePartida huerfana = RecompensaDePartida.nueva(UUID.randomUUID(), UUID.randomUUID(), T0);

        assertThrows(DataIntegrityViolationException.class, () -> {
            recompensas.guardar(huerfana);
            recompensas.pendientes(1); // fuerza el flush dentro de la transaccion de prueba
        });
    }
}
