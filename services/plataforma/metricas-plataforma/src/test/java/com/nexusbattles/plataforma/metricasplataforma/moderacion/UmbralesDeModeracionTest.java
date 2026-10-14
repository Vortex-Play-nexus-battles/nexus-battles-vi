package com.nexusbattles.plataforma.metricasplataforma.moderacion;

import com.nexusbattles.plataforma.resiliencia.parametros.LectorDeParametros;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("Umbrales de alta frecuencia: del catalogo, con la variable de respaldo (D-25)")
class UmbralesDeModeracionTest {

    private static final String CATALOGO = "http://parametros:8088/api/v1";
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-14T15:00:00Z"), ZoneOffset.UTC);

    private MockRestServiceServer servidor;

    private LectorDeParametros lector() {
        RestClient.Builder constructor = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(constructor).ignoreExpectOrder(true).build();
        return LectorDeParametros.sobre(constructor.build(), CATALOGO, RELOJ, Duration.ofSeconds(30));
    }

    private void valor(String clave, String valor) {
        String json = valor == null
                ? "{\"clave\":\"" + clave + "\",\"valor\":null,\"tipo\":\"ENTERO\",\"version\":1}"
                : "{\"clave\":\"" + clave + "\",\"valor\":\"" + valor + "\",\"tipo\":\"ENTERO\",\"version\":2}";
        servidor.expect(manyTimes(), requestTo(CATALOGO + "/parametros/" + clave + "/valor"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("manda el catalogo: lo que ve y cambia la administracion es lo que se evalua")
    void mandaElCatalogo() {
        LectorDeParametros lector = lector();
        valor(UmbralesDeModeracion.SANCIONES, "5");
        valor(UmbralesDeModeracion.REPORTES, "20");

        UmbralesDeModeracion.Umbrales u = new UmbralesDeModeracion(lector, 3, null).vigentes();

        assertThat(u.sancionesPorDia()).isEqualTo(5);
        assertThat(u.reportesPorDia()).isEqualTo(20);
    }

    @Test
    @DisplayName("el catalogo sin valor (el PO no decidio) deja el respaldo; sin respaldo, no hay umbral")
    void sinValorEnElCatalogo() {
        LectorDeParametros lector = lector();
        valor(UmbralesDeModeracion.SANCIONES, null);
        valor(UmbralesDeModeracion.REPORTES, null);

        UmbralesDeModeracion.Umbrales u = new UmbralesDeModeracion(lector, 3, null).vigentes();

        assertThat(u.sancionesPorDia()).isEqualTo(3);
        assertThat(u.reportesPorDia()).as("D-25: sin cifra del PO no se inventa ninguna").isNull();
    }

    @Test
    @DisplayName("el catalogo caido degrada al respaldo, nunca a una excepcion")
    void catalogoCaido() {
        LectorDeParametros lector = lector();
        servidor.expect(manyTimes(), requestTo(org.hamcrest.Matchers.startsWith(CATALOGO)))
                .andRespond(withServerError());

        UmbralesDeModeracion.Umbrales u = new UmbralesDeModeracion(lector, 3, 40).vigentes();

        assertThat(u.sancionesPorDia()).isEqualTo(3);
        assertThat(u.reportesPorDia()).isEqualTo(40);
    }

    @Test
    @DisplayName("un valor que no es un entero degrada al respaldo; cero o negativo es «sin umbral»")
    void valoresRaros() {
        LectorDeParametros lector = lector();
        valor(UmbralesDeModeracion.SANCIONES, "tres");
        valor(UmbralesDeModeracion.REPORTES, "0");

        UmbralesDeModeracion.Umbrales u = new UmbralesDeModeracion(lector, 3, null).vigentes();

        assertThat(u.sancionesPorDia()).isEqualTo(3);
        assertThat(u.reportesPorDia()).isNull();
        assertThat(new UmbralesDeModeracion.Umbrales(-1, 0)).isEqualTo(UmbralesDeModeracion.Umbrales.NINGUNO);
    }

    @Test
    @DisplayName("sin catalogo configurado no se pregunta a nadie: todo sale del respaldo")
    void sinCatalogo() {
        UmbralesDeModeracion.Umbrales u =
                new UmbralesDeModeracion(LectorDeParametros.soloRespaldo(), null, null).vigentes();

        assertThat(u).isEqualTo(UmbralesDeModeracion.Umbrales.NINGUNO);
    }
}
