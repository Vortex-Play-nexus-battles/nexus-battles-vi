package com.nexusbattles.ms_ecommerce.controller;

import com.nexusbattles.ms_ecommerce.dto.CheckoutRequestDTO;
import com.nexusbattles.ms_ecommerce.service.CheckoutService;
import com.nexusbattles.ms_ecommerce.service.CheckoutService.CarritoNoDisponibleException;
import com.nexusbattles.ms_ecommerce.service.CheckoutService.PasarelaNoDisponibleException;
import com.nexusbattles.ms_ecommerce.service.CheckoutService.ResultadoCompra;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Resumen de compra y pago con tarjeta — HU-CAR-010.
 *
 * <p>Ruta alineada con el resto del servicio ({@code /api/v1/carrito},
 * {@code /api/v1/productos}): el frontend llama a {@code /api/v1/checkout}.
 *
 * <p>La identidad sale del JWT (ADR-002), no de una cabecera {@code X-User-Id}
 * que el navegador puede escribir a su gusto.
 *
 * <p>Contrato de respuesta:
 * <ul>
 *   <li>200 {@code {aprobado: true,  estado: "APROBADO",  mensaje}}: pago hecho, carrito vaciado.</li>
 *   <li>200 {@code {aprobado: false, estado: "RECHAZADO", mensaje}}: la pasarela dijo que no;
 *       el carrito sigue intacto.</li>
 *   <li>409: carrito inexistente, ajeno o vacío.</li>
 *   <li>503: ms-finanzas no respondió; no se cobró nada.</li>
 *   <li>400: el formato de los datos no pasó {@code @Valid}.</li>
 * </ul>
 * Un rechazo es un resultado legítimo del pago y no un error del servidor, por
 * eso va con 200: el jugador ve el motivo en vez de un mensaje genérico.
 */
@RestController
@RequestMapping("/api/v1/checkout")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> procesarCheckout(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CheckoutRequestDTO request) {

        String usuarioId = jwt == null ? null : jwt.getClaimAsString("uid");
        if (usuarioId == null || usuarioId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("mensaje", "Tu sesión expiró. Inicia sesión de nuevo."));
        }

        ResultadoCompra resultado = checkoutService.ejecutarCompra(usuarioId, request);
        return ResponseEntity.ok(Map.of(
            "aprobado", resultado.aprobado(),
            "estado", resultado.aprobado() ? "APROBADO" : "RECHAZADO",
            "mensaje", resultado.mensaje()));
    }

    @ExceptionHandler(CarritoNoDisponibleException.class)
    ResponseEntity<Map<String, Object>> carritoNoDisponible(CarritoNoDisponibleException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensaje", e.getMessage()));
    }

    @ExceptionHandler(PasarelaNoDisponibleException.class)
    ResponseEntity<Map<String, Object>> pasarelaNoDisponible(PasarelaNoDisponibleException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("mensaje", e.getMessage()));
    }
}
