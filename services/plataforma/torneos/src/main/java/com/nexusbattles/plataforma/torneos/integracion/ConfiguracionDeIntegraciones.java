package com.nexusbattles.plataforma.torneos.integracion;

import com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio;
import com.nexusbattles.plataforma.torneos.torneo.ConsultaDeSanciones;
import com.nexusbattles.plataforma.torneos.torneo.FiltroDeNombres;
import com.nexusbattles.plataforma.torneos.torneo.LibroDeCreditos;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/**
 * Los tres puertos de salida sobre RestClient. El libro y las sanciones van
 * con la credencial de servicio (ADR-005) cuando esta configurada; la lista
 * negra es publica dentro de la red.
 */
@Configuration
public class ConfiguracionDeIntegraciones {

    @Bean
    public Clock relojDeTorneos() {
        return Clock.systemUTC();
    }

    @Bean
    public LibroDeCreditos libroDeCreditos(@Value("${torneos.creditos.url}") String url,
                                           ObjectProvider<InterceptorDePortadorDeServicio> credencial) {
        return new ClienteCreditos(conCredencial(credencial), url);
    }

    @Bean
    public FiltroDeNombres filtroDeNombres(@Value("${torneos.lista-negra.url}") String url) {
        return new ClienteListaNegra(RestClient.builder().build(), url);
    }

    @Bean
    public ConsultaDeSanciones consultaDeSanciones(@Value("${torneos.sanciones.url}") String url,
                                                  ObjectProvider<InterceptorDePortadorDeServicio> credencial) {
        return new ClienteSanciones(conCredencial(credencial), url);
    }

    private static RestClient conCredencial(ObjectProvider<InterceptorDePortadorDeServicio> credencial) {
        RestClient.Builder constructor = RestClient.builder();
        credencial.ifAvailable(constructor::requestInterceptor);
        return constructor.build();
    }
}
