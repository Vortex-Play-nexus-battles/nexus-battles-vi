package com.nexusbattles.ms_subastas.pujas.api;

import com.nexusbattles.ms_subastas.pujas.dto.CompraInmediataRequest;
import com.nexusbattles.ms_subastas.pujas.dto.PujaResponse;
import com.nexusbattles.ms_subastas.pujas.dto.PujarRequest;
import com.nexusbattles.ms_subastas.pujas.model.Puja;
import com.nexusbattles.ms_subastas.pujas.service.PujaApplicationService;
import com.nexusbattles.ms_subastas.pujas.service.PujaRechazadaException;
import com.nexusbattles.ms_subastas.subastas.port.IdentidadClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * HU-SUB-004: pujar y comprar de forma inmediata. Implementa
 * {@code contracts/openapi/ms-subastas-pujas.yaml}.
 *
 * <p><b>La identidad sale siempre del token, nunca del cuerpo ni de un
 * parametro.</b> Estos dos endpoints mueven creditos, asi que aceptar un
 * jugadorId de la peticion permitiria pujar en nombre de otro. Se resuelve por
 * el puerto {@link IdentidadClient}, que {@code IdentidadDesdeToken} implementa
 * validando el JWT y exigiendo el claim {@code uid}.
 *
 * <p>Sin {@code /api/v1} en el mapping: lo antepone
 * {@code server.servlet.context-path} globalmente, igual que en el controlador
 * del listado.
 *
 * <p>Los dos responden 201 y no 200: cada llamada que prospera crea una fila
 * nueva en pujas, tambien la compra inmediata, que deja una puja GANADORA.
 */
@RestController
@RequestMapping("/subastas/{subastaId}")
public class PujaController {

    private final PujaApplicationService pujas;
    private final IdentidadClient identidad;

    public PujaController(PujaApplicationService pujas, IdentidadClient identidad) {
        this.pujas = pujas;
        this.identidad = identidad;
    }

    /**
     * @param idempotencyKey obligatoria por contrato. No se genera en el
     *                       servidor a proposito: derivada del reloj, un
     *                       reintento del cliente por timeout traeria una clave
     *                       distinta y ms-finanzas reservaria los creditos dos
     *                       veces, que es justo lo que la clave evita.
     */
    @PostMapping("/pujas")
    @ResponseStatus(HttpStatus.CREATED)
    public PujaResponse pujar(
            @PathVariable UUID subastaId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(min = 8, max = 128) String idempotencyKey,
            @Valid @RequestBody PujarRequest solicitud) {

        Puja puja = pujas.pujar(subastaId, jugadorAutenticado(), solicitud.monto(), idempotencyKey);
        return PujaResponse.de(puja);
    }

    @PostMapping("/compra-inmediata")
    @ResponseStatus(HttpStatus.CREATED)
    public PujaResponse comprarAhora(
            @PathVariable UUID subastaId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(min = 8, max = 128) String idempotencyKey,
            @Valid @RequestBody CompraInmediataRequest solicitud) {

        // La confirmacion se comprueba en el servidor, no solo en la interfaz:
        // un bug de front no puede cerrar una compra que el jugador no pidio.
        if (!solicitud.estaConfirmada()) {
            throw new PujaRechazadaException(PujaRechazadaException.Motivo.CONFIRMACION_REQUERIDA,
                    "La compra inmediata exige confirmacion explicita del jugador");
        }

        Puja ganadora = pujas.comprarAhora(subastaId, jugadorAutenticado(), idempotencyKey);
        return PujaResponse.de(ganadora);
    }

    private UUID jugadorAutenticado() {
        return identidad.actual().usuarioId();
    }
}
