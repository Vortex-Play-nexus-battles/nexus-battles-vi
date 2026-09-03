package com.nexusbattles.plataforma.salaspartidas.persistencia;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Adaptador de persistencia contra una PostgreSQL de verdad — HU-SAL-001.
 *
 * <p>Se ejecuta con {@code ddl-auto=validate} a proposito: asi Hibernate compara
 * el mapeo de {@code SalaEntidad} contra las columnas que creo la migracion de
 * Flyway. Si la migracion y la entidad dejan de coincidir, esta prueba falla
 * antes que la aplicacion en produccion.
 *
 * <p><b>Sin {@code disabledWithoutDocker} a proposito.</b> heroes e inventario lo
 * usan, y por eso sus pruebas de integracion llevan tiempo saltandose sin que
 * nadie se entere: el build sale verde igual. Aqui se prefiere que falle. Una
 * prueba de integracion omitida no es una prueba que pasa, es una que no se
 * ejecuto, y la unica forma de notarlo es que duela.
 */
@Testcontainers
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Import(RepositorioSalasJpa.class)
class RepositorioSalasJpaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final UUID ANFITRION = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private RepositorioDeSalas repositorio;

    @Test
    @DisplayName("guarda una sala y la recupera igual que se guardo")
    void guardaYRecupera() {
        Sala sala = Sala.crear(new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 400, true, false, 2), ANFITRION);

        repositorio.guardar(sala);
        Sala recuperada = repositorio.buscarPorId(sala.id()).orElseThrow();

        assertAll(
                () -> assertEquals(sala.id(), recuperada.id()),
                () -> assertEquals(EstadoSala.ABIERTA, recuperada.estado()),
                () -> assertEquals(Modalidad.HASTA_SEIS, recuperada.modalidad()),
                () -> assertEquals(4, recuperada.maximoParticipantes()),
                () -> assertEquals(400, recuperada.recompensaCreditos()),
                () -> assertTrue(recuperada.incluirHeroeIA()),
                () -> assertEquals(2, recuperada.tamanoEquipo()),
                () -> assertEquals(ANFITRION, recuperada.idAnfitrion()),
                () -> assertEquals(1, recuperada.ocupacion()));
    }

    @Test
    @DisplayName("una sala privada se recupera privada")
    void salaPrivada() {
        Sala sala = Sala.crear(new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, false, true, null), ANFITRION);

        repositorio.guardar(sala);

        assertAll(
                () -> assertEquals(EstadoSala.PRIVADA,
                        repositorio.buscarPorId(sala.id()).orElseThrow().estado()),
                () -> assertTrue(repositorio.buscarPorId(sala.id()).orElseThrow().privada()));
    }

    @Test
    @DisplayName("el tamano de equipo nulo se guarda y vuelve nulo")
    void sinEquipo() {
        Sala sala = Sala.crear(new ParametrosDeSala(2, Modalidad.CONTRA_IA, 0, false, false, null), ANFITRION);

        repositorio.guardar(sala);

        assertNull(repositorio.buscarPorId(sala.id()).orElseThrow().tamanoEquipo());
    }

    @Test
    @DisplayName("el momento de creacion sobrevive al viaje de ida y vuelta")
    void conservaLaFecha() {
        Sala sala = Sala.crear(new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, false, false, null), ANFITRION);

        repositorio.guardar(sala);
        Sala recuperada = repositorio.buscarPorId(sala.id()).orElseThrow();

        // PostgreSQL guarda TIMESTAMPTZ con precision de microsegundos; el Instant
        // de Java tiene nanosegundos. Comparar al segundo evita un falso rojo.
        assertEquals(sala.creadaEn().getEpochSecond(), recuperada.creadaEn().getEpochSecond());
    }

    @Test
    @DisplayName("una sala que no existe no se encuentra")
    void salaInexistente() {
        assertTrue(repositorio.buscarPorId(UUID.randomUUID()).isEmpty());
    }
}
