package nexus.alertas;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
