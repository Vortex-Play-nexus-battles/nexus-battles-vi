package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/**
 * Cableado de sanciones: reloj, cliente hacia notificaciones con credencial
 * de servicio (ADR-005) y el reintento programado de los avisos.
 */
@Configuration
@EnableScheduling
public class ConfiguracionDeSanciones {

    @Bean
    public Clock relojDeSanciones() {
        return Clock.systemUTC();
    }

    @Bean
    public EmisorDeAvisos emisorDeAvisos(
            @Value("${sanciones.notificaciones.url}") String urlDeNotificaciones,
            ObjectProvider<InterceptorDePortadorDeServicio> credencial) {
        RestClient.Builder constructor = RestClient.builder();
        credencial.ifAvailable(constructor::requestInterceptor);
        return new ClienteNotificaciones(constructor.build(), urlDeNotificaciones);
    }
}
