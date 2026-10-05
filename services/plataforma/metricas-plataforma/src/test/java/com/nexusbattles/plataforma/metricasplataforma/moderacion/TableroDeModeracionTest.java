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

    private static FuenteDeUsuarios.Indicadores indicadores() {
        return new FuenteDeUsuarios.Indicadores(12,
                Map.of("ACTIVO", 8L, "PENDIENTE_VERIFICACION", 1L, "INACTIVO", 0L, "SUSPENDIDO", 2L, "BANEADO", 1L),
                new FuenteDeUsuarios.Registros("2026-09-30", "2026-10-01", 3,
                        List.of(new FuenteDeUsuarios.DiaDeRegistro("2026-09-30", 1),
                                new FuenteDeUsuarios.DiaDeRegistro("2026-10-01", 2))),
                false, HASTA);
    }

    @Test
    @DisplayName("sin umbral del PO no hay alertas ni se inventa uno (D-25); con umbral, se evalua por dia")
    void umbral() {
        TableroDeModeracion sinUmbral = TableroDeModeracion.de(agregados(1, 9), null, null, "no consultado");
        assertThat(sinUmbral.alertasConfiguradas()).isFalse();
        assertThat(sinUmbral.umbralSancionesPorDia()).isNull();
        assertThat(sinUmbral.alertas()).isEmpty();
        assertThat(sinUmbral.pendientes()).hasSize(2);

        TableroDeModeracion conUmbral = TableroDeModeracion.de(agregados(1, 9), 5, null, "no consultado");
        assertThat(conUmbral.alertasConfiguradas()).isTrue();
        assertThat(conUmbral.alertas()).hasSize(1);
        assertThat(conUmbral.alertas().get(0)).contains("2026-09-21", "9");
        assertThat(TableroDeModeracion.de(agregados(1, 9), 0, null, "no consultado").alertasConfiguradas()).isFalse();
        assertThat(agregados(1, 9).maximoEnUnDia()).isEqualTo(9);
    }

    @Test
    @DisplayName("D-25: el umbral del PO es «a partir de»: un dia con EXACTAMENTE el umbral ya es alta frecuencia")
    void elUmbralEsAPartirDe() {
        // dias 2026-09-20, -21, -22, -23: 2, 3, 4 y 1 sanciones; umbral 3 (D-25, decidido por el PO el 2026-10-05)
        TableroDeModeracion tablero = TableroDeModeracion.de(agregados(2, 3, 4, 1), 3, null, "no consultado");

        assertThat(tablero.alertasConfiguradas()).isTrue();
        assertThat(tablero.umbralSancionesPorDia()).isEqualTo(3);
        assertThat(tablero.alertas()).hasSize(2);
        assertThat(tablero.alertas().get(0)).contains("2026-09-21", "3");
        assertThat(tablero.alertas().get(1)).contains("2026-09-22", "4");
        assertThat(tablero.alertas()).noneMatch(a -> a.contains("2026-09-20") || a.contains("2026-09-23"));
    }

    @Test
    @DisplayName("con los indicadores de cuentas de ms-identidad se publican y 'nuevos usuarios' deja de ser pendiente")
    void conIndicadoresDeCuentas() {
        TableroDeModeracion tablero = TableroDeModeracion.de(agregados(1), null, indicadores(), null);

        assertThat(tablero.registroDeUsuarios()).isNotNull();
        assertThat(tablero.registroDeUsuarios().total()).isEqualTo(12);
        assertThat(tablero.registroDeUsuarios().porEstado()).containsEntry("SUSPENDIDO", 2L);
        assertThat(tablero.registroDeUsuarios().registros().total()).isEqualTo(3);
        assertThat(tablero.pendientes())
                .hasSize(1)
                .allMatch(p -> p.startsWith("frecuencia de reportes"));
    }

    @Test
    @DisplayName("sin los indicadores, 'nuevos usuarios' queda pendiente con el MOTIVO real, sin tumbar lo demas (CA-03)")
    void sinIndicadoresDiceElMotivo() {
        TableroDeModeracion tablero = TableroDeModeracion.de(agregados(1), null, null,
                "ms-identidad no responde: connection refused");

        assertThat(tablero.registroDeUsuarios()).isNull();
        assertThat(tablero.sanciones()).isNotNull();
        assertThat(tablero.pendientes())
                .anyMatch(p -> p.startsWith("registro de nuevos usuarios") && p.contains("connection refused"))
                .anyMatch(p -> p.startsWith("frecuencia de reportes"));
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
