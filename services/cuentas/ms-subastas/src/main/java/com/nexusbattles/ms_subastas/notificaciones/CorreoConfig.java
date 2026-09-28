package com.nexusbattles.ms_subastas.notificaciones;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbattles.comun.seguridad.servicio.TokenDeServicio;
import com.nexusbattles.ms_subastas.seguridad.PortadorDeServicio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * La salida por correo de los avisos de subastas (B8, 7.7.8).
 *
 * <p>Solo existe con {@code CORREO_URL} configurada. Sin ella —desarrollo
 * local, el banco E2E, que no levanta correo— los avisos van solo a la
 * bandeja y el outbox ni siquiera los marca para correo: no se acumula una cola
 * de correos que nunca van a salir.
 *
 * <p>Los dos clientes firman con la credencial de servicio de ms-subastas
 * (ADR-005): correo y las rutas internas de ms-identidad solo atienden a
 * {@code rol=SERVICIO}. Sin credencial configurada salen sin
 * {@code Authorization}, reciben 401 y el drenador lo reintenta y lo dice en
 * la bitacora; no se esconde.
 */
@Configuration
@ConditionalOnExpression("!'${app.correo.url:}'.isBlank()")
public class CorreoConfig {

    private static final Logger log = LoggerFactory.getLogger(CorreoConfig.class);

    @Bean
    public CorreoSubastaClient correoSubastaClient(@Value("${app.correo.url}") String url,
                                                   @Value("${app.correo.timeout-ms:3000}") long timeoutMs,
                                                   ObjectMapper mapper,
                                                   ObjectProvider<TokenDeServicio> tokenDeServicio) {
        log.info("Avisos de subastas tambien por correo: {}", url);
        return new CorreoSubastaClientHttp(url, clienteHttp(timeoutMs), mapper, Duration.ofMillis(timeoutMs),
                portadorDe(tokenDeServicio));
    }

    @Bean
    public ContactoClient contactoClient(@Value("${app.identidad.url:http://localhost:8089}") String url,
                                         @Value("${app.identidad.timeout-ms:3000}") long timeoutMs,
                                         ObjectMapper mapper,
                                         ObjectProvider<TokenDeServicio> tokenDeServicio) {
        return new ContactoClientHttp(url, clienteHttp(timeoutMs), mapper, Duration.ofMillis(timeoutMs),
                portadorDe(tokenDeServicio));
    }

    private static HttpClient clienteHttp(long timeoutMs) {
        return HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
    }

    private static PortadorDeServicio portadorDe(ObjectProvider<TokenDeServicio> proveedor) {
        TokenDeServicio token = proveedor.getIfAvailable();
        if (token == null) {
            log.warn("ms-subastas no tiene credencial de servicio (DIRECTORIO_ACTIVO_CLIENT_ID vacio): los correos "
                    + "de subastas saldran sin Authorization y correo los rechazara.");
            return PortadorDeServicio.ninguno();
        }
        return PortadorDeServicio.de(token);
    }
}
