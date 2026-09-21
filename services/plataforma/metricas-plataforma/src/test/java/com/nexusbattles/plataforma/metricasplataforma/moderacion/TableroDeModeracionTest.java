package com.nexusbattles.plataforma.metricasplataforma.moderacion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("Tablero de moderacion (HU-MET-001)")
class TableroDeModeracionTest {

    private static final OffsetDateTime HASTA = OffsetDateTime.of(2026, 10, 1, 10, 0, 0, 0, ZoneOffset.UTC);

    private static FuenteDeModeracion.Agregados agregados(long... porDia) {
        List<FuenteDeModeracion.PorDia> dias = new java.util.ArrayList<>();
        for (int i = 0; i < porDia.length; i++) {
            dias.add(new FuenteDeModeracion.PorDia("2026-09-2" + i, porDia[i]));
        }
        return new FuenteDeModeracion.Agregados(HASTA.minusDays(30), HASTA, 0, Map.of(), dias, Map.of(), 0, 0);
    }

    @Test
    @DisplayName("sin umbral del PO no hay alertas ni se inventa uno (D-25); con umbral, se evalua por dia")
    void umbral() {
        TableroDeModeracion sinUmbral = TableroDeModeracion.de(agregados(1, 9), null);
        assertThat(sinUmbral.alertasConfiguradas()).isFalse();
        assertThat(sinUmbral.umbralSancionesPorDia()).isNull();
        assertThat(sinUmbral.alertas()).isEmpty();
        assertThat(sinUmbral.pendientes()).hasSize(2);

        TableroDeModeracion conUmbral = TableroDeModeracion.de(agregados(1, 9), 5);
        assertThat(conUmbral.alertasConfiguradas()).isTrue();
        assertThat(conUmbral.alertas()).hasSize(1);
        assertThat(conUmbral.alertas().get(0)).contains("2026-09-21", "9");
        assertThat(TableroDeModeracion.de(agregados(1, 9), 0).alertasConfiguradas()).isFalse();
        assertThat(agregados(1, 9).maximoEnUnDia()).isEqualTo(9);
    }

    @Test
    @DisplayName("el cliente lee GET /sanciones/metricas con el periodo; caido es FuenteNoDisponible")
    void cliente() {
        RestClient.Builder constructor = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        ClienteDeModeracion cliente = new ClienteDeModeracion(constructor.build(), "http://moderacion/api/v1/");
        servidor.expect(requestTo(org.hamcrest.Matchers.startsWith("http://moderacion/api/v1/sanciones/metricas?desde=2026-09-01")))
                .andRespond(withSuccess("{\"desde\":\"2026-09-01T10:00:00Z\",\"hasta\":\"2026-10-01T10:00:00Z\",\"total\":2,"
                        + "\"porTipo\":{\"ADVERTENCIA\":2,\"SUSPENSION\":0,\"BANEO\":0},\"porDia\":[{\"fecha\":\"2026-09-30\",\"emitidas\":2}],"
                        + "\"apelaciones\":{\"PENDIENTE\":0,\"MANTENIDA\":0,\"REDUCIDA\":0,\"REVERTIDA\":0},\"moderadoresActivos\":1,\"revertidas\":0}",
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(org.hamcrest.Matchers.startsWith("http://moderacion/api/v1/sanciones/metricas")))
                .andRespond(withServerError());

        FuenteDeModeracion.Agregados a = cliente.consultar(HASTA.minusDays(30), HASTA);
        assertThat(a.total()).isEqualTo(2);
        assertThat(a.porTipo().get("ADVERTENCIA")).isEqualTo(2);
        assertThat(a.maximoEnUnDia()).isEqualTo(2);
        assertThatThrownBy(() -> cliente.consultar(HASTA.minusDays(30), HASTA))
                .isInstanceOf(FuenteDeModeracion.FuenteNoDisponible.class);
        servidor.verify();
    }
}
