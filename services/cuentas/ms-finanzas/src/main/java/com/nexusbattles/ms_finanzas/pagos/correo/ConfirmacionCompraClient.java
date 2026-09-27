package com.nexusbattles.ms_finanzas.pagos.correo;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ConfirmacionCompraClient {

    private static final Logger log = LoggerFactory.getLogger(ConfirmacionCompraClient.class);

    private final RestClient restClient;

    @Value("${app.correo.url-confirmacion-compra}")
    private String urlConfirmacionCompra;

    public ConfirmacionCompraClient(RestClient restClientCorreo) {
        this.restClient = restClientCorreo;
    }

    @Retry(name = "correo", fallbackMethod = "enviarConfirmacionCompraConFallback")
    @CircuitBreaker(name = "correo")
    public void enviarConfirmacionCompra(ConfirmacionCompraRequest datos) {
        restClient.post()
            .uri(urlConfirmacionCompra)
            .body(datos)
            .retrieve()
            .toBodilessEntity();
    }

    private void enviarConfirmacionCompraConFallback(ConfirmacionCompraRequest datos, Throwable ex) {
        log.warn("Servicio de correo no disponible, no se pudo enviar la confirmación de compra a '{}'. " +
            "El pago ya quedó aprobado y NO se revierte. Motivo: {}", datos.email(), ex.getMessage());
    }
}
