package com.nexusbattles.ms_ecommerce.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Cliente de la pasarela simulada de ms-finanzas (HU-PAG-001).
 *
 * <p>La {@code url} es obligatoria: este servicio no tiene
 * spring-cloud-starter-loadbalancer, y sin url Feign no sabe resolver
 * {@code ms-finanzas} y el contexto no arranca (lo detecta
 * ArranqueDeLaAplicacionIT). La dirección real se configura con
 * {@code finanzas.url} o la variable {@code FINANZAS_URL}; el valor por
 * omisión solo sirve para que el contexto levante en pruebas, porque crear el
 * cliente no hace ninguna llamada de red.
 */
@FeignClient(name = "ms-finanzas", url = "${finanzas.url:http://localhost:8080}")
public interface FinanzasClient {

    @PostMapping("/api/v1/finanzas/pagos")
    ResponseEntity<String> procesarPago(@RequestBody Map<String, Object> solicitudPago);
}
