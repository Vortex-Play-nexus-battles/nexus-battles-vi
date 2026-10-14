package com.nexusbattles.ms_cumplimiento.auditoria.catalogo;

import com.nexusbattles.ms_cumplimiento.auditoria.repository.AuditLogRepository;
import com.nexusbattles.ms_cumplimiento.auditoria.service.AuditLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SA-18 — la importación del catálogo contra PostgreSQL de verdad, con las
 * migraciones de Flyway (V5 trae el nombre de lo afectado): cada operación
 * entra una vez con su referencia, su momento y su nombre; repetir la ronda no
 * duplica nada; y la consulta del último momento importado funciona en la base
 * real.
 *
 * <p>Requiere Docker (Testcontainers), como el resto de pruebas con base. Se
 * llama {@code ...Test} y no {@code ...IT}: este módulo usa las inclusiones
 * por omisión de Surefire, que no recogen los {@code IT}.
 */
@SpringBootTest
@Testcontainers
@DisplayName("La bitácora importa las operaciones del catálogo una sola vez (SA-18)")
class ImportacionDelCatalogoContraPostgresTest {

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
    private AuditLogRepository repositorio;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("dos rondas con las mismas operaciones dejan una entrada por operación, con su nombre")
    void unaVezCadaUna() {
        ClienteDelCatalogo cliente = mock(ClienteDelCatalogo.class);
        Instant suspendido = Instant.parse("2026-10-14T15:00:00.250Z");
        LoteDelCatalogo lote = new LoteDelCatalogo(suspendido, true, List.of(
            new OperacionDelCatalogo("op-alta", Instant.parse("2026-10-14T14:00:00Z"), "uid-admin",
                "ALTA_PRODUCTO", "PRODUCTO", "p-1", "Corona del Nexo", null,
                "Tipo: Arma · Estado: Pieza única", null, "Alta del producto en el catálogo", "181.52.10.4"),
            new OperacionDelCatalogo("op-susp", suspendido, "uid-admin", "SUSPENSION_PRODUCTO", "PRODUCTO", "p-1",
                "Corona del Nexo", "Estado: Pieza única", "Estado: Suspendido", "Rompe el balance",
                "Suspensión del producto", null)));
        when(cliente.consultar(any(), eq(100))).thenReturn(lote);
        ImportadorDelCatalogo importador =
            new ImportadorDelCatalogo(cliente, service, repositorio, Duration.ofMinutes(2), 100, 10);

        assertThat(importador.importar()).isEqualTo(2);
        assertThat(importador.importar()).isZero();

        List<Map<String, Object>> filas = jdbc.queryForList(
            "select tipo_accion, afectado, nombre_afectado, motivo, ip_origen, ocurrido_en, referencia"
                + " from audit_log where referencia like 'productos:%' order by ocurrido_en");
        assertThat(filas).hasSize(2);
        assertThat(filas.get(0)).containsEntry("tipo_accion", "CREACION")
            .containsEntry("afectado", "producto:p-1")
            .containsEntry("nombre_afectado", "Corona del Nexo")
            .containsEntry("motivo", "Alta del producto en el catálogo")
            .containsEntry("referencia", "productos:op-alta");
        assertThat(filas.get(1)).containsEntry("tipo_accion", "SUSPENSION")
            .containsEntry("motivo", "Rompe el balance")
            .containsEntry("ip_origen", "no registrada");
        assertThat(repositorio.ultimoMomentoConReferencia("productos:%")).isEqualTo(suspendido);
        assertThat(repositorio.ultimoMomentoConReferencia("otro-origen:%")).isNull();
    }
}
