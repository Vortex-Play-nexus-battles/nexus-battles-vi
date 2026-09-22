package com.nexusbattles.plataforma.metricasplataforma.tecnicas;

import com.nexusbattles.plataforma.metricasplataforma.moderacion.ClienteDeModeracion;
import com.nexusbattles.plataforma.metricasplataforma.moderacion.FuenteDeModeracion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Recolector de Actuator y fuente de moderacion, con tiempos cortos: un tablero no espera a nadie. */
@Configuration
public class ConfiguracionDeMetricasTecnicas {

    private static RestClient conTiempos() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(2000);
        fabrica.setReadTimeout(3000);
        return RestClient.builder().requestFactory(fabrica).build();
    }

    @Bean
    RecolectorDeMetricas recolectorDeMetricas() {
        return new RecolectorDeActuator(conTiempos());
    }

    @Bean
    FuenteDeModeracion fuenteDeModeracion(@Value("${metricas.moderacion.url}") String url) {
        return new ClienteDeModeracion(conTiempos(), url);
    }
}
