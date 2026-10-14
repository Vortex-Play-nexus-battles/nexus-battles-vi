package com.nexusbattles.plataforma.adminparametros.parametros;

import com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

@Configuration
@org.springframework.scheduling.annotation.EnableScheduling
public class ConfiguracionDeParametros {

    @Bean
    public Clock relojDeParametros() {
        return Clock.systemUTC();
    }

    /** La linea de cada cambio en la bitacora JSON (regla 6). */
    @Bean
    public Auditoria auditoria() {
        return new AuditoriaEnBitacora();
    }

    /**
     * SA-5 (§7.3.5): la entrega de cada version a ms-cumplimiento si hay URL;
     * sin ella, las versiones esperan.
     *
     * <p>B12 — el cliente sale con tiempos de espera de conexion y de lectura:
     * con ms-cumplimiento aceptando la conexion y callado, la vuelta corta en
     * 2 s + 5 s y lo intenta despues, sin quedarse colgada.
     */
    @Bean
    public EnvioDeAuditoria envioDeAuditoria(VersionRepository versiones, Clock reloj,
                                             @Value("${parametros.auditoria.url:}") String url,
                                             @Value("${parametros.auditoria.tiempo-conexion-ms:2000}") long conexionMs,
                                             @Value("${parametros.auditoria.tiempo-respuesta-ms:5000}") long respuestaMs,
                                             @Value("${parametros.auditoria.reintento-ms:10000}") long reintentoMs,
                                             @Value("${parametros.auditoria.espera-maxima-ms:900000}") long esperaMaximaMs,
                                             ObjectProvider<InterceptorDePortadorDeServicio> credencial) {
        return new EnvioDeAuditoria(versiones, clienteAuditoria(url, conexionMs, respuestaMs, credencial), reloj,
                Duration.ofMillis(reintentoMs), Duration.ofMillis(esperaMaximaMs));
    }

    /** El cliente HTTP de la bitacora central, o nulo sin URL; tambien lo usan las politicas (SA-11). */
    public static ClienteAuditoria clienteAuditoria(String url, long conexionMs, long respuestaMs,
                                             ObjectProvider<InterceptorDePortadorDeServicio> credencial) {
        if (url == null || url.isBlank()) {
            return null;
        }
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(conexionMs))
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(Duration.ofMillis(respuestaMs));
        RestClient.Builder constructor = RestClient.builder().requestFactory(fabrica);
        credencial.ifAvailable(constructor::requestInterceptor);
        return new ClienteAuditoria(constructor.build(), url.strip());
    }
}
