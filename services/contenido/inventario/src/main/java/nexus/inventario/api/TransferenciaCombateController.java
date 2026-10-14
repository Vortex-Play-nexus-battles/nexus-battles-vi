package nexus.inventario.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import nexus.inventario.aplicacion.TransferenciaCombate;
import nexus.inventario.aplicacion.TransferirEquipoPorCombate;
import nexus.inventario.dominio.ElementoInventario;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventario/transferencias-combate")
public class TransferenciaCombateController {

    private final TransferirEquipoPorCombate servicio;

    public TransferenciaCombateController(TransferirEquipoPorCombate servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    public TransferenciaCombateResponse transferir(
            @RequestHeader(name = "Idempotency-Key") @NotBlank @Size(max = 100) String operacionId,
            @Valid @RequestBody TransferirEquipoCombateRequest solicitud) {
        List<TransferenciaCombate> transferencias = solicitud.transferencias().stream()
                .map(movimiento -> new TransferenciaCombate(
                        movimiento.elementoId(),
                        movimiento.propietarioOrigenId(),
                        movimiento.heroeOrigenId(),
                        movimiento.propietarioDestinoId()))
                .toList();
        List<String> transferidos = servicio.transferir(operacionId, transferencias).stream()
                .map(ElementoInventario::id)
                .toList();
        return new TransferenciaCombateResponse(operacionId, transferidos);
    }
}
