package com.nexusbattles.ms_finanzas.transacciones;

import java.security.Principal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta el historial de transacciones (HU-PAG-002). Sirve la pantalla
 * "Historial de transacciones" en Mi Cuenta.
 *
 * <p>El {@code uid} se lee del {@link Principal} inyectado por Spring Security:
 * {@code ConversorRolesJwt} de {@code shared/libs/plataforma-seguridad}
 * configura {@code principalClaimName = "uid"}, así que
 * {@code principal.getName()} devuelve el UUID inmutable del usuario. El
 * controller nunca acepta el uid por query ni por path, así que el usuario no
 * puede consultar transacciones de otro.
 *
 * <p>La consulta administrativa es otra ruta, {@code /usuarios/{uid}}
 * (transacciones.yaml 1.1.0, SA-15): la ficha administrativa de la cuenta
 * muestra su historial de pagos en moneda real (§7.3.4 «Historial de
 * transacciones»). Solo la abren Administrador y Super Administrador
 * ({@code SecurityConfig}); un jugador no ve la de otro por ninguna de las dos.
 */
@RestController
@RequestMapping("/transacciones")
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
            Principal principal,
            @RequestParam(name = "page", defaultValue = "0") int pagina,
            @RequestParam(name = "size", defaultValue = "20") int tamanoPagina) {

        String uid = principal.getName();
        return ResponseEntity.ok(consultaService.listarPorUsuario(uid, paginaDe(pagina, tamanoPagina)));
    }

    /**
     * El historial de pagos en moneda real de una cuenta, para su ficha
     * administrativa (transacciones.yaml 1.1.0, §7.3.4). Misma proyección y
     * mismo orden que {@code /mi-historial}: sin la referencia de la pasarela.
     */
    @GetMapping("/usuarios/{uid}")
    public ResponseEntity<Page<ResumenTransaccion>> historialDeUnaCuenta(
            @PathVariable("uid") String uid,
            @RequestParam(name = "page", defaultValue = "0") int pagina,
            @RequestParam(name = "size", defaultValue = "20") int tamanoPagina) {

        return ResponseEntity.ok(consultaService.listarPorUsuario(uid, paginaDe(pagina, tamanoPagina)));
    }

    private static Pageable paginaDe(int pagina, int tamanoPagina) {
        return PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanoPagina, 1), TAMANO_MAXIMO_PAGINA));
    }
}
