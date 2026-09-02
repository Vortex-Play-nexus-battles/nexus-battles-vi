package com.nexusbattles.ms_cumplimiento.auditoria.service;
import com.nexusbattles.ms_cumplimiento.auditoria.dto.AuditLogResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.nexusbattles.ms_cumplimiento.auditoria.exception.AuditWriteException;
import com.nexusbattles.ms_cumplimiento.auditoria.model.AuditActionType;
import com.nexusbattles.ms_cumplimiento.auditoria.model.AuditLog;
import org.junit.jupiter.api.Assertions;
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

@SpringBootTest
@Testcontainers
class AuditLogServiceTest {

    // Postgres real y desechable: se prende solo para correr las
    // pruebas y se apaga al terminar. Necesario porque el trigger
    // anti-tampering es SQL puro, no se puede probar con Mockito.
    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configurarConexion(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // El application.properties de test fuerza H2 por defecto (para
        // otras pruebas más rápidas); aquí lo sobreescribimos explícitamente
        // a Postgres, porque el trigger que probamos es SQL real de Postgres.
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // Flyway corre automáticamente contra este Postgres temporal
        // gracias a spring.flyway.enabled=true en application.properties,
        // así que las tablas y triggers ya existen antes de cada prueba.
    }

    @Autowired
    private AuditLogService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void elTriggerBloqueaUnUpdateSobreAuditLog() {
        // Arrange: creamos un registro válido usando el propio servicio
        AuditLog entrada = service.registrar(
            AuditActionType.CAMBIO_ROL,
            "admin-123",
            "usuario-456",
            "USER",
            "ADMIN",
            "Ascenso por antigüedad",
            "192.168.1.1"
        );
        assertThat(entrada.getId()).isNotNull();

        // Act + Assert: cualquier intento de UPDATE directo por SQL
        // debe fallar, porque el trigger lo bloquea antes de que se
        // aplique.
        assertThatThrownBy(() ->
            jdbcTemplate.update(
                "UPDATE audit_log SET motivo = 'manipulado' WHERE id = ?",
                entrada.getId()
            )
        )
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("append-only");
    }

    @Test
    void elTriggerBloqueaUnDeleteSobreAuditLog() {
        AuditLog entrada = service.registrar(
            AuditActionType.SUSPENSION,
            "admin-789",
            "usuario-321",
            "ACTIVO",
            "SUSPENDIDO",
            "Incumplimiento de normas",
            "10.0.0.5"
        );
        assertThat(entrada.getId()).isNotNull();

        assertThatThrownBy(() ->
            jdbcTemplate.update(
                "DELETE FROM audit_log WHERE id = ?",
                entrada.getId()
            )
        )
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("append-only");

        // Confirmamos que el registro sigue existiendo intacto:
        // el intento de borrado no debe haber tenido ningún efecto.
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_log WHERE id = ?",
            Integer.class,
            entrada.getId()
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    void siFallaElGuardadoSeLanzaAuditWriteExceptionYNoQuedaRegistro() {
        // "motivo" tiene un límite de 500 caracteres en la tabla
        // (VARCHAR(500)); forzamos un fallo real de base de datos
        // pasando uno más largo, en vez de simular el error con un mock.
        String motivoDemasiadoLargo = "x".repeat(600);

        Assertions.assertThrows(
            AuditWriteException.class,
            () -> service.registrar(
                AuditActionType.OTRO,
                "admin-999",
                "usuario-999",
                null,
                null,
                motivoDemasiadoLargo,
                "127.0.0.1"
            )
        );

        // HU-AUD-001 exige que, si el registro no se puede escribir,
        // la acción no se consuma: confirmamos que no quedó nada
        // guardado con ese administrador.
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_log WHERE administrador_id = ?",
            Integer.class,
            "admin-999"
        );
        assertThat(count).isEqualTo(0);
    }
    @Test
    void consultarConFiltrosDeberiaFiltrarPorAdministradorYTipoAccion() {
        // Arrange
        service.registrar(
            AuditActionType.CREACION,
            "admin-filtro-1",
            "user-1",
            null,
            null,
            "Creación inicial",
            "127.0.0.1"
        );
        service.registrar(
            AuditActionType.SANCION,
            "admin-filtro-1",
            "user-2",
            null,
            null,
            "Sanción temporal",
            "127.0.0.1"
        );
        service.registrar(
            AuditActionType.CREACION,
            "admin-filtro-2",
            "user-3",
            null,
            null,
            "Otra creación",
            "127.0.0.1"
        );

        Pageable pageable = PageRequest.of(0, 10);

        // Act: service.consultar retorna Page<AuditLog>
        Page<AuditLog> resultado = service.consultar(
            "admin-filtro-1",
            AuditActionType.CREACION,
            null,
            null,
            pageable
        );

        // Assert
        assertThat(resultado.getTotalElements()).isEqualTo(1);
        assertThat(resultado.getContent().get(0).getAdministradorId()).isEqualTo("admin-filtro-1");
        assertThat(resultado.getContent().get(0).getTipoAccion()).isEqualTo(AuditActionType.CREACION);
    }

    @Test
    void consultarConRangoDeFechasDeberiaRetornarRegistrosValidos() {
        // Arrange
        service.registrar(
            AuditActionType.CAMBIO_ROL,
            "admin-fecha",
            "user-fecha",
            "USER",
            "ADMIN",
            "Cambio con rango de fechas",
            "10.0.0.1"
        );

        Instant ahora = Instant.now();
        Instant haceUnHora = ahora.minus(1, ChronoUnit.HOURS);
        Instant enUnaHora = ahora.plus(1, ChronoUnit.HOURS);

        Pageable pageable = PageRequest.of(0, 10);

        // Act
        Page<AuditLog> resultado = service.consultar(
            "admin-fecha",
            null,
            haceUnHora,
            enUnaHora,
            pageable
        );

        // Assert
        assertThat(resultado.getContent()).isNotEmpty();
        assertThat(resultado.getContent().get(0).getAdministradorId()).isEqualTo("admin-fecha");
    }
}
