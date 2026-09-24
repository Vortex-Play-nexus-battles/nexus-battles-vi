package com.nexusbattles.ms_ecommerce.controller;

import com.nexusbattles.ms_ecommerce.dto.PaginaDeVitrina;
import com.nexusbattles.ms_ecommerce.service.VitrinaDelCatalogoService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * La vitrina de la tienda sobre el catalogo maestro:
 * {@code GET /ecommerce/api/v1/vitrina} (el servicio declara el context-path
 * {@code /ecommerce}).
 *
 * <p>Publica, como la vitrina legada: el catalogo lo ve cualquiera que entre a
 * la tienda. Sustituye a {@code GET /api/v1/productos} de este servicio, que
 * leia una tabla local vacia (ver {@link VitrinaController}).
 *
 * <p>Los limites de {@code page} y {@code size} los comprueba la validacion de
 * Spring antes de entrar al metodo: fuera de rango es un 400 con problem
 * details, y el catalogo ni se consulta.
 */
@RestController
@RequestMapping("/api/v1/vitrina")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:8080", "http://127.0.0.1:8080"})
public class VitrinaDelCatalogoController {

    private final VitrinaDelCatalogoService vitrina;

    /**
     * @param page pagina, desde 0
     * @param size productos por pagina: 16 por norma de la vista (RN-PRD-010),
     *             50 como mucho, el mismo tope que el listado del catalogo
     * @param tipo HEROE, HABILIDAD, ARMA, ARMADURA, ITEM o EPICA; sin el, todos
     */
    @GetMapping
    public ResponseEntity<PaginaDeVitrina> vitrina(
            @RequestParam(name = "page", defaultValue = "0")
            @Min(value = 0, message = "page debe ser mayor o igual que 0") int page,
            @RequestParam(name = "size", defaultValue = "16")
            @Min(value = 1, message = "size debe estar entre 1 y 50")
            @Max(value = 50, message = "size debe estar entre 1 y 50") int size,
            @RequestParam(name = "tipo", required = false) String tipo) {
        return ResponseEntity.ok(vitrina.pagina(page, size, tipo));
    }
}
