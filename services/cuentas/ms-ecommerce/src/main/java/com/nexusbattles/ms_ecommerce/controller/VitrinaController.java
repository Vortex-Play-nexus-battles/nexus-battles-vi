package com.nexusbattles.ms_ecommerce.controller;

import com.nexusbattles.ms_ecommerce.dto.ProductoVitrinaDto;
import com.nexusbattles.ms_ecommerce.service.VitrinaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ecommerce")
@RequiredArgsConstructor
public class VitrinaController {

    private final VitrinaService vitrinaService;

    @GetMapping("/productos")
    public ResponseEntity<Page<ProductoVitrinaDto>> obtenerProductos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "16") int size, // Paginación de 16 elementos por norma[cite: 3]
            @RequestParam(required = false, defaultValue = "COP") String moneda) {

        Pageable pageable = PageRequest.of(page, size);
        Page<ProductoVitrinaDto> resultado = vitrinaService.obtenerProductosVitrina(pageable, moneda);
        return ResponseEntity.ok(resultado);
    }
}