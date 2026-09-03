package com.nexusbattles.ms_ecommerce.controller;

import com.nexusbattles.ms_ecommerce.dto.AgregarItemRequest;
import com.nexusbattles.ms_ecommerce.model.Carrito;
import com.nexusbattles.ms_ecommerce.service.CarritoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ecommerce/carrito")
@RequiredArgsConstructor
public class CarritoController {

    private final CarritoService carritoService;

    @GetMapping
    public ResponseEntity<Carrito> obtenerCarrito(@RequestHeader("X-User-Id") String usuarioId) {
        return ResponseEntity.ok(carritoService.obtenerOCrearCarrito(usuarioId));
    }

    @PostMapping("/items")
    public ResponseEntity<Carrito> agregarItem(
            @RequestHeader("X-User-Id") String usuarioId,
            @Valid @RequestBody AgregarItemRequest request) {
        return ResponseEntity.ok(carritoService.agregarProducto(usuarioId, request));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Carrito> eliminarItem(
            @RequestHeader("X-User-Id") String usuarioId,
            @PathVariable Long itemId) {
        return ResponseEntity.ok(carritoService.eliminarItem(usuarioId, itemId));
    }
}
