package com.nexusbattles.ms_finanzas.creditos.controller;

import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.*;
import com.nexusbattles.ms_finanzas.creditos.service.CreditoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/creditos")
public class CreditoController {

    private final CreditoService creditoService;

    public CreditoController(CreditoService creditoService) {
        this.creditoService = creditoService;
    }

    @GetMapping("/{uid}/saldo")
    public ResponseEntity<SaldoResponse> obtenerSaldo(@PathVariable("uid") String uid) {
        return ResponseEntity.ok(creditoService.obtenerSaldo(uid));
    }

    @PostMapping("/reservar")
    public ResponseEntity<ReservaResponse> reservar(
        @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
        @RequestBody ReservarRequest req) {
        ReservaResponse respuesta = creditoService.reservar(req, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @PostMapping("/reservas/{reservaId}/liberar")
    public ResponseEntity<ReservaResponse> liberar(@PathVariable("reservaId") UUID reservaId) {
        return ResponseEntity.ok(creditoService.liberar(reservaId));
    }

    @PostMapping("/reservas/{reservaId}/consumir")
    public ResponseEntity<ConsumirResponse> consumir(
        @PathVariable("reservaId") UUID reservaId,
        @RequestBody ConsumirRequest req) {
        return ResponseEntity.ok(creditoService.consumir(reservaId, req));
    }

    @PostMapping("/debitar")
    public ResponseEntity<DebitarResponse> debitar(@RequestBody DebitarRequest req) {
        return ResponseEntity.ok(creditoService.debitar(req));
    }

    @PostMapping("/reversar")
    public ResponseEntity<ReversarResponse> reversar(@RequestBody ReversarRequest req) {
        return ResponseEntity.ok(creditoService.reversar(req));
    }

    @GetMapping("/operaciones/{refId}")
    public ResponseEntity<OperacionResponse> consultarOperacion(@PathVariable("refId") String refId) {
        return ResponseEntity.ok(creditoService.consultarOperacionPorRefId(refId));
    }

    @PostMapping("/acreditar")
    public ResponseEntity<AcreditarResponse> acreditar(@RequestBody AcreditarRequest req) {
        return ResponseEntity.ok(creditoService.acreditar(req));
    }
}
