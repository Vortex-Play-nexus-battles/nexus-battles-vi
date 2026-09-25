package com.nexusbattles.plataforma.correo.cola;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** correo_envios_total{estado,plantilla} y correo_pendientes. */
class MetricasDeCorreoTest {

    private final SimpleMeterRegistry registro = new SimpleMeterRegistry();
    private final RepositorioDeEnvios repositorio = mock(RepositorioDeEnvios.class);

    @Test
    void cuentaCadaEstadoPorPlantilla() {
        MetricasDeCorreo metricas = new MetricasDeCorreo(registro, repositorio);

        metricas.registrar(EstadoDeEnvio.ENVIADO, "bienvenida");
        metricas.registrar(EstadoDeEnvio.ENVIADO, "bienvenida");
        metricas.registrar(EstadoDeEnvio.ERROR_REINTENTABLE, "bienvenida");
        metricas.registrar(EstadoDeEnvio.ENVIADO, "sancion");
        metricas.registrar(EstadoDeEnvio.FALLIDO, null);

        assertThat(registro.get("correo.envios").tags("estado", "ENVIADO", "plantilla", "bienvenida")
                .counter().count()).isEqualTo(2.0);
        assertThat(registro.get("correo.envios").tags("estado", "ERROR_REINTENTABLE", "plantilla", "bienvenida")
                .counter().count()).isEqualTo(1.0);
        assertThat(registro.get("correo.envios").tags("estado", "ENVIADO", "plantilla", "sancion")
                .counter().count()).isEqualTo(1.0);
        assertThat(registro.get("correo.envios").tags("estado", "FALLIDO", "plantilla", "desconocida")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void losPendientesSeLeenDeLaColaAlMirarlos() {
        when(repositorio.contarPendientes()).thenReturn(4L, 0L);
        new MetricasDeCorreo(registro, repositorio);

        assertThat(registro.get("correo.pendientes").gauge().value()).isEqualTo(4.0);
        assertThat(registro.get("correo.pendientes").gauge().value()).isEqualTo(0.0);
    }

    @Test
    void siLaBaseNoRespondeLosPendientesSonDesconocidosYNoCero() {
        when(repositorio.contarPendientes()).thenThrow(new CannotGetJdbcConnectionException("caida"));
        new MetricasDeCorreo(registro, repositorio);

        assertThat(registro.get("correo.pendientes").gauge().value()).isNaN();
    }
}
