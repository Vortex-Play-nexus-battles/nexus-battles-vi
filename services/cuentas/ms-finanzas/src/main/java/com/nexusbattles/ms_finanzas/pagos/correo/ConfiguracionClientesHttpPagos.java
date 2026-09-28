package com.nexusbattles.ms_finanzas.pagos.correo;

import com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
class ConfiguracionClientesHttpPagos {

    /**
     * Cliente hacia el servicio de correo (confirmacion de compra), con la
     * credencial de servicio si esta configurada.
     *
     * <p>B12 — con tiempos de espera de conexion y de lectura. El reintento y
     * el cortacircuitos de {@link ConfirmacionCompraClient} cuentan fallos, y
     * sin tiempo de lectura un correo que acepta la conexion y no contesta
     * nunca falla: el pago ya aprobado se quedaba esperando dentro de la
     * peticion del jugador. Por omision 2 s y 5 s; se pueden mover con
     * {@code app.correo.tiempo-*} o, como las demas llamadas salientes de
     * ms-finanzas, con FINANZAS_TIEMPO_CONEXION_MS / FINANZAS_TIEMPO_RESPUESTA_MS.
     */
    @Bean
    RestClient restClientCorreo(RestClient.Builder builder,
                                ObjectProvider<InterceptorDePortadorDeServicio> credencial,
                                @Value("${app.correo.tiempo-conexion-ms:${FINANZAS_TIEMPO_CONEXION_MS:2000}}") long conexionMs,
                                @Value("${app.correo.tiempo-respuesta-ms:${FINANZAS_TIEMPO_RESPUESTA_MS:5000}}") long respuestaMs) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(conexionMs))
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(Duration.ofMillis(respuestaMs));
        builder.requestFactory(fabrica);
        credencial.ifAvailable(builder::requestInterceptor);
        return builder.build();
    }
}
