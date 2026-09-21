package com.nexusbattles.plataforma.metricasplataforma.tecnicas;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Las cuatro lecturas de Actuator por servicio y la brecha cuando no responde. */
@DisplayName("Recolector de Actuator (HU-MET-004)")
class RecolectorDeActuatorTest {

    private final RestClient.Builder constructor = RestClient.builder();
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
    private final RecolectorDeActuator recolector = new RecolectorDeActuator(constructor.build());

    @Test
    @DisplayName("deriva /actuator de la URL de salud y traduce COUNT, TOTAL_TIME, MAX, cpu y memoria")
    void recolecta() {
        assertThat(RecolectorDeActuator.baseDe("http://srv-x:8084/actuator/health")).isEqualTo("http://srv-x:8084/actuator");
        assertThat(RecolectorDeActuator.baseDe("http://srv-x:8084/")).isEqualTo("http://srv-x:8084/actuator");
        servidor.expect(requestTo("http://srv-x:8084/actuator/metrics/http.server.requests"))
                .andRespond(withSuccess("{\"name\":\"http.server.requests\",\"measurements\":[{\"statistic\":\"COUNT\",\"value\":200.0},{\"statistic\":\"TOTAL_TIME\",\"value\":8.0},{\"statistic\":\"MAX\",\"value\":0.75}]}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://srv-x:8084/actuator/metrics/http.server.requests?tag=outcome:SERVER_ERROR"))
                .andRespond(withSuccess("{\"name\":\"http.server.requests\",\"measurements\":[{\"statistic\":\"COUNT\",\"value\":4.0}]}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://srv-x:8084/actuator/metrics/process.cpu.usage"))
                .andRespond(withSuccess("{\"name\":\"process.cpu.usage\",\"measurements\":[{\"statistic\":\"VALUE\",\"value\":0.42}]}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://srv-x:8084/actuator/metrics/jvm.memory.used"))
                .andRespond(withSuccess("{\"name\":\"jvm.memory.used\",\"measurements\":[{\"statistic\":\"VALUE\",\"value\":209715200}]}", MediaType.APPLICATION_JSON));

        MetricasDeServicio m = recolector.recolectar("salas-partidas", "http://srv-x:8084/actuator/health");
        assertThat(m.recolectado()).isTrue();
        assertThat(m.peticiones()).isEqualTo(200);
        assertThat(m.promedioMs()).isEqualTo(40d);
        assertThat(m.maximoMs()).isEqualTo(750d);
        assertThat(m.errores5xx()).isEqualTo(4);
        assertThat(m.cpu()).isEqualTo(0.42);
        assertThat(m.memoriaMb()).isEqualTo(200d);
        servidor.verify();
    }

    @Test
    @DisplayName("una metrica que Actuator no conoce (404) es ausencia; un servicio caido es brecha")
    void ausenciaYBrecha() {
        servidor.expect(requestTo("http://srv-y:8081/actuator/metrics/http.server.requests"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        servidor.expect(requestTo("http://srv-y:8081/actuator/metrics/http.server.requests?tag=outcome:SERVER_ERROR"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        servidor.expect(requestTo("http://srv-y:8081/actuator/metrics/process.cpu.usage"))
                .andRespond(withSuccess("{\"name\":\"process.cpu.usage\",\"measurements\":[{\"statistic\":\"VALUE\",\"value\":0.1}]}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://srv-y:8081/actuator/metrics/jvm.memory.used"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        servidor.expect(requestTo("http://srv-z:8082/actuator/metrics/http.server.requests"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        MetricasDeServicio sinTrafico = recolector.recolectar("comentarios", "http://srv-y:8081/actuator/health");
        assertThat(sinTrafico.recolectado()).isTrue();
        assertThat(sinTrafico.peticiones()).isZero();
        assertThat(sinTrafico.promedioMs()).isNull();
        assertThat(sinTrafico.cpu()).isEqualTo(0.1);
        assertThat(sinTrafico.memoriaMb()).isNull();

        MetricasDeServicio caido = recolector.recolectar("correo", "http://srv-z:8082/actuator/health");
        assertThat(caido.recolectado()).isFalse();
        assertThat(caido.brecha()).contains("503");
        servidor.verify();
    }
}
