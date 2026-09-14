package com.nexusbattles.ms_finanzas.transacciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Prueba de integración del repositorio contra un PostgreSQL real levantado
 * por Testcontainers. Verifica el mapeo JPA, las restricciones de la
 * migración Flyway V1 (unique en ref_id) y el índice de ordenamiento por
 * (uid, creado desc) que sostiene el historial que se expone en el PR
 * siguiente.
 *
 * <p>Sigue el mismo patrón que {@code SubastaRepositoryIT} de ms-subastas:
 * {@code @SpringBootTest} + {@code @ServiceConnection} de Spring Boot 4.1,
 * que auto-configura el datasource desde el contenedor sin
 * {@code @DynamicPropertySource} manual, y desactiva la clase cuando no hay
 * Docker disponible.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class TransaccionRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private TransaccionRepository repositorio;

    private Transaccion nueva(String refId, String uid, Instant creado) {
        Transaccion t = new Transaccion();
        t.setRefId(refId);
        t.setUidUsuario(uid);
        t.setMonto(new BigDecimal("100.00"));
        t.setMoneda("COP");
        t.setConcepto("prueba");
        t.setResultado(ResultadoTransaccion.APROBADO);
        t.setCreado(creado);
        t.setActualizado(creado);
        return t;
    }

    @Test
    void guardarYRecuperarPorId() {
        Transaccion guardada = repositorio.save(
                nueva("ref-guardar", "uid-1", Instant.parse("2026-09-14T10:00:00Z")));

        Optional<Transaccion> encontrada = repositorio.findById(guardada.getId());

        assertThat(encontrada).isPresent();
        assertThat(encontrada.get().getRefId()).isEqualTo("ref-guardar");
        assertThat(encontrada.get().getMonto()).isEqualByComparingTo("100.00");
    }

    @Test
    void findByRefId_existente_devuelveOptionalPresente() {
        repositorio.save(nueva("ref-lookup", "uid-1", Instant.parse("2026-09-14T10:00:00Z")));
        assertThat(repositorio.findByRefId("ref-lookup")).isPresent();
    }

    @Test
    void findByRefId_inexistente_devuelveVacio() {
        assertThat(repositorio.findByRefId("no-existe")).isEmpty();
    }

    @Test
    void existsByRefId_reflejaEstadoReal() {
        assertThat(repositorio.existsByRefId("ref-exists")).isFalse();
        repositorio.save(nueva("ref-exists", "uid-1", Instant.parse("2026-09-14T10:00:00Z")));
        assertThat(repositorio.existsByRefId("ref-exists")).isTrue();
    }

    @Test
    void refIdDuplicado_lanzaDataIntegrityViolation() {
        repositorio.save(nueva("ref-dup", "uid-1", Instant.parse("2026-09-14T10:00:00Z")));

        assertThatThrownBy(() -> repositorio.saveAndFlush(
                nueva("ref-dup", "uid-2", Instant.parse("2026-09-14T11:00:00Z"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByUidUsuarioOrderByCreadoDesc_paginado_devuelveEnOrdenCorrecto() {
        repositorio.save(nueva("ref-a", "uid-listar", Instant.parse("2026-09-14T10:00:00Z")));
        repositorio.save(nueva("ref-b", "uid-listar", Instant.parse("2026-09-14T12:00:00Z")));
        repositorio.save(nueva("ref-c", "uid-listar", Instant.parse("2026-09-14T11:00:00Z")));
        // Ruido de otro usuario que no debe aparecer.
        repositorio.save(nueva("ref-otro", "uid-ajeno", Instant.parse("2026-09-14T13:00:00Z")));

        Page<Transaccion> pagina = repositorio.findByUidUsuarioOrderByCreadoDesc(
                "uid-listar", PageRequest.of(0, 20));

        assertThat(pagina.getTotalElements()).isEqualTo(3);
        assertThat(pagina.getContent()).extracting(Transaccion::getRefId)
                .containsExactly("ref-b", "ref-c", "ref-a");
    }
}
