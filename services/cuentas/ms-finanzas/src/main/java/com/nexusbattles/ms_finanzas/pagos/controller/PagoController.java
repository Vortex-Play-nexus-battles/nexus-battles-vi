package com.nexusbattles.ms_finanzas.pagos.controller;

import com.nexusbattles.ms_finanzas.pagos.dto.PagoDTOs.ProcesarPagoRequest;
import com.nexusbattles.ms_finanzas.pagos.dto.PagoDTOs.ProcesarPagoResponse;
import com.nexusbattles.ms_finanzas.pagos.service.PagoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de HU-PAG-001: procesa un pago en dinero real contra la pasarela
 * simulada. Lo llaman los módulos que necesitan cobrar dinero real
 * (HU-CAR-010 de ms-ecommerce, y cualquier otro flujo de compra real).
 */
@RestController
@RequestMapping("/pagos")
public class PagoController {

    private final PagoService pagoService;

    public PagoController(PagoService pagoService) {
        this.pagoService = pagoService;
    }

    @PostMapping("/procesar")
    public ResponseEntity<ProcesarPagoResponse> procesar(@RequestBody ProcesarPagoRequest request) {
        ProcesarPagoResponse respuesta = pagoService.procesar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }
}
