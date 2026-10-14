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
        return new FuenteDeUsuarios.Indicadores(12L,
                Map.of("ACTIVO", 8L, "PENDIENTE_VERIFICACION", 1L, "INACTIVO", 0L, "SUSPENDIDO", 2L, "BANEADO", 1L),
                new FuenteDeUsuarios.Registros("2026-09-30", "2026-10-01", 3L,
                        List.of(new FuenteDeUsuarios.DiaDeRegistro("2026-09-30", 1L),
                                new FuenteDeUsuarios.DiaDeRegistro("2026-10-01", 2L))),
                false, HASTA);
    }

    private static final TableroDeModeracion.Lectura<FuenteDeUsuarios.Indicadores> SIN_CUENTAS =
            TableroDeModeracion.Lectura.fallida(CausaDeIndisponibilidad.NO_RESPONDE, "no consultado");

    private static FuenteDeReportes.Frecuencia frecuencia(long... porDia) {
        List<FuenteDeReportes.PorDia> dias = new java.util.ArrayList<>();
        long total = 0;
        for (int i = 0; i < porDia.length; i++) {
            dias.add(new FuenteDeReportes.PorDia("2026-09-2" + i, porDia[i]));
            total += porDia[i];
        }
        Map<String, Long> categorias = new java.util.LinkedHashMap<>();
        FuenteDeReportes.CATEGORIAS_DEL_CONTRATO.forEach(c -> categorias.put(c, 0L));
        categorias.put("SPAM", total);
        return new FuenteDeReportes.Frecuencia(HASTA.minusDays(30), HASTA, total, dias, categorias, total, total);
    }

    private static TableroDeModeracion.Lectura<FuenteDeReportes.Frecuencia> reportes(long... porDia) {
        return TableroDeModeracion.Lectura.de(frecuencia(porDia));
    }

    private static UmbralesDeModeracion.Umbrales umbrales(Integer sanciones, Integer reportes) {
        return new UmbralesDeModeracion.Umbrales(sanciones, reportes);
    }

    @Test
    @DisplayName("sin umbral del PO no hay alertas ni se inventa uno (D-25); con umbral, se evalua por dia")
    void umbral() {
        TableroDeModeracion sinUmbral = TableroDeModeracion.de(agregados(1, 9), umbrales(null, null), SIN_CUENTAS,
                reportes(50));
        assertThat(sinUmbral.alertasConfiguradas()).isFalse();
        assertThat(sinUmbral.umbralSancionesPorDia()).isNull();
        assertThat(sinUmbral.umbralReportesPorDia()).isNull();
        assertThat(sinUmbral.alertas()).isEmpty();
        assertThat(sinUmbral.avisos()).isEmpty();

        TableroDeModeracion conUmbral = TableroDeModeracion.de(agregados(1, 9), umbrales(5, null), SIN_CUENTAS,
                reportes(50));
        assertThat(conUmbral.alertasConfiguradas()).isTrue();
        assertThat(conUmbral.alertas()).hasSize(1);
        assertThat(conUmbral.alertas().get(0)).contains("2026-09-21", "9");
        assertThat(conUmbral.avisos()).containsExactly(
                new TableroDeModeracion.Aviso(TableroDeModeracion.TipoDeAviso.SANCIONES, "2026-09-21", 9, 5));
        assertThat(TableroDeModeracion.de(agregados(1, 9), umbrales(0, null), SIN_CUENTAS, reportes())
                .alertasConfiguradas()).isFalse();
        assertThat(TableroDeModeracion.de(agregados(1, 9), null, SIN_CUENTAS, reportes())
                .alertasConfiguradas()).isFalse();
        assertThat(agregados(1, 9).maximoEnUnDia()).isEqualTo(9);
    }

    @Test
    @DisplayName("D-25: el umbral del PO es «a partir de»: un dia con EXACTAMENTE el umbral ya es alta frecuencia")
    void elUmbralEsAPartirDe() {
        // dias 2026-09-20, -21, -22, -23: 2, 3, 4 y 1 sanciones; umbral 3 (D-25, decidido por el PO el 2026-10-05)
        TableroDeModeracion tablero = TableroDeModeracion.de(agregados(2, 3, 4, 1), umbrales(3, null), SIN_CUENTAS,
                reportes());

        assertThat(tablero.alertasConfiguradas()).isTrue();
        assertThat(tablero.umbralSancionesPorDia()).isEqualTo(3);
        assertThat(tablero.alertas()).hasSize(2);
        assertThat(tablero.alertas().get(0)).isEqualTo("Alta frecuencia de sanciones el 2026-09-21: 3 (umbral: 3 o más por día)");
        assertThat(tablero.alertas().get(1)).contains("2026-09-22", "4");
        assertThat(tablero.alertas()).noneMatch(a -> a.contains("2026-09-20") || a.contains("2026-09-23"));
    }

    @Test
    @DisplayName("§7.3.8: la frecuencia de reportes se publica siempre y alerta, «a partir de», solo con su umbral")
    void alertaDeReportes() {
        // dias 2026-09-20, -21, -22: 4, 20 y 25 reportes
        TableroDeModeracion sinCifra = TableroDeModeracion.de(agregados(1), umbrales(3, null), SIN_CUENTAS,
                reportes(4, 20, 25));
        assertThat(sinCifra.reportes().total()).isEqualTo(49);
        assertThat(sinCifra.umbralReportesPorDia()).as("D-25: la cifra de reportes sigue sin decision").isNull();
        assertThat(sinCifra.avisos()).isEmpty();

        TableroDeModeracion conCifra = TableroDeModeracion.de(agregados(1), umbrales(3, 20), SIN_CUENTAS,
                reportes(4, 20, 25));
        assertThat(conCifra.alertasConfiguradas()).isTrue();
        assertThat(conCifra.umbralReportesPorDia()).isEqualTo(20);
        assertThat(conCifra.avisos()).containsExactly(
                new TableroDeModeracion.Aviso(TableroDeModeracion.TipoDeAviso.REPORTES, "2026-09-21", 20, 20),
                new TableroDeModeracion.Aviso(TableroDeModeracion.TipoDeAviso.REPORTES, "2026-09-22", 25, 20));
        assertThat(conCifra.alertas()).containsExactly(
                "Alta frecuencia de reportes el 2026-09-21: 20 (umbral: 20 o más por día)",
                "Alta frecuencia de reportes el 2026-09-22: 25 (umbral: 20 o más por día)");

        TableroDeModeracion soloReportes = TableroDeModeracion.de(agregados(1), umbrales(null, 20), SIN_CUENTAS,
                reportes(4));
        assertThat(soloReportes.alertasConfiguradas()).as("hay al menos un umbral").isTrue();
        assertThat(soloReportes.avisos()).isEmpty();
    }

    @Test
    @DisplayName("con los indicadores de cuentas y los reportes, no queda nada pendiente")
    void conIndicadoresDeCuentas() {
        TableroDeModeracion tablero = TableroDeModeracion.de(agregados(1), umbrales(null, null),
                TableroDeModeracion.Lectura.de(indicadores()), reportes(2));

        assertThat(tablero.registroDeUsuarios()).isNotNull();
        assertThat(tablero.registroDeUsuarios().total()).isEqualTo(12);
        assertThat(tablero.registroDeUsuarios().porEstado()).containsEntry("SUSPENDIDO", 2L);
        assertThat(tablero.registroDeUsuarios().registros().total()).isEqualTo(3);
        assertThat(tablero.pendientes()).isEmpty();
        assertThat(tablero.noDisponibles()).isEmpty();
    }

    @Test
    @DisplayName("sin cuentas o sin reportes, cada uno queda pendiente con su CAUSA y su motivo, sin tumbar lo demas (CA-03)")
    void sinIndicadoresDiceElMotivo() {
        TableroDeModeracion tablero = TableroDeModeracion.de(agregados(1), umbrales(3, 20),
                TableroDeModeracion.Lectura.fallida(CausaDeIndisponibilidad.NO_RESPONDE,
                        "ms-identidad no responde: connection refused"),
                TableroDeModeracion.Lectura.fallida(CausaDeIndisponibilidad.SIN_PERMISO,
                        "comentarios nego el permiso"));

        assertThat(tablero.registroDeUsuarios()).isNull();
        assertThat(tablero.reportes()).isNull();
        assertThat(tablero.sanciones()).isNotNull();
        assertThat(tablero.pendientes()).containsExactly(
                "registro de nuevos usuarios: ms-identidad no responde: connection refused",
                "frecuencia de reportes: comentarios nego el permiso");
        assertThat(tablero.noDisponibles()).containsExactly(
                new TableroDeModeracion.IndicadorNoDisponible(TableroDeModeracion.Indicador.REGISTRO_DE_USUARIOS,
                        CausaDeIndisponibilidad.NO_RESPONDE),
                new TableroDeModeracion.IndicadorNoDisponible(TableroDeModeracion.Indicador.FRECUENCIA_DE_REPORTES,
                        CausaDeIndisponibilidad.SIN_PERMISO));
        assertThat(tablero.avisos()).as("sin la frecuencia no se evalua su umbral").isEmpty();
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
