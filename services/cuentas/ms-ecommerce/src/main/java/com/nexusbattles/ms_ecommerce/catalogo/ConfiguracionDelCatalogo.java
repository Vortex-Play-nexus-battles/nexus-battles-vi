package com.nexusbattles.ms_ecommerce.catalogo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/**
 * Cliente HTTP hacia el catalogo maestro y reloj del servicio.
 *
 * <p>Se usa el {@link RestClient} de spring-web tal cual: este modulo sigue en
 * Maven y no trae el constructor autoconfigurado de Boot, y no hace falta
 * ninguna dependencia mas para lo que se necesita — una URL base y dos tiempos
 * de espera. Misma fabrica ({@link SimpleClientHttpRequestFactory}) y mismos
 * valores que usan los clientes de metricas-plataforma.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesDelCatalogo.class)
public class ConfiguracionDelCatalogo {

    /** Nombre del bean del cliente, por si el servicio llega a tener otro RestClient. */
    public static final String CLIENTE = "clienteDelCatalogoMaestro";

    @Bean(CLIENTE)
    RestClient clienteDelCatalogoMaestro(PropiedadesDelCatalogo propiedades) {
        return constructorDelCliente(propiedades).build();
    }

    /**
     * Reloj de la instancia. Se inyecta en lugar de leer la hora del sistema
     * para que la vigencia de la copia del catalogo se pueda probar sin
     * esperar de verdad.
     */
    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }

    /**
     * El constructor con URL base y tiempos de espera, sin construir. Las
     * pruebas lo reutilizan para que el cliente que prueban sea exactamente
     * el que se despliega.
     */
    static RestClient.Builder constructorDelCliente(PropiedadesDelCatalogo propiedades) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(propiedades.timeoutConexion());
        fabrica.setReadTimeout(propiedades.timeoutLectura());
        return RestClient.builder()
                .baseUrl(propiedades.url())
                .requestFactory(fabrica);
    }
}
