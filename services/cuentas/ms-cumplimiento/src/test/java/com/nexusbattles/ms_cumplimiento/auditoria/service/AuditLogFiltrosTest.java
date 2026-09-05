package com.nexusbattles.ms_cumplimiento.auditoria.service;

import com.nexusbattles.ms_cumplimiento.auditoria.model.AuditActionType;
import com.nexusbattles.ms_cumplimiento.auditoria.model.AuditLog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class AuditLogFiltrosTest {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configurarConexion(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private AuditLogService service;

    private static final Pageable SIN_PAGINAR = PageRequest.of(0, 20);

    @Test
    void filtraSoloPorAdministrador() {
        service.registrar(AuditActionType.CAMBIO_ROL, "admin-A", "usuario-1",
            null, null, "motivo A", "10.0.0.1");
        service.registrar(AuditActionType.CAMBIO_ROL, "admin-B", "usuario-2",
            null, null, "motivo B", "10.0.0.2");

        Page<AuditLog> resultado = service.consultar("admin-A", null, null, null, SIN_PAGINAR);

        assertThat(resultado.getContent())
            .allMatch(entrada -> entrada.getAdministradorId().equals("admin-A"));
        assertThat(resultado.getContent())
            .anyMatch(entrada -> entrada.getMotivo().equals("motivo A"));
    }

    @Test
    void filtraSoloPorTipoDeAccion() {
        service.registrar(AuditActionType.SUSPENSION, "admin-C", "usuario-3",
            null, null, "suspendido por incumplimiento", "10.0.0.3");
        service.registrar(AuditActionType.APROBACION, "admin-C", "usuario-4",
            null, null, "aprobado tras revisión", "10.0.0.4");

        Page<AuditLog> resultado = service.consultar(null, AuditActionType.SUSPENSION, null, null, SIN_PAGINAR);

        assertThat(resultado.getContent())
            .allMatch(entrada -> entrada.getTipoAccion() == AuditActionType.SUSPENSION);
        assertThat(resultado.getContent())
            .anyMatch(entrada -> entrada.getMotivo().equals("suspendido por incumplimiento"));
    }

    @Test
    void filtraPorRangoDeFechasQueIncluyeElRegistro() {
        AuditLog entrada = service.registrar(AuditActionType.SANCION, "admin-D", "usuario-5",
            null, null, "dentro del rango", "10.0.0.5");

        Instant desde = entrada.getFechaHora().minus(1, ChronoUnit.HOURS);
        Instant hasta = entrada.getFechaHora().plus(1, ChronoUnit.HOURS);

        Page<AuditLog> resultado = service.consultar(null, null, desde, hasta, SIN_PAGINAR);

        assertThat(resultado.getContent())
            .anyMatch(e -> e.getId().equals(entrada.getId()));
    }

    @Test
    void filtraPorRangoDeFechasQueExcluyeElRegistro() {
        AuditLog entrada = service.registrar(AuditActionType.SANCION, "admin-E", "usuario-6",
            null, null, "fuera del rango", "10.0.0.6");

        Instant desde = entrada.getFechaHora().plus(2, ChronoUnit.HOURS);
        Instant hasta = entrada.getFechaHora().plus(3, ChronoUnit.HOURS);

        Page<AuditLog> resultado = service.consultar(null, null, desde, hasta, SIN_PAGINAR);

        assertThat(resultado.getContent())
            .noneMatch(e -> e.getId().equals(entrada.getId()));
    }

    @Test
    void sinFiltrosDevuelveTodosLosRegistros() {
        service.registrar(AuditActionType.OTRO, "admin-F", "usuario-7",
            null, null, "registro sin filtro 1", "10.0.0.7");
        service.registrar(AuditActionType.OTRO, "admin-G", "usuario-8",
            null, null, "registro sin filtro 2", "10.0.0.8");

        Page<AuditLog> resultado = service.consultar(null, null, null, null, SIN_PAGINAR);

        assertThat(resultado.getContent().size()).isGreaterThanOrEqualTo(2);
    }
}
