package com.nexusbattles.ms_subastas.subastas.api;

import com.nexusbattles.ms_subastas.subastas.dto.FiltrosSubasta;
import com.nexusbattles.ms_subastas.subastas.dto.PaginaDeSubastasResponse;
import com.nexusbattles.ms_subastas.subastas.model.TipoProducto;
import com.nexusbattles.ms_subastas.subastas.service.SubastaListadoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * HU-SUB-011. Endpoint publico (jugador o visitante, sin autenticacion) --
 * confirmado desde el inicio, no bloqueado por la decision de JWT pendiente
 * entre Andres/Edwin/Santiago.
 *
 * Sin /api/v1 en el mapping: ya lo agrega server.servlet.context-path
 * globalmente (confirmado en el log de arranque de MsSubastasApplication).
 */
@RestController
@RequestMapping("/subastas")
public class SubastaListadoController {

    private final SubastaListadoService subastaListadoService;

    public SubastaListadoController(SubastaListadoService subastaListadoService) {
        this.subastaListadoService = subastaListadoService;
    }

    @GetMapping
    public PaginaDeSubastasResponse listar(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) List<TipoProducto> tipoProducto,
        @RequestParam(required = false) String rareza,
        @RequestParam(required = false) BigDecimal precioMin,
        @RequestParam(required = false) BigDecimal precioMax,
        @RequestParam(required = false) String tiempoRestante,
        @RequestParam(required = false) String tipoVenta,
        @RequestParam(required = false) String metodoPago,
        @RequestParam(required = false) String vendedor,
        @RequestParam(defaultValue = "FECHA_PUBLICACION") String ordenarPor,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "16") int size
    ) {
        FiltrosSubasta filtros = new FiltrosSubasta(
            q, tipoProducto, rareza, precioMin, precioMax,
            tiempoRestante, tipoVenta, metodoPago, vendedor, ordenarPor
        );
        return subastaListadoService.listar(filtros, page, size);
    }
}
