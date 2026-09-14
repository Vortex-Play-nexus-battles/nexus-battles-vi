package com.nexusbattles.ms_subastas.pujas.api;

import com.nexusbattles.ms_subastas.pujas.dto.PujaAutomaticaRequest;
import com.nexusbattles.ms_subastas.pujas.dto.PujaAutomaticaResponse;
import com.nexusbattles.ms_subastas.pujas.model.PujaAutomatica;
import com.nexusbattles.ms_subastas.pujas.service.PujaAutomaticaApplicationService;
import com.nexusbattles.ms_subastas.subastas.port.IdentidadClient;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * HU-SUB-004, criterio 4: la puja automatica del jugador sobre una subasta.
 *
 * <p><b>PUT y no POST</b> porque hay como maximo una por jugador y subasta (asi
 * lo fija el unico de {@code pujas_automaticas}): la peticion describe el estado
 * final deseado, no un recurso nuevo, y repetirla con el mismo limite deja todo
 * igual. Por eso responde 200 y no 201.
 *
 * <p>Sin cabecera Idempotency-Key, a diferencia de pujar y comprar: configurar
 * no mueve creditos todavia. La reserva la hace el motor cuando llegue a emitir,
 * y esa si lleva su propia clave.
 */
@RestController
@RequestMapping("/subastas/{subastaId}/puja-automatica")
public class PujaAutomaticaController {

    private final PujaAutomaticaApplicationService pujasAutomaticas;
    private final IdentidadClient identidad;

    public PujaAutomaticaController(PujaAutomaticaApplicationService pujasAutomaticas, IdentidadClient identidad) {
        this.pujasAutomaticas = pujasAutomaticas;
        this.identidad = identidad;
    }

    @PutMapping
    public PujaAutomaticaResponse configurar(
            @PathVariable UUID subastaId,
            @Valid @RequestBody PujaAutomaticaRequest solicitud) {

        PujaAutomatica configurada = pujasAutomaticas.configurar(
                subastaId, jugadorAutenticado(), solicitud.limite());
        return PujaAutomaticaResponse.de(configurada);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@PathVariable UUID subastaId) {
        pujasAutomaticas.desactivar(subastaId, jugadorAutenticado());
    }

    private UUID jugadorAutenticado() {
        return identidad.actual().usuarioId();
    }
}
