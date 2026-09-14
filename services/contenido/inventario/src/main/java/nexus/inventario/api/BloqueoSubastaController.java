package nexus.inventario.api;

import jakarta.validation.Valid;
import java.util.UUID;
import nexus.inventario.aplicacion.GestionarBloqueoSubasta;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventario/elementos/{elementoId}/bloqueo-subasta")
public class BloqueoSubastaController {

    private static final String CABECERA_IDENTIDAD = "X-User-Name";
    private final GestionarBloqueoSubasta gestion;

    public BloqueoSubastaController(GestionarBloqueoSubasta gestion) {
        this.gestion = gestion;
    }

    @PutMapping
    public ElementoInventarioResponse bloquear(
            @RequestHeader(name = CABECERA_IDENTIDAD, required = false) String identidad,
            @RequestHeader(name = "Idempotency-Key", required = false) String claveIdempotencia,
            @PathVariable String elementoId,
            @Valid @RequestBody BloquearEnSubastaRequest solicitud) {
        return ElementoInventarioResponse.de(gestion.bloquear(
                identidad, elementoId, solicitud.subastaId().toString(), claveIdempotencia));
    }

    @DeleteMapping("/{subastaId}")
    public ElementoInventarioResponse liberar(
            @RequestHeader(name = "Idempotency-Key", required = false) String claveIdempotencia,
            @PathVariable String elementoId,
            @PathVariable UUID subastaId) {
        return ElementoInventarioResponse.de(
                gestion.liberar(elementoId, subastaId.toString(), claveIdempotencia));
    }
}
