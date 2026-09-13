package com.nexusbattles.plataforma.metricasplataforma.disponibilidad;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestClient;

/**
 * Cablea el monitor de disponibilidad (HU-DIS-001).
 *
 * <p>El dominio —registro, informe, umbral— no conoce Spring. Aqui solo se
 * decide quien es cada pieza en tiempo de ejecucion, de modo que las reglas
 * del calculo se prueben sin contexto y este archivo no tenga logica que
 * probar.
 */
@Configuration
@EnableConfigurationProperties(ConfiguracionDeDisponibilidad.class)
@EnableScheduling
public class ConfiguracionDelMonitor {

    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }

    @Bean
    RegistroDeDisponibilidad registroDeDisponibilidad() {
        return new RegistroDeDisponibilidad();
    }

    @Bean
    Alertas alertas() {
        return new AlertasEnBitacora();
    }

    /**
     * Cliente HTTP de la sonda, con tiempos de espera cortos.
     *
     * <p>Sin ellos, un servicio colgado —que acepta la conexion pero no
     * responde— bloquearia la ronda entera y dejaria de medirse el resto del
     * bloque, que es justo lo contrario de lo que pide CA-01. Dos segundos es
     * lo que ya usan los clientes HTTP del monorepo.
     */
    @Bean
    SondaDeSalud sondaDeSalud() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(2000);
        fabrica.setReadTimeout(2000);

        return new SondaDeActuator(RestClient.builder().requestFactory(fabrica).build());
    }

    @Bean
    MonitorDeDisponibilidad monitorDeDisponibilidad(
            ConfiguracionDeDisponibilidad configuracion,
            SondaDeSalud sonda,
            RegistroDeDisponibilidad registro,
            Alertas alertas,
            Clock reloj) {
        return new MonitorDeDisponibilidad(configuracion, sonda, registro, alertas, reloj);
    }

    @Bean
    RondaProgramada rondaProgramada(MonitorDeDisponibilidad monitor) {
        return new RondaProgramada(monitor);
    }

    /**
     * Dispara la ronda periodica.
     *
     * <p>Va en una clase aparte y no en el monitor para que el monitor siga
     * siendo una clase corriente que se puede instanciar en una prueba sin
     * que nada se ejecute solo.
     */
    static class RondaProgramada {

        private final MonitorDeDisponibilidad monitor;

        RondaProgramada(MonitorDeDisponibilidad monitor) {
            this.monitor = monitor;
        }

        @Scheduled(fixedRateString = "${disponibilidad.intervalo-ms}")
        void comprobar() {
            monitor.comprobarTodos();
        }
    }
}
