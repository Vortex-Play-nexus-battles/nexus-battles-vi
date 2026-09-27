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
public class ConfiguracionDeParametros {

    @Bean
    public Clock relojDeParametros() {
        return Clock.systemUTC();
    }

    /**
     * La auditoria de cada cambio (HU-AUD-001): ms-cumplimiento si hay URL, la
     * bitacora si no.
     *
     * <p>B12 — el cliente sale con tiempos de espera de conexion y de lectura.
     * La auditoria es fail-open, pero sin tiempo de lectura "no responde"
     * nunca llegaba: con ms-cumplimiento aceptando la conexion y sin contestar,
     * el cambio de un parametro se quedaba colgado dentro de la peticion del
     * administrador. 2 s para conectar (en la red de Docker se conecta en
     * milisegundos) y 5 s para responder, muy por debajo de los 60 s del borde.
     */
    @Bean
    public Auditoria auditoria(@Value("${parametros.auditoria.url:}") String url,
                               @Value("${parametros.auditoria.tiempo-conexion-ms:2000}") long conexionMs,
                               @Value("${parametros.auditoria.tiempo-respuesta-ms:5000}") long respuestaMs,
                               ObjectProvider<InterceptorDePortadorDeServicio> credencial) {
        if (url == null || url.isBlank()) {
            return new AuditoriaEnBitacora();
        }
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(conexionMs))
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(Duration.ofMillis(respuestaMs));
        RestClient.Builder constructor = RestClient.builder().requestFactory(fabrica);
        credencial.ifAvailable(constructor::requestInterceptor);
        return new ClienteAuditoria(constructor.build(), url);
    }
}
