package com.nexusbattles.plataforma.metricasplataforma.disponibilidad;

import java.time.Clock;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Cablea el monitor de disponibilidad (HU-DIS-001).
 *
 * <p>El dominio —registro, informe, umbral— no conoce Spring. Aqui solo se
 * decide quien es cada pieza en tiempo de ejecucion, de modo que las reglas
 * del calculo se prueben sin contexto y este archivo no tenga logica que
 * probar.
 */
@Configuration
@EnableConfigurationProperties({ConfiguracionDeDisponibilidad.class,
        com.nexusbattles.plataforma.metricasplataforma.sistema.ConfiguracionDelSistema.class})
@EnableScheduling
public class ConfiguracionDelMonitor {

    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }

    /**
     * El almacen es PostgreSQL (esquema {@code metricas}, migrado por Flyway
     * al arrancar): las interrupciones sobreviven a los redespliegues, que
     * es cuando mas caidas hay que contar.
     */
    @Bean
    AlmacenDeDisponibilidad almacenDeDisponibilidad(JdbcClient jdbc) {
        return new AlmacenEnPostgres(jdbc);
    }

    @Bean
    RegistroDeDisponibilidad registroDeDisponibilidad(AlmacenDeDisponibilidad almacen) {
        return new RegistroDeDisponibilidad(almacen);
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
     *
     * <p>Estos plazos son los del monitor y no cambian con RFINAL-08: la
     * pantalla «Sistema» y el tablero tecnico tienen los suyos (sondeo.* en
     * application.yml), para que acortar la espera de una pantalla no mueva
     * la cifra de disponibilidad.
     */
    @Bean
    SondaDeSalud sondaDeSalud() {
        return SondaDeActuator.conPlazos(Duration.ofMillis(2000), Duration.ofMillis(2000));
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
