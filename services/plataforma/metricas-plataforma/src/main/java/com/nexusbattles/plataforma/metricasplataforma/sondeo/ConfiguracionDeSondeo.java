package com.nexusbattles.plataforma.metricasplataforma.sondeo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cablea el sondeo interactivo del panel (RFINAL-08): una sola ronda acotada
 * para las dos pantallas que preguntan en vivo («Sistema» y el tablero
 * tecnico).
 *
 * <p>La ronda se publica como {@link RondaEnParalelo} y no como un
 * {@code ExecutorService}: un bean de tipo {@code Executor} haria que Spring
 * Boot retirara su ejecutor de tareas por defecto, y nada de este cambio
 * deberia tocar eso. Al cerrar el contexto, Spring llama a
 * {@link RondaEnParalelo#close()} y el ejecutor se apaga.
 */
@Configuration
@EnableConfigurationProperties(ConfiguracionDelSondeo.class)
public class ConfiguracionDeSondeo {

    @Bean
    RondaEnParalelo rondaEnParalelo(ConfiguracionDelSondeo sondeo) {
        return RondaEnParalelo.acotada(sondeo.hilos(), sondeo.plazoDeLaRonda());
    }
}
