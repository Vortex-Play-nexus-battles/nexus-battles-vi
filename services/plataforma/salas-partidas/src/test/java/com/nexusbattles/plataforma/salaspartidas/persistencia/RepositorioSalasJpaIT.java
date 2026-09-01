package com.nexusbattles.plataforma.salaspartidas.persistencia;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.IngresoNoPermitido;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    // =========================================================================
    // HU-SAL-002 · RF-JUE-002 — los participantes tienen que sobrevivir al viaje
    //
    // Guardar solo el numero de ocupantes deja el aforo cuadrado pero pierde
    // QUIEN esta dentro. En cuanto una sala se rehidrata desde PostgreSQL, las
    // reglas de ingreso dejan de poder aplicarse: no hay contra que comparar.
    // Estas cinco pruebas fijan las cuatro invariantes que se rompen.
    // =========================================================================

    private static final UUID ANA = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID BRUNO = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    /** Sala de cuatro cupos con el anfitrion y los dos jugadores dentro. */
    private Sala salaConTresDentro() {
        Sala sala = Sala.crear(
                new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, false, false, null), ANFITRION);
        sala.unirse(ANA);
        sala.unirse(BRUNO);
        return sala;
    }

    @Test
    @DisplayName("una sala con varios participantes conserva sus identidades al rehidratarse")
    void conservaLosParticipantes() {
        Sala sala = salaConTresDentro();

        repositorio.guardar(sala);
        Sala recuperada = repositorio.buscarPorId(sala.id()).orElseThrow();

        assertAll(
                () -> assertEquals(3, recuperada.ocupacion()),
                () -> assertEquals(3, recuperada.participantes().size()),
                () -> assertTrue(recuperada.participantes().contains(ANFITRION), "el anfitrion"),
                () -> assertTrue(recuperada.participantes().contains(ANA), "Ana"),
                () -> assertTrue(recuperada.participantes().contains(BRUNO), "Bruno"));
    }

    @Test
    @DisplayName("tras rehidratar sigue detectando a quien ya esta dentro")
    void detectaDuplicadosTrasRehidratar() {
        Sala sala = salaConTresDentro();
        repositorio.guardar(sala);

        Sala recuperada = repositorio.buscarPorId(sala.id()).orElseThrow();

        assertThrows(IngresoNoPermitido.class, () -> recuperada.unirse(ANA));
    }

    @Test
    @DisplayName("tras rehidratar el anfitrion sigue contando como participante")
    void elAnfitrionSobreviveAlViaje() {
        Sala sala = Sala.crear(
                new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, false, false, null), ANFITRION);
        repositorio.guardar(sala);

        Sala recuperada = repositorio.buscarPorId(sala.id()).orElseThrow();

        assertThrows(IngresoNoPermitido.class, () -> recuperada.unirse(ANFITRION));
    }

    @Test
    @DisplayName("tras rehidratar el ultimo cupo sigue llevando la sala a LLENA")
    void elAforoSigueCuadrandoTrasRehidratar() {
        Sala sala = Sala.crear(
                new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, false, false, null), ANFITRION);
        repositorio.guardar(sala);

        Sala recuperada = repositorio.buscarPorId(sala.id()).orElseThrow();
        recuperada.unirse(ANA);

        assertAll(
                () -> assertEquals(2, recuperada.ocupacion()),
                () -> assertEquals(EstadoSala.LLENA, recuperada.estado()),
                () -> assertTrue(recuperada.participantes().contains(ANA)));
    }

    @Test
    @DisplayName("una sala guardada llena sigue rechazando tras rehidratarse")
    void salaLlenaSigueRechazando() {
        Sala sala = Sala.crear(
                new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, false, false, null), ANFITRION);
        sala.unirse(ANA);
        repositorio.guardar(sala);

        Sala recuperada = repositorio.buscarPorId(sala.id()).orElseThrow();

        assertAll(
                () -> assertEquals(EstadoSala.LLENA, recuperada.estado()),
                () -> assertThrows(IngresoNoPermitido.class, () -> recuperada.unirse(BRUNO)));
    }
}
