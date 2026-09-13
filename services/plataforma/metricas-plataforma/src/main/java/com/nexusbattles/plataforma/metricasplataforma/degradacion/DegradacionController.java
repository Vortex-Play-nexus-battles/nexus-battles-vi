package com.nexusbattles.plataforma.metricasplataforma.degradacion;

import java.time.Instant;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexusbattles.plataforma.resiliencia.RegistroDeDegradacion;

/**
 * Que secciones estan limitadas ahora mismo (HU-DIS-003).
 *
 * <p>Contrato: contracts/openapi/metricas-plataforma.yaml
 *
 * <p>Es lo que permite responder CP-03 con un dato y no con una opinion: si el
 * registro dice que solo una dependencia esta degradada, el resto de los
 * modulos esta respondiendo con normalidad. Y responder este endpoint **con
 * 200** mientras hay una seccion caida es en si mismo la evidencia de CA-01: el
 * servicio sigue en pie y lo esta contando.
 */
@RestController
@RequestMapping("/api/v1/degradacion")
public class DegradacionController {

    private final RegistroDeDegradacion registro;

    public DegradacionController(RegistroDeDegradacion registro) {
        this.registro = registro;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public EstadoResponse estado() {
        List<SeccionResponse> secciones = registro.activas().stream()
                .map(degradacion -> new SeccionResponse(
                        degradacion.dependencia(), degradacion.seccion(), degradacion.desde()))
                .toList();

        return new EstadoResponse(registro.sinDegradaciones(), secciones);
    }

    /**
     * @param operativoPorCompleto true cuando no hay ninguna seccion limitada.
     *     Ojo con leerlo al reves: que haya una seccion limitada no significa
     *     que el servicio este caido, sino justo lo contrario.
     * @param seccionesLimitadas las secciones degradadas, en el orden en que aparecieron
     */
    public record EstadoResponse(boolean operativoPorCompleto, List<SeccionResponse> seccionesLimitadas) {}

    public record SeccionResponse(String dependencia, String seccion, Instant desde) {}
}
