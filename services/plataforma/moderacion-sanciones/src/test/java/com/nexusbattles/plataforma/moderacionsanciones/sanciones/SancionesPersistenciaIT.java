package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las tres tablas de V2 contra PostgreSQL con {@code ddl-auto=validate}, y la
 * consulta de sancion activa leyendo el historial de verdad (la que antes
 * respondia «sin sancion» a todo el mundo).
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "sanciones.avisos.reintento-ms=3600000"
})
class SancionesPersistenciaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

    @Autowired
    private SancionesService servicio;

    @Autowired
    private ConsultaSancionActivaService consulta;

    @Autowired
    private AvisoPendienteRepository avisos;

    @Autowired
    private ApelacionRepository apelaciones;

    @Test
    @DisplayName("advertencia no restringe; suspension si; revertida por apelacion deja de restringir; todo queda guardado")
    void cicloCompleto() {
        UUID jugador = UUID.randomUUID();
        Actor moderadora = new Actor(UUID.randomUUID(), "MODERADOR");
        Actor admin = new Actor(UUID.randomUUID(), "ADMINISTRADOR");

        servicio.emitir(moderadora, new SancionesService.SolicitudDeSancion(jugador, Sancion.Tipo.ADVERTENCIA,
                "Lenguaje ofensivo", "Convivencia", "com-1", null, false));
        assertThat(consulta.consultar(jugador).sancionActiva()).isFalse();

        Sancion suspension = servicio.emitir(moderadora, new SancionesService.SolicitudDeSancion(jugador,
                Sancion.Tipo.SUSPENSION, "Reincidencia", null, null, 24L, false));
        var activa = consulta.consultar(jugador);
        assertThat(activa.sancionActiva()).isTrue();
        assertThat(activa.tipo()).isEqualTo("SUSPENSION");
        // PostgreSQL guarda microsegundos: se compara al milisegundo.
        assertThat(activa.vigenteHasta().truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
                .isEqualTo(suspension.vigenteHasta().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));

        Apelacion apelacion = servicio.apelar(new Actor(jugador, "JUGADOR"), suspension.id(), "No fui yo");
        servicio.resolver(admin, apelacion.id(), Apelacion.Estado.REVERTIDA, "Tiene razon", null);

        assertThat(consulta.consultar(jugador).sancionActiva()).isFalse();
        assertThat(servicio.historialDe(admin, jugador)).hasSize(2);
        assertThat(apelaciones.findById(apelacion.id()).orElseThrow().estado()).isEqualTo(Apelacion.Estado.REVERTIDA);
        assertThat(avisos.findByEntregadoEnIsNullOrderByCreadoEnAsc(org.springframework.data.domain.PageRequest.of(0, 10)))
                .extracting(AvisoPendiente::tipo)
                .contains("SANCION_ADVERTENCIA", "SANCION_SUSPENSION", "APELACION_REVERTIDA");

        // HU-MET-001: los agregados del periodo salen de lo mismo que se guardo.
        java.time.OffsetDateTime ahora = java.time.OffsetDateTime.now();
        MetricasDeModeracion metricas = servicio.metricas(ahora.minusDays(1), ahora.plusDays(1));
        assertThat(metricas.total()).isGreaterThanOrEqualTo(2);
        assertThat(metricas.porTipo().get(Sancion.Tipo.ADVERTENCIA)).isGreaterThanOrEqualTo(1);
        assertThat(metricas.porTipo().get(Sancion.Tipo.SUSPENSION)).isGreaterThanOrEqualTo(1);
        assertThat(metricas.apelaciones().get(Apelacion.Estado.REVERTIDA)).isGreaterThanOrEqualTo(1);
        assertThat(metricas.revertidas()).isGreaterThanOrEqualTo(1);
        assertThat(metricas.moderadoresActivos()).isGreaterThanOrEqualTo(1);
        assertThat(metricas.maximoEnUnDia()).isGreaterThanOrEqualTo(1);
        assertThat(servicio.metricas(ahora.minusDays(40), ahora.minusDays(39)).total()).isZero();
    }
}
