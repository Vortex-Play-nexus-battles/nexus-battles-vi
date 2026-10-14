package com.nexusbattles.plataforma.moderacionsanciones.auditoria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;

/**
 * La bitacora central (§7.3.5, SA-5): la cola de asientos y su entrega a
 * ms-cumplimiento con el cliente de las salidas (credencial de servicio,
 * traza y tiempos de espera).
 */
@Configuration
public class ConfiguracionDeAuditoria {

    private static final Logger BITACORA = LoggerFactory.getLogger(ConfiguracionDeAuditoria.class);

    @Bean
    public BitacoraDeAuditoria bitacoraDeAuditoria(AsientoDeAuditoriaRepository asientos, Clock reloj) {
        return new BitacoraEnCola(asientos, reloj);
    }

    @Bean
    public EntregaDeAuditoria entregaDeAuditoria(
            AsientoDeAuditoriaRepository asientos,
            RestClient restClientDeSalidas,
            Clock reloj,
            @Value("${auditoria.url:}") String url,
            @Value("${auditoria.reintento-ms:10000}") long reintentoMs,
            @Value("${auditoria.espera-maxima-ms:900000}") long esperaMaximaMs) {
        ClienteDeCumplimiento cliente = null;
        if (url == null || url.isBlank()) {
            BITACORA.warn("Sin AUDITORIA_URL: las acciones administrativas quedan en la cola de auditoria y en "
                    + "la bitacora JSON hasta que se configure");
        } else {
            cliente = new ClienteDeCumplimiento(restClientDeSalidas, url.strip());
        }
        return new EntregaDeAuditoria(asientos, cliente, reloj, Duration.ofMillis(reintentoMs),
                Duration.ofMillis(esperaMaximaMs));
    }
}
