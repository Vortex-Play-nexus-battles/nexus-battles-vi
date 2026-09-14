package com.nexusbattles.ms_subastas.pujas.api;

import com.nexusbattles.ms_subastas.pujas.dto.MiResumenResponse;
import com.nexusbattles.ms_subastas.pujas.service.ConsultaDeParticipacionService;
import com.nexusbattles.ms_subastas.subastas.port.IdentidadClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lo que el jugador tiene en juego sumando todas las subastas.
 *
 * <p>En su propio controlador y no colgando de {@code /subastas/{subastaId}}
 * porque no habla de ninguna subasta concreta. Colgarlo de ahi obligaria a
 * inventar un identificador para pedirlo.
 *
 * <p>No devuelve saldo total ni disponible: eso lo sabe ms-finanzas, que
 * todavia no existe. Este servicio solo responde por lo que el mismo retiene.
 */
@RestController
@RequestMapping("/mis-pujas")
public class MisPujasController {

    private final ConsultaDeParticipacionService consultas;
    private final IdentidadClient identidad;

    public MisPujasController(ConsultaDeParticipacionService consultas, IdentidadClient identidad) {
        this.consultas = consultas;
        this.identidad = identidad;
    }

    @GetMapping("/resumen")
    public MiResumenResponse resumen() {
        return consultas.miResumen(identidad.actual().usuarioId());
    }
}
