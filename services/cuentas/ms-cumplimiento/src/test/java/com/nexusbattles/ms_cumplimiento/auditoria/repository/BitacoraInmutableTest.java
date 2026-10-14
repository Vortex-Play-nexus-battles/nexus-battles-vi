package com.nexusbattles.ms_cumplimiento.auditoria.repository;

import com.nexusbattles.ms_cumplimiento.auditoria.model.AuditActionType;
import com.nexusbattles.ms_cumplimiento.auditoria.model.AuditLog;
import com.nexusbattles.ms_cumplimiento.auditoria.service.AuditLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §7.3.5 — «el registro de auditoría debe ser inmutable (no puede ser
 * modificado o eliminado)», contra PostgreSQL de verdad: lo que la API no
 * ofrece (no hay ruta de edición ni de borrado) tampoco se puede hacer
 * entrando por la base. UPDATE y DELETE los cierran los disparadores por fila
 * de V1/V2; TRUNCATE, el disparador por sentencia de V4. Después de cada
 * intento la entrada sigue ahí, igual.
 *
 * <p>Requiere Docker (Testcontainers), como el resto de pruebas con base.
 */
@SpringBootTest
@Testcontainers
@DisplayName("La bitácora es inmutable también entrando por la base (§7.3.5)")
class BitacoraInmutableTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configurarConexion(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private AuditLogService service;

    @Autowired
    private JdbcTemplate jdbc;

    private AuditLog unaEntrada() {
        return service.registrar(AuditActionType.SUSPENSION, "admin-inmutable", "usuario-inmutable",
            "ACTIVO", "SUSPENDIDO", "Motivo que no se puede cambiar", "10.0.0.9");
    }

    private String motivoDe(String id) {
        return jdbc.queryForObject("select motivo from audit_log where id = ?", String.class, id);
    }

    @Test
    @DisplayName("UPDATE se rechaza y la entrada no cambia")
    void sinModificar() {
        AuditLog entrada = unaEntrada();

        assertThatThrownBy(() -> jdbc.update("update audit_log set motivo = 'otro' where id = ?", entrada.getId()))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("append-only");
        assertThat(motivoDe(entrada.getId())).isEqualTo("Motivo que no se puede cambiar");
    }

    @Test
    @DisplayName("DELETE se rechaza y la entrada sigue")
    void sinBorrar() {
        AuditLog entrada = unaEntrada();

        assertThatThrownBy(() -> jdbc.update("delete from audit_log where id = ?", entrada.getId()))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("append-only");
        assertThat(jdbc.queryForObject("select count(*) from audit_log where id = ?", Long.class, entrada.getId()))
            .isEqualTo(1L);
    }

    @Test
    @DisplayName("TRUNCATE se rechaza (V4) y la bitácora conserva lo que tenía")
    void sinVaciar() {
        AuditLog entrada = unaEntrada();
        Long antes = jdbc.queryForObject("select count(*) from audit_log", Long.class);

        assertThatThrownBy(() -> jdbc.execute("truncate table audit_log"))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("TRUNCATE no permitido");
        assertThat(jdbc.queryForObject("select count(*) from audit_log", Long.class)).isEqualTo(antes);
        assertThat(motivoDe(entrada.getId())).isEqualTo("Motivo que no se puede cambiar");
    }
}
