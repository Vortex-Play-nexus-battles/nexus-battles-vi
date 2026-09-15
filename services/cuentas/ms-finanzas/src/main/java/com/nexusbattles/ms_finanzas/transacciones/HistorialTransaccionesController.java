package com.nexusbattles.ms_finanzas.transacciones;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexusbattles.ms_finanzas.seguridad.RequireAutenticacion;
import com.nexusbattles.ms_finanzas.seguridad.SecurityInterceptor;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Consulta el historial de transacciones (HU-PAG-002). Sirve la pantalla
 * "Historial de transacciones" en Mi Cuenta.
 *
 * <p>El {@code uidActual} lo pone {@link SecurityInterceptor} tras validar el
 * JWT; el controller nunca acepta el uid por query o path, así el usuario no
 * puede consultar transacciones de otro. La consulta administrativa (ver el
 * historial de otro usuario) se agrega en un PR aparte junto con la anotación
 * de rol correspondiente — no está en el alcance de esta HU.
 */
@RestController
@RequestMapping("/transacciones")
@RequireAutenticacion
public class HistorialTransaccionesController {

    /**
     * Tope del tamaño de página. Sin él, un cliente podría pedir {@code
     * size=100000} y forzar al servicio a materializar todo el historial de
     * un usuario en memoria.
     */
    private static final int TAMANO_MAXIMO_PAGINA = 100;

    private final TransaccionConsultaService consultaService;

    public HistorialTransaccionesController(TransaccionConsultaService consultaService) {
        this.consultaService = consultaService;
    }

    @GetMapping("/mi-historial")
    public ResponseEntity<Page<ResumenTransaccion>> miHistorial(
            HttpServletRequest request,
            @RequestParam(name = "page", defaultValue = "0") int pagina,
            @RequestParam(name = "size", defaultValue = "20") int tamanoPagina) {

        String uid = (String) request.getAttribute(SecurityInterceptor.ATTR_UID);
        Pageable pageable = PageRequest.of(
                Math.max(pagina, 0),
                Math.min(Math.max(tamanoPagina, 1), TAMANO_MAXIMO_PAGINA));
        return ResponseEntity.ok(consultaService.listarPorUsuario(uid, pageable));
    }
}
