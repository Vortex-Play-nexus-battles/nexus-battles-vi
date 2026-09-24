package com.nexusbattles.ms_ecommerce.controller;

import com.nexusbattles.ms_ecommerce.dto.AgregarItemRequest;
import com.nexusbattles.ms_ecommerce.dto.CarritoDto;
import com.nexusbattles.ms_ecommerce.seguridad.ConversorDeRoles;
import com.nexusbattles.ms_ecommerce.service.CarritoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Carrito de compras del jugador.
 *
 * <p>El carrito es del {@code uid} del token de acceso (ADR-002), no de la
 * cabecera {@code X-User-Id}: esa cabecera la escribia el navegador y
 * cualquiera podia poner el identificador de otro. La cadena de seguridad
 * ({@code SeguridadConfig}) garantiza que aqui llega un usuario autenticado.
 *
 * <p>Los productos son los del catalogo maestro (UUID del servicio productos)
 * y las respuestas son {@link CarritoDto}, nunca la entidad. Un producto que
 * no se puede agregar sale como problem details (422/409) y un catalogo que no
 * responde como 503 (ver {@code ManejadorDeErrores}).
 */
@RestController
@RequestMapping("/api/v1/carrito")
@RequiredArgsConstructor
public class CarritoController {

    private final CarritoService carritoService;

    @GetMapping
    public ResponseEntity<CarritoDto> obtenerCarrito(@AuthenticationPrincipal Jwt usuario) {
        return ResponseEntity.ok(carritoService.obtenerOCrearCarrito(ConversorDeRoles.identificadorDe(usuario)));
    }

    @PostMapping("/items")
    public ResponseEntity<CarritoDto> agregarItem(
        @AuthenticationPrincipal Jwt usuario,
        @Valid @RequestBody AgregarItemRequest request) {
        return ResponseEntity.ok(carritoService.agregarProducto(ConversorDeRoles.identificadorDe(usuario), request));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<CarritoDto> eliminarItem(
        @AuthenticationPrincipal Jwt usuario,
        @PathVariable Long itemId) {
        return ResponseEntity.ok(carritoService.eliminarItem(ConversorDeRoles.identificadorDe(usuario), itemId));
    }
}
