package com.nexusbattles.plataforma.metricasplataforma.tecnicas;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-MET-004: alertas solo sobre umbrales escritos; las brechas se dicen, no se disimulan. */
@DisplayName("Tablero tecnico (HU-MET-004)")
class TableroTecnicoTest {

    private static final Instant AHORA = Instant.parse("2026-10-01T10:00:00Z");

    @Test
    @DisplayName("cpu por encima del 75 %, latencia maxima por encima del objetivo y servicio caido disparan alertas")
    void alertas() {
        TableroTecnico tablero = TableroTecnico.de(AHORA, List.of(
                        new MetricasDeServicio("salas-partidas", 0.91, 180d, 1200, 40d, 820d, 3, null),
                        new MetricasDeServicio("comentarios", 0.10, 120d, 300, 20d, 90d, 0, null)),
                List.of(new TableroTecnico.Disponibilidad("salas-partidas", true, 99.99),
                        new TableroTecnico.Disponibilidad("comentarios", false, 97.5)),
                500, 99.95);
        assertThat(tablero.alertas()).extracting(TableroTecnico.Alerta::metrica)
                .containsExactly("cpu", "latencia", "disponibilidad");
        assertThat(tablero.alertas().get(0).servicio()).isEqualTo("salas-partidas");
        assertThat(tablero.alertas().get(2).detalle()).contains("no responde UP");
        assertThat(tablero.brechas()).isEmpty();
        assertThat(tablero.umbrales().cpu()).isEqualTo(0.75);
        assertThat(tablero.servicios().get(0).tasaDeError()).isEqualTo(3d / 1200);
    }

    @Test
    @DisplayName("un servicio que no se pudo recolectar es una brecha de observabilidad (CA-03), no una alerta ni un cero")
    void brecha() {
        TableroTecnico tablero = TableroTecnico.de(AHORA, List.of(
                        MetricasDeServicio.brecha("correo", "Connection refused"),
                        new MetricasDeServicio("torneos", 0.2, 100d, 0, null, null, 0, null)),
                List.of(new TableroTecnico.Disponibilidad("correo", false, null),
                        new TableroTecnico.Disponibilidad("torneos", true, 99.2)),
                500, 99.95);
        assertThat(tablero.conBrechas()).isTrue();
        assertThat(tablero.brechas()).containsExactly("correo: Connection refused");
        // El caido tambien sale como alerta de disponibilidad; el que esta por debajo del mes tambien.
        assertThat(tablero.alertas()).extracting(TableroTecnico.Alerta::servicio).containsExactly("correo", "torneos");
        assertThat(tablero.servicios().get(1).tasaDeError()).isNull();
        String texto = tablero.comoTexto();
        assertThat(texto).contains("BRECHA DE OBSERVABILIDAD", "torneos: cpu 20 %", "n/d", "Alertas:");
    }

    @Test
    @DisplayName("sin nada por encima de los umbrales el texto dice «Sin alertas»")
    void sinAlertas() {
        TableroTecnico tablero = TableroTecnico.de(AHORA, List.of(
                        new MetricasDeServicio("torneos", 0.2, 100d, 10, 12d, 30d, 0, null)),
                List.of(new TableroTecnico.Disponibilidad("torneos", true, 100d)), 500, 99.95);
        assertThat(tablero.alertas()).isEmpty();
        assertThat(tablero.comoTexto()).contains("Sin alertas.");
    }
}
