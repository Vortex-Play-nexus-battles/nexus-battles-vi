package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio;
import com.nexusbattles.plataforma.resiliencia.parametros.LectorDeParametros;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;

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
     * El lector del catalogo de parametros (HU-ADM-001).
     *
     * <p>Se construye SIEMPRE, tambien cuando {@code PARAMETROS_URL} viene
     * vacia: en ese caso {@link LectorDeParametros#desde} devuelve un lector
     * sin catalogo que no hace ni una peticion y sirve solo respaldos. Antes
     * este cableado tenia el {@code if} aqui y devolvia otro objeto distinto
     * segun el entorno, con lo que el camino «sin catalogo» no pasaba por el
     * mismo codigo que el de produccion y no lo cubria ninguna prueba.
     */
    @Bean
    public LectorDeParametros lectorDeParametros(
            @Value("${sanciones.parametros.url:}") String urlDeParametros,
            @Value("${sanciones.parametros.cache-segundos:30}") long cacheSegundos,
            Clock reloj) {
        return LectorDeParametros.desde(RestClient.builder().build(), urlDeParametros, reloj,
                Duration.ofSeconds(cacheSegundos));
    }

    /**
     * HU-ADM-001 (CA-04): el rango de la suspension y el plazo de apelacion se
     * leen de admin-parametros por API; las variables de entorno quedan como
     * respaldo cuando no responde o el PO no ha fijado el valor.
     */
    @Bean
    public LimitesDeSancion limitesDeSancion(
            LectorDeParametros parametros,
            @Value("${sanciones.suspension.minima-horas:1}") long minimaHoras,
            @Value("${sanciones.suspension.maxima-dias:30}") long maximaDias,
            @Value("${sanciones.apelacion.plazo-dias:30}") long plazoDias) {
        return new LimitesDesdeParametros(parametros,
                LimitesDeSancion.Fijos.de(minimaHoras, maximaDias, plazoDias));
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
