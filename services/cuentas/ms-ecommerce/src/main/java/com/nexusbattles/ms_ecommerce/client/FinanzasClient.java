package com.nexusbattles.ms_ecommerce.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.Map;

@FeignClient(name = "ms-finanzas")
public interface FinanzasClient {

    @PostMapping("/api/v1/finanzas/pagos")
    ResponseEntity<String> procesarPago(@RequestBody Map<String, Object> solicitudPago);
}
