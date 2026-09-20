package com.nexusbattles.ms_finanzas.partidas;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integración del repositorio de cofres contra PostgreSQL real. Verifica el
 * mapeo JPA, la migración V3 y el orden por fecha desc del índice.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class CofreEntregadoRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private CofreEntregadoRepository repositorio;

    private CofreEntregado nuevo(String uid, Instant entregadoEn) {
        CofreEntregado c = new CofreEntregado();
        c.setUidJugador(uid);
        c.setSemanaIso("2026-W38");
        c.setContenido(CofreService.CONTENIDO_PLACEHOLDER);
        c.setEntregadoEn(entregadoEn);
        return c;
    }

    @Test
    void guardarYRecuperarPorId() {
        CofreEntregado guardado = repositorio.save(
                nuevo("uid-1", Instant.parse("2026-09-16T10:00:00Z")));

        assertThat(repositorio.findById(guardado.getId())).isPresent();
    }

    @Test
    void findByUidJugadorOrderByEntregadoEnDesc_devuelveEnOrdenCorrectoYFiltraOtros() {
        repositorio.save(nuevo("uid-lista", Instant.parse("2026-09-16T10:00:00Z")));
        repositorio.save(nuevo("uid-lista", Instant.parse("2026-09-16T12:00:00Z")));
        repositorio.save(nuevo("uid-lista", Instant.parse("2026-09-16T11:00:00Z")));
        repositorio.save(nuevo("uid-otro", Instant.parse("2026-09-16T13:00:00Z")));

        Page<CofreEntregado> pagina = repositorio.findByUidJugadorOrderByEntregadoEnDesc(
                "uid-lista", PageRequest.of(0, 20));

        assertThat(pagina.getTotalElements()).isEqualTo(3);
        assertThat(pagina.getContent()).extracting(c -> c.getEntregadoEn().toString())
                .containsExactly(
                        "2026-09-16T12:00:00Z",
                        "2026-09-16T11:00:00Z",
                        "2026-09-16T10:00:00Z");
    }

    @Test
    void idSeGeneraAutomaticamente() {
        CofreEntregado guardado = repositorio.save(
                nuevo("uid-x", Instant.parse("2026-09-16T10:00:00Z")));

        UUID id = guardado.getId();
        assertThat(id).isNotNull();
    }
}
