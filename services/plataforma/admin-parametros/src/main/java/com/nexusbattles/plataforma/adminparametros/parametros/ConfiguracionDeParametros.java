package com.nexusbattles.plataforma.adminparametros.parametros;

import com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration
public class ConfiguracionDeParametros {

    @Bean
    public Clock relojDeParametros() {
        return Clock.systemUTC();
    }

    @Bean
    public Auditoria auditoria(@Value("${parametros.auditoria.url:}") String url,
                               ObjectProvider<InterceptorDePortadorDeServicio> credencial) {
        if (url == null || url.isBlank()) {
            return new AuditoriaEnBitacora();
        }
        RestClient.Builder constructor = RestClient.builder();
        credencial.ifAvailable(constructor::requestInterceptor);
        return new ClienteAuditoria(constructor.build(), url);
    }
}
