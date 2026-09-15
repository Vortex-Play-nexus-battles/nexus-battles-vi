package com.nexusbattles.ms_subastas.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClient;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClientFake;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClientHttp;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClientResiliente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InventarioClientConfig {

    private static final Logger log = LoggerFactory.getLogger(InventarioClientConfig.class);

    @Bean
    @ConditionalOnProperty(name = "app.inventario.modo", havingValue = "fake", matchIfMissing = true)
    public InventarioClient inventarioClientFake() {
        log.warn("ms-subastas arranca con el doble EN MEMORIA de inventario (app.inventario.modo=fake). "
                + "Las reservas y transferencias se simulan en memoria.");
        return new InventarioClientFake();
    }

    @Bean
    @ConditionalOnProperty(name = "app.inventario.modo", havingValue = "http")
    public InventarioClient inventarioClientHttp(
            @Value("${app.inventario.base-url:http://localhost:8080}") String baseUrl,
            @Value("${app.inventario.timeout-ms:5000}") long timeoutMs,
            ObjectMapper objectMapper) {
        log.info("ms-subastas arranca con el cliente HTTP real de inventario (app.inventario.modo=http): {}", baseUrl);
        // Envuelto en cortacircuitos: estas llamadas ocurren dentro del lock
        // pesimista de la subasta, asi que un inventario lento congela todas
        // las pujas de esa subasta hasta que se corte.
        return new InventarioClientResiliente(new InventarioClientHttp(baseUrl, timeoutMs, objectMapper));
    }
}
