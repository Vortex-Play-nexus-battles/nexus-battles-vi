package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.persistencia;

import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.RepositorioDeBloqueos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los bloqueos de los mensajes privados contra una PostgreSQL de verdad —
 * V15, D-40 (auditoria de DEV del 30-sep).
 *
 * <p>Con {@code ddl-auto=validate} y sin la transaccion por prueba, como
 * {@link RepositorioMensajesDirectosJpaIT}: bloquear dos veces tiene que
 * chocar con la clave primaria y darse por bueno, igual que en el servicio.
 */
@Testcontainers
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(RepositorioBloqueosJpa.class)
@DisplayName("RepositorioBloqueosJpa · la tabla bloqueos_mensajes_directos (D-40)")
class RepositorioBloqueosJpaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRUNO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CARLA = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant T0 = Instant.parse("2026-10-01T15:00:00Z");

    @Autowired
    private RepositorioDeBloqueos repositorio;

    @Autowired
    private BloqueosSpringData almacen;

    @BeforeEach
    void vaciar() {
        almacen.deleteAll();
    }

    @Test
    @DisplayName("bloquear guarda la fila una sola vez: el segundo intento la da por buena")
    void bloquearEsIdempotente() {
        boolean primero = repositorio.bloquear(ANA, BRUNO, T0);
        boolean segundo = repositorio.bloquear(ANA, BRUNO, T0.plusSeconds(60));

        assertAll(
                () -> assertTrue(primero),
                () -> assertFalse(segundo),
                () -> assertEquals(1, almacen.count()),
                () -> assertEquals(T0, almacen.findById(new BloqueoEntidad.Clave(ANA, BRUNO)).orElseThrow()
                        .bloqueadoEn(), "el segundo intento no cambia la fecha del primero"),
                () -> assertTrue(repositorio.bloqueo(ANA, BRUNO)),
                () -> assertFalse(repositorio.bloqueo(BRUNO, ANA), "un bloqueo tiene sentido: quien a quien"));
    }

    @Test
    @DisplayName("desbloquear quita la fila; repetirlo no falla")
    void desbloquearEsIdempotente() {
        repositorio.bloquear(ANA, BRUNO, T0);

        assertAll(
                () -> assertTrue(repositorio.desbloquear(ANA, BRUNO)),
                () -> assertFalse(repositorio.desbloquear(ANA, BRUNO)),
                () -> assertFalse(repositorio.bloqueo(ANA, BRUNO)),
                () -> assertEquals(0, almacen.count()));
    }

    @Test
    @DisplayName("a quienes bloqueo un jugador y quienes lo bloquearon a el")
    void conjuntos() {
        repositorio.bloquear(ANA, BRUNO, T0);
        repositorio.bloquear(ANA, CARLA, T0);
        repositorio.bloquear(CARLA, ANA, T0);
        repositorio.bloquear(BRUNO, CARLA, T0);

        assertAll(
                () -> assertEquals(Set.of(BRUNO, CARLA), repositorio.bloqueadosPor(ANA)),
                () -> assertEquals(Set.of(CARLA), repositorio.quienesBloquearonA(ANA)),
                () -> assertEquals(Set.of(ANA, BRUNO), repositorio.quienesBloquearonA(CARLA)),
                () -> assertEquals(Set.of(), repositorio.bloqueadosPor(UUID.randomUUID())));
    }

    @Test
    @DisplayName("la base de datos tampoco deja que alguien se bloquee a si mismo")
    void noASiMismo() {
        assertThrows(DataIntegrityViolationException.class,
                () -> almacen.saveAndFlush(new BloqueoEntidad(ANA, ANA, T0)));
    }
}
