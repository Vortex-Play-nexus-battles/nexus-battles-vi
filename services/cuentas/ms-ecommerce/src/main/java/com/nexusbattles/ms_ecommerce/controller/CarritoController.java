package com.nexusbattles.ms_ecommerce.controller;

import com.nexusbattles.ms_ecommerce.dto.AgregarItemRequest;
import com.nexusbattles.ms_ecommerce.model.Carrito;
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
 */
@RestController
@RequestMapping("/api/v1/carrito")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:8080", "http://127.0.0.1:8080"})
public class CarritoController {

    private final CarritoService carritoService;

    @GetMapping
    public ResponseEntity<Carrito> obtenerCarrito(@AuthenticationPrincipal Jwt usuario) {
        return ResponseEntity.ok(carritoService.obtenerOCrearCarrito(ConversorDeRoles.identificadorDe(usuario)));
    }

    @PostMapping("/items")
    public ResponseEntity<Carrito> agregarItem(
        @AuthenticationPrincipal Jwt usuario,
        @Valid @RequestBody AgregarItemRequest request) {
        return ResponseEntity.ok(carritoService.agregarProducto(ConversorDeRoles.identificadorDe(usuario), request));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Carrito> eliminarItem(
        @AuthenticationPrincipal Jwt usuario,
        @PathVariable Long itemId) {
        return ResponseEntity.ok(carritoService.eliminarItem(ConversorDeRoles.identificadorDe(usuario), itemId));
    }
}
