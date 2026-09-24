package com.nexusbattles.ms_ecommerce.controller;

import com.nexusbattles.ms_ecommerce.dto.ProductoVitrinaDto;
import com.nexusbattles.ms_ecommerce.service.VitrinaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Vitrina LEGADA: pagina la tabla local {@code productos} de este servicio.
 *
 * @deprecated Sustituida por {@code GET /api/v1/vitrina}
 * ({@link VitrinaDelCatalogoController}), que proyecta el catalogo maestro del
 * servicio productos (RF-CAR-001). Tres cosas que conviene saber antes de
 * tocarla:
 * <ol>
 *   <li><b>Esta obsoleta</b>: la tabla que lee nace vacia y no esta conectada
 *       a nada, y sus ids son BIGINT locales que el catalogo no conoce. La
 *       tienda usa {@code /api/v1/vitrina}.</li>
 *   <li><b>Se conserva sin cambios por compatibilidad de contrato</b>: la
 *       regla 2 de plataforma dice que un cambio incompatible abre una version
 *       nueva en vez de modificar la existente, y pasar este listado a los
 *       UUID del catalogo lo seria (el {@code id} dejaria de ser un numero).
 *       Por eso la vitrina nueva vive en otra ruta y esta sigue respondiendo
 *       exactamente lo mismo que antes.</li>
 *   <li><b>Deja de ser alcanzable por el borde</b> en cuanto el borde envie
 *       {@code /api/v1/productos} al servicio productos (colision de prefijo,
 *       #421): desde entonces solo contesta a quien llame a ms-ecommerce
 *       directamente, y se podra retirar.</li>
 * </ol>
 */
@Deprecated
@RestController
@RequestMapping("/api/v1/productos")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:8080", "http://127.0.0.1:8080"})
public class VitrinaController {

    private final VitrinaService vitrinaService;

    @GetMapping()
    public ResponseEntity<Page<ProductoVitrinaDto>> obtenerProductos(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "16") int size, // Paginación de 16 elementos por norma[cite: 3]
        @RequestParam(required = false, defaultValue = "COP") String moneda) {

        Pageable pageable = PageRequest.of(page, size);
        Page<ProductoVitrinaDto> resultado = vitrinaService.obtenerProductosVitrina(pageable, moneda);
        return ResponseEntity.ok(resultado);
    }
}
