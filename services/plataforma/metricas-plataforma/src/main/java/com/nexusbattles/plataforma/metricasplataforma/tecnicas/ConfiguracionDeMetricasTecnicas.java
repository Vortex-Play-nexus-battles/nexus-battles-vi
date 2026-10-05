package com.nexusbattles.plataforma.metricasplataforma.tecnicas;

import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.ConfiguracionDeDisponibilidad;
import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.MonitorDeDisponibilidad;
import com.nexusbattles.plataforma.metricasplataforma.moderacion.ClienteDeModeracion;
import com.nexusbattles.plataforma.metricasplataforma.moderacion.FuenteDeModeracion;
import com.nexusbattles.plataforma.metricasplataforma.sondeo.ConfiguracionDelSondeo;
import com.nexusbattles.plataforma.metricasplataforma.sondeo.ResultadoReciente;
import com.nexusbattles.plataforma.metricasplataforma.sondeo.RondaEnParalelo;
import com.nexusbattles.plataforma.observabilidad.PropiedadesDeLatencia;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;

/** Recolector de Actuator, tablero en vivo y fuente de moderacion, con tiempos cortos: un tablero no espera a nadie. */
@Configuration
public class ConfiguracionDeMetricasTecnicas {

    private static RestClient conTiempos(Duration conexion, Duration respuesta) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(conexion);
        fabrica.setReadTimeout(respuesta);
        return RestClient.builder().requestFactory(fabrica).build();
    }

    /**
     * Las lecturas de Actuator del tablero usan los plazos del sondeo
     * interactivo (RFINAL-08, {@code sondeo.*}): es una peticion de pantalla.
     */
    @Bean
    RecolectorDeMetricas recolectorDeMetricas(ConfiguracionDelSondeo sondeo) {
        return new RecolectorDeActuator(conTiempos(sondeo.plazoDeConexion(), sondeo.plazoDeRespuesta()));
    }

    @Bean
    TableroEnVivo tableroEnVivo(ConfiguracionDeDisponibilidad configuracion, RecolectorDeMetricas recolector,
                                MonitorDeDisponibilidad monitor, PropiedadesDeLatencia latencia,
                                RondaEnParalelo ronda, ConfiguracionDelSondeo sondeo, Clock reloj) {
        return new TableroEnVivo(configuracion, recolector, monitor, latencia, ronda,
                new ResultadoReciente<>(sondeo.vigencia()), reloj);
    }

    /** Sin cambios en RFINAL-08: es una sola llamada y no forma parte de la ronda. */
    @Bean
    FuenteDeModeracion fuenteDeModeracion(@Value("${metricas.moderacion.url}") String url) {
        return new ClienteDeModeracion(conTiempos(Duration.ofMillis(2000), Duration.ofMillis(3000)), url);
    }
}
