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

    /**
     * HU-ADM-001 (CA-04): el rango de la suspension y el plazo de apelacion se
     * leen de admin-parametros por API; las variables de entorno quedan como
     * respaldo cuando no responde o el PO no ha fijado el valor.
     */
    @Bean
    public LimitesDeSancion limitesDeSancion(
            @Value("${sanciones.parametros.url:}") String urlDeParametros,
            @Value("${sanciones.suspension.minima-horas:1}") long minimaHoras,
            @Value("${sanciones.suspension.maxima-dias:30}") long maximaDias,
            @Value("${sanciones.apelacion.plazo-dias:30}") long plazoDias,
            @Value("${sanciones.parametros.cache-segundos:30}") long cacheSegundos,
            Clock reloj) {
        LimitesDeSancion respaldo = LimitesDeSancion.Fijos.de(minimaHoras, maximaDias, plazoDias);
        if (urlDeParametros == null || urlDeParametros.isBlank()) {
            return respaldo;
        }
        return new LimitesDesdeParametros(RestClient.builder().build(), urlDeParametros, respaldo, reloj,
                java.time.Duration.ofSeconds(cacheSegundos));
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
