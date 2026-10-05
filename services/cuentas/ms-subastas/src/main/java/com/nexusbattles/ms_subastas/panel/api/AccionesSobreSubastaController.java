package com.nexusbattles.ms_subastas.panel.api;

import com.nexusbattles.ms_subastas.panel.dto.PanelDtos;
import com.nexusbattles.ms_subastas.panel.service.CancelacionService;
import com.nexusbattles.ms_subastas.panel.service.SeguimientoService;
import com.nexusbattles.ms_subastas.subastas.port.IdentidadClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Lo que el jugador hace sobre una subasta desde su panel (7.7.9 y 7.7.10):
 * cancelarla si es suya, y seguirla o dejar de seguirla.
 */
@RestController
@RequestMapping("/subastas/{subastaId}")
public class AccionesSobreSubastaController {

    private final CancelacionService cancelaciones;
    private final SeguimientoService seguimiento;
    private final IdentidadClient identidad;

    public AccionesSobreSubastaController(CancelacionService cancelaciones, SeguimientoService seguimiento,
                                          IdentidadClient identidad) {
        this.cancelaciones = cancelaciones;
        this.seguimiento = seguimiento;
        this.identidad = identidad;
    }

    /** POST y no DELETE: cancelar cobra una penalizacion, no borra nada. */
    @PostMapping("/cancelacion")
    public PanelDtos.Cancelacion cancelar(@PathVariable UUID subastaId) {
        return PanelDtos.Cancelacion.de(cancelaciones.cancelar(subastaId, jugador()));
    }

    @PutMapping("/seguimiento")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void seguir(@PathVariable UUID subastaId) {
        seguimiento.seguir(subastaId, jugador());
    }

    @DeleteMapping("/seguimiento")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dejarDeSeguir(@PathVariable UUID subastaId) {
        seguimiento.dejarDeSeguir(subastaId, jugador());
    }

    private UUID jugador() {
        return identidad.actual().usuarioId();
    }
}
