package com.nexusbattles.plataforma.salaspartidas.persistencia;

import com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeVinculosDeTorneo;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.VinculoDeTorneo;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El vinculo sala↔encuentro contra una PostgreSQL de verdad — HU-TOR-004.
 * Con {@code ddl-auto=validate}: si la entidad y V12 dejan de coincidir, falla aqui.
 */
@Testcontainers
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Import({RepositorioVinculosDeTorneoJpa.class, RepositorioSalasJpa.class})
class RepositorioVinculosDeTorneoJpaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TORNEO = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final Instant T0 = Instant.parse("2026-09-21T10:00:00Z");

    @Autowired
    private RepositorioDeVinculosDeTorneo vinculos;

    @Autowired
    private RepositorioDeSalas salas;

    private Sala salaGuardada() {
        return salas.guardar(Sala.crear(new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, 0, false, null), ANA,
                new FichaDeParticipante("Ana", new HeroeDeCombate("h-a", "Arquero", null, 5, 100, 100))));
    }

    @Test
    @DisplayName("se guarda, se recupera igual y el segundo guardar sobre la misma sala actualiza (informado)")
    void guardaRecuperaYActualiza() {
        Sala sala = salaGuardada();
        VinculoDeTorneo nuevo = VinculoDeTorneo.nuevo(sala.id(), TORNEO, 5, ANA, T0);
        vinculos.guardar(nuevo);

        assertEquals(nuevo, vinculos.buscarPorSala(sala.id()).orElseThrow());

        vinculos.guardar(nuevo.fallo("torneos respondio 503"));
        VinculoDeTorneo conFallo = vinculos.buscarPorSala(sala.id()).orElseThrow();
        vinculos.guardar(conFallo.informado(T0.plusSeconds(60)));
        VinculoDeTorneo informado = vinculos.buscarPorSala(sala.id()).orElseThrow();

        assertAll(
                () -> assertEquals("torneos respondio 503", conFallo.ultimoFallo()),
                () -> assertTrue(informado.informado()),
                () -> assertEquals(T0.plusSeconds(60), informado.informadoEn()),
                () -> assertNull(informado.ultimoFallo()),
                () -> assertTrue(vinculos.buscarPorSala(UUID.randomUUID()).isEmpty()));
    }

    @Test
    @DisplayName("una sala que no existe no puede ser encuentro: la clave foranea lo impide")
    void exigeSalaReal() {
        assertThrows(DataIntegrityViolationException.class,
                () -> vinculos.guardar(VinculoDeTorneo.nuevo(UUID.randomUUID(), TORNEO, 1, ANA, T0)));
    }
}
