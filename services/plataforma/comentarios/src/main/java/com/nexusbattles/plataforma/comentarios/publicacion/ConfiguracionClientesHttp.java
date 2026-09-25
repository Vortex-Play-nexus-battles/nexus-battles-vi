package com.nexusbattles.plataforma.comentarios.publicacion;

import java.net.http.HttpClient;
import java.time.Duration;

import com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Clientes HTTP del servicio de comentarios.
 *
 * <p>El builder lo entrega Spring Boot ya configurado —con el interceptor que
 * propaga el trace id (regla 5)— y es de ambito prototipo: cada cliente de
 * aqui recibe el suyo, asi que lo que se le anade a uno no se le pega al otro.
 *
 * <p>Si el servicio tiene credencial propia configurada
 * ({@code DIRECTORIO_ACTIVO_*}, ADR-001/ADR-005), la lleva en cada llamada a
 * moderacion-sanciones y notificaciones; si no, llama como hasta ahora. Asi la
 * verificacion de lista negra puede cerrarse a tokens de servicio cuando su
 * dueno lo decida, sin tocar este servicio.
 *
 * <h2>Tiempos acotados (B3)</h2>
 *
 * <p>Hasta B3 ninguno de estos clientes tenia tiempo de lectura: un servicio
 * de sanciones o de lista negra colgado dejaba la publicacion esperando para
 * siempre, y el respaldo previsto (retener en revision, 503) no llegaba a
 * actuar nunca. Ahora cada cliente corta: el de plataforma a 2 s de conexion
 * y 5 s de lectura por omision, y el del catalogo mas corto, porque vive en
 * otro host y se le pregunta en cada escritura.
 */
@Configuration
class ConfiguracionClientesHttp {

    /**
     * El de siempre (lista negra, sanciones, notificaciones, auditoria).
     * {@code @Primary} porque desde B3 hay dos {@link RestClient}: quien no
     * pide uno por nombre recibe este, como antes de que existiera el otro.
     */
    @Bean
    @Primary
    RestClient restClientComentarios(RestClient.Builder builder,
                                     ObjectProvider<InterceptorDePortadorDeServicio> credencial,
                                     @Value("${comentarios.http.timeout-conexion:2s}") Duration conexion,
                                     @Value("${comentarios.http.timeout-lectura:5s}") Duration lectura) {
        credencial.ifAvailable(builder::requestInterceptor);
        return builder.requestFactory(fabricaCon(conexion, lectura)).build();
    }

    /**
     * El catalogo de productos (B3): {@code GET {PRODUCTOS_URL}/api/v1/productos/{id}}.
     * Sin credencial de servicio: la ruta es publica, y un token que el host de
     * contenido no supiera verificar la convertiria en un 401.
     */
    @Bean
    RestClient restClientProductos(RestClient.Builder builder,
                                   @Value("${comentarios.productos.url}") String base,
                                   @Value("${comentarios.productos.timeout-conexion:1s}") Duration conexion,
                                   @Value("${comentarios.productos.timeout-lectura:2s}") Duration lectura) {
        return builder
                .baseUrl(base.replaceAll("/+$", ""))
                .requestFactory(fabricaCon(conexion, lectura))
                .build();
    }

    /**
     * HTTP/1.1 explicito: con la preferencia por omision del cliente del JDK
     * (HTTP/2) cada primera peticion en claro intenta un {@code Upgrade: h2c}
     * que los servicios y el borde no hablan.
     */
    static ClientHttpRequestFactory fabricaCon(Duration conexion, Duration lectura) {
        HttpClient cliente = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(conexion)
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(cliente);
        fabrica.setReadTimeout(lectura);
        return fabrica;
    }
}
