package com.nexusbattles.ms_subastas.pujas.api;

import com.nexusbattles.ms_subastas.pujas.dto.CompraInmediataRequest;
import com.nexusbattles.ms_subastas.pujas.dto.MiParticipacionResponse;
import com.nexusbattles.ms_subastas.pujas.dto.PujaDelHistorialResponse;
import com.nexusbattles.ms_subastas.pujas.dto.PujaResponse;
import com.nexusbattles.ms_subastas.pujas.dto.PujarRequest;
import com.nexusbattles.ms_subastas.pujas.model.Puja;
import com.nexusbattles.ms_subastas.pujas.service.ConsultaDeParticipacionService;
import com.nexusbattles.ms_subastas.pujas.service.PujaApplicationService;
import com.nexusbattles.ms_subastas.pujas.service.PujaRechazadaException;
import com.nexusbattles.ms_subastas.subastas.port.IdentidadClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
    private final ConsultaDeParticipacionService consultas;
    private final IdentidadClient identidad;

    public PujaController(PujaApplicationService pujas, ConsultaDeParticipacionService consultas,
                          IdentidadClient identidad) {
        this.pujas = pujas;
        this.consultas = consultas;
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

    /**
     * Historial de pujas de la subasta, de la mas reciente a la mas antigua.
     *
     * <p>Publico: se puede mirar sin sesion, igual que el listado. Con sesion,
     * cada linea viene marcada como propia, para que la interfaz pueda
     * resaltarlas sin conocer el uid de nadie.
     *
     * <p>No devuelve apodos. El de cada postor vive en ms-identidad, y traerlo
     * obligaria a este servicio a consultar otro dominio solo para pintar una
     * lista.
     */
    @GetMapping("/pujas")
    public List<PujaDelHistorialResponse> historial(@PathVariable UUID subastaId) {
        return consultas.historial(subastaId, jugadorSiHaySesion());
    }

    /**
     * La situacion del jugador que mira en esta subasta: si va ganando, cuanto
     * lleva retenido, que limite automatico tiene y cuando puede volver a pujar.
     *
     * <p>Existe porque el listado de HU-SUB-011 es el mismo para todos y no
     * puede responder nada de eso. Sin este endpoint la pantalla tenia que
     * suponerlo, y lo que hacia era mostrar siempre "no vas ganando" y "sin
     * puja automatica", aunque fuera falso.
     */
    @GetMapping("/mi-participacion")
    public MiParticipacionResponse miParticipacion(@PathVariable UUID subastaId) {
        return consultas.miParticipacion(subastaId, jugadorAutenticado());
    }

    private UUID jugadorAutenticado() {
        return identidad.actual().usuarioId();
    }

    /**
     * El jugador, si hay sesion valida; null si no. Se usa solo donde la
     * respuesta es publica y la sesion unicamente enriquece lo que se devuelve:
     * negar el historial a quien no ha entrado seria mas restrictivo que el
     * listado, que si es publico.
     */
    private UUID jugadorSiHaySesion() {
        try {
            return identidad.actual().usuarioId();
        } catch (RuntimeException sinSesionValida) {
            return null;
        }
    }
}
