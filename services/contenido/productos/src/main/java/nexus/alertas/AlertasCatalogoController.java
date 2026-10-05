package nexus.alertas;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/productos/alertas")
public class AlertasCatalogoController {

    private final AlertasCatalogoServicio servicio;

    public AlertasCatalogoController(AlertasCatalogoServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/inicio-sesion")
    public List<AlertaResponse> consultarAlIniciarSesion(Principal jugador) {
        return servicio.consultarAlIniciarSesion(jugador.getName()).stream()
                .map(AlertaResponse::desde)
                .toList();
    }

    /**
     * HU-NOT-001 (#532), productos.yaml 1.6.0 — los cambios del catalogo para
     * otro servicio. Solo con token de servicio (la regla esta en
     * {@code SeguridadConfig}); de solo lectura. Los limites de {@code limite}
     * los comprueba Bean Validation antes de entrar: fuera de rango, o un
     * {@code desde} que no es fecha y hora, responden 400 con Problem Details
     * ({@code ManejadorDeErrores}).
     */
    @GetMapping("/cambios")
    public LoteResponse consultarCambios(
            @RequestParam(name = "desde", required = false) Instant desde,
            @RequestParam(name = "limite", defaultValue = "50")
            @Min(value = 1, message = "limite debe estar entre 1 y 200")
            @Max(value = 200, message = "limite debe estar entre 1 y 200")
            int limite) {
        LoteDeAlertasCatalogo lote = servicio.consultarCambios(desde, limite);
        return new LoteResponse(
                lote.hasta(),
                lote.completo(),
                lote.alertas().stream().map(AlertaResponse::desde).toList());
    }

    /** Respuesta de {@code GET /cambios}: el esquema LoteDeAlertasCatalogo del contrato. */
    public record LoteResponse(
            Instant hasta,
            boolean completo,
            List<AlertaResponse> alertas) {
    }

    public record AlertaResponse(
            String id,
            String productoId,
            String productoNombre,
            TipoCambioCatalogo tipo,
            String descripcion,
            Instant implementadaEn) {

        static AlertaResponse desde(AlertaCatalogo alerta) {
            return new AlertaResponse(
                    alerta.id(),
                    alerta.productoId(),
                    alerta.productoNombre(),
                    alerta.tipo(),
                    alerta.descripcion(),
                    alerta.implementadaEn());
        }
    }
}
