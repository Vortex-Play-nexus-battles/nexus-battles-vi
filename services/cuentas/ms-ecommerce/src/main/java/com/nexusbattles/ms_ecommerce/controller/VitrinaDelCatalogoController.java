package com.nexusbattles.ms_ecommerce.controller;

import com.nexusbattles.ms_ecommerce.dto.ConsultaDeVitrina;
import com.nexusbattles.ms_ecommerce.dto.PaginaDeVitrina;
import com.nexusbattles.ms_ecommerce.precios.Moneda;
import com.nexusbattles.ms_ecommerce.seguridad.ConversorDeRoles;
import com.nexusbattles.ms_ecommerce.service.VitrinaDelCatalogoService;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * La vitrina de la tienda sobre el catalogo maestro:
 * {@code GET /ecommerce/api/v1/vitrina} (el servicio declara el context-path
 * {@code /ecommerce}).
 *
 * <p>Publica, como la vitrina legada: el catalogo lo ve cualquiera que entre a
 * la tienda, tambien desde la portada. Sustituye a {@code GET /api/v1/productos}
 * de este servicio, que leia una tabla local vacia (ver {@link VitrinaController}).
 *
 * <p>B5 (contrato 1.4.0): moneda, filtros y busqueda; y, si la peticion trae la
 * sesion de un usuario, las marcas de su lista de deseos y de lo que ya tiene.
 * La identidad sale del token, nunca de un parametro.
 *
 * <p>Los limites de los parametros los comprueba la validacion de Spring antes
 * de entrar al metodo: fuera de rango es un 400 con problem details, y el
 * catalogo ni se consulta.
 */
@RestController
@RequestMapping("/api/v1/vitrina")
@RequiredArgsConstructor
public class VitrinaDelCatalogoController {

    private final VitrinaDelCatalogoService vitrina;

    /**
     * @param page         pagina, desde 0
     * @param size         productos por pagina: 16 por norma de la vista (RN-PRD-010),
     *                     50 como mucho, el mismo tope que el listado del catalogo
     * @param tipo         HEROE, HABILIDAD, ARMA, ARMADURA, ITEM o EPICA; sin el, todos
     * @param moneda       COP (por omision), USD o EUR
     * @param precioMinimo precio final minimo, en esa moneda
     * @param precioMaximo precio final maximo, en esa moneda
     * @param enPromocion  true = solo con promocion vigente
     * @param busqueda     texto libre
     */
    @GetMapping
    public ResponseEntity<PaginaDeVitrina> vitrina(
            @RequestParam(name = "page", defaultValue = "0")
            @Min(value = 0, message = "page debe ser mayor o igual que 0") int page,
            @RequestParam(name = "size", defaultValue = "16")
            @Min(value = 1, message = "size debe estar entre 1 y 50")
            @Max(value = 50, message = "size debe estar entre 1 y 50") int size,
            @RequestParam(name = "tipo", required = false) String tipo,
            @RequestParam(name = "moneda", defaultValue = "COP") Moneda moneda,
            @RequestParam(name = "precioMinimo", required = false)
            @DecimalMin(value = "0", message = "precioMinimo no puede ser negativo") BigDecimal precioMinimo,
            @RequestParam(name = "precioMaximo", required = false)
            @DecimalMin(value = "0", message = "precioMaximo no puede ser negativo") BigDecimal precioMaximo,
            @RequestParam(name = "enPromocion", defaultValue = "false") boolean enPromocion,
            @RequestParam(name = "busqueda", required = false)
            @Size(max = 100, message = "busqueda admite hasta 100 caracteres") String busqueda,
            Authentication autenticacion) {
        ConsultaDeVitrina consulta = new ConsultaDeVitrina(page, size, tipo, moneda, precioMinimo, precioMaximo,
                enPromocion, busqueda);
        String usuario = ConversorDeRoles.usuarioDe(autenticacion).orElse(null);
        return ResponseEntity.ok(vitrina.pagina(consulta, usuario));
    }
}
