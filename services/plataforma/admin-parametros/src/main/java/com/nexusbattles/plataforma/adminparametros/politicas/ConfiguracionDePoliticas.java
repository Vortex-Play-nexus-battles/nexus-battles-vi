package com.nexusbattles.plataforma.adminparametros.politicas;

import com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio;
import com.nexusbattles.plataforma.adminparametros.parametros.ConfiguracionDeParametros;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * SA-11: la entrega de los cambios en las politicas de uso a la bitacora
 * central, con la misma URL, los mismos tiempos y la misma credencial de
 * servicio que los cambios de parametros (SA-5).
 */
@Configuration
public class ConfiguracionDePoliticas {

    @Bean
    public EnvioDeAuditoriaDePoliticas envioDeAuditoriaDePoliticas(
            CambioDePoliticaRepository cambios, Clock reloj,
            @Value("${parametros.auditoria.url:}") String url,
            @Value("${parametros.auditoria.tiempo-conexion-ms:2000}") long conexionMs,
            @Value("${parametros.auditoria.tiempo-respuesta-ms:5000}") long respuestaMs,
            @Value("${parametros.auditoria.reintento-ms:10000}") long reintentoMs,
            @Value("${parametros.auditoria.espera-maxima-ms:900000}") long esperaMaximaMs,
            ObjectProvider<InterceptorDePortadorDeServicio> credencial) {
        return new EnvioDeAuditoriaDePoliticas(cambios,
                ConfiguracionDeParametros.clienteAuditoria(url, conexionMs, respuestaMs, credencial), reloj,
                Duration.ofMillis(reintentoMs), Duration.ofMillis(esperaMaximaMs));
    }
}
