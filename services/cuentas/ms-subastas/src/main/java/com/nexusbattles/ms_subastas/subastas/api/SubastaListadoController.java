package com.nexusbattles.ms_subastas.subastas.api;

import com.nexusbattles.ms_subastas.reglas.FuenteDeReglas;
import com.nexusbattles.ms_subastas.subastas.dto.FiltrosSubasta;
import com.nexusbattles.ms_subastas.subastas.dto.PaginaDeSubastasResponse;
import com.nexusbattles.ms_subastas.subastas.dto.ReglasVigentesResponse;
import com.nexusbattles.ms_subastas.subastas.dto.SubastaDetalleResponse;
import com.nexusbattles.ms_subastas.subastas.dto.SugerenciasResponse;
import com.nexusbattles.ms_subastas.subastas.model.TipoProducto;
import com.nexusbattles.ms_subastas.subastas.port.IdentidadClient;
import com.nexusbattles.ms_subastas.subastas.service.CalculadorComisionPublicacion;
import com.nexusbattles.ms_subastas.subastas.service.FichaDeSubastaService;
import com.nexusbattles.ms_subastas.subastas.service.SubastaListadoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * HU-SUB-011. Endpoints publicos (jugador o visitante, sin autenticacion) --
 * confirmado desde el inicio, no bloqueados por la decision de JWT pendiente
 * entre Andres/Edwin/Santiago.
 *
 * <p>B8 anade la ficha de una subasta y las reglas vigentes, tambien publicas
 * ({@code ms-subastas-listado.yaml} 1.1.0).
 *
 * Sin /api/v1 en el mapping: ya lo agrega server.servlet.context-path
 * globalmente (confirmado en el log de arranque de MsSubastasApplication).
 */
@RestController
@RequestMapping("/subastas")
public class SubastaListadoController {

    private final SubastaListadoService subastaListadoService;
    private final FichaDeSubastaService fichas;
    private final FuenteDeReglas reglas;
    private final CalculadorComisionPublicacion comisiones;
    private final IdentidadClient identidad;

    public SubastaListadoController(SubastaListadoService subastaListadoService, FichaDeSubastaService fichas,
                                    FuenteDeReglas reglas, CalculadorComisionPublicacion comisiones,
                                    IdentidadClient identidad) {
        this.subastaListadoService = subastaListadoService;
        this.fichas = fichas;
        this.reglas = reglas;
        this.comisiones = comisiones;
        this.identidad = identidad;
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

    @GetMapping("/sugerencias")
    public SugerenciasResponse sugerir(
        @RequestParam String q,
        @RequestParam(defaultValue = "8") int limite
    ) {
        return subastaListadoService.sugerir(q, limite);
    }

    /** B8: las reglas de 7.7 vigentes, para que la interfaz no las escriba a mano. */
    @GetMapping("/reglas")
    public ReglasVigentesResponse reglasVigentes() {
        return ReglasVigentesResponse.desde(reglas.vigentes(), comisiones);
    }

    /** B8: la ficha de una subasta, en cualquier estado. La sesion solo cuenta la visita. */
    @GetMapping("/{subastaId}")
    public SubastaDetalleResponse detalle(@PathVariable UUID subastaId) {
        return fichas.ficha(subastaId, jugadorSiHaySesion());
    }

    private UUID jugadorSiHaySesion() {
        try {
            return identidad.actual().usuarioId();
        } catch (RuntimeException sinSesion) {
            return null;
        }
    }
}
