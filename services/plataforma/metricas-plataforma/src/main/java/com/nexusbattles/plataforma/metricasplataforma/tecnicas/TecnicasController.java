package com.nexusbattles.plataforma.metricasplataforma.tecnicas;

import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.Comprobacion;
import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.ConfiguracionDeDisponibilidad;
import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.InformeDeDisponibilidad;
import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.MonitorDeDisponibilidad;
import com.nexusbattles.plataforma.observabilidad.PropiedadesDeLatencia;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@code GET /api/v1/tecnicas} (JSON) y {@code /api/v1/tecnicas/informe/texto}
 * — HU-MET-004. Recolecta en el momento de la peticion: es un tablero, no un
 * historico (el historico de disponibilidad ya lo guarda HU-DIS-001).
 */
@RestController
@RequestMapping("/api/v1/tecnicas")
public class TecnicasController {

    private final ConfiguracionDeDisponibilidad configuracion;
    private final RecolectorDeMetricas recolector;
    private final MonitorDeDisponibilidad monitor;
    private final PropiedadesDeLatencia latencia;
    private final Clock reloj;

    public TecnicasController(ConfiguracionDeDisponibilidad configuracion, RecolectorDeMetricas recolector,
                              MonitorDeDisponibilidad monitor, PropiedadesDeLatencia latencia, Clock reloj) {
        this.configuracion = configuracion;
        this.recolector = recolector;
        this.monitor = monitor;
        this.latencia = latencia;
        this.reloj = reloj;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public TableroTecnico tablero() {
        return construir();
    }

    @GetMapping(value = "/informe/texto", produces = MediaType.TEXT_PLAIN_VALUE)
    public String texto() {
        return construir().comoTexto();
    }

    TableroTecnico construir() {
        List<MetricasDeServicio> recolectadas = new ArrayList<>();
        configuracion.servicios().forEach((servicio, url) -> recolectadas.add(recolector.recolectar(servicio, url)));

        Map<String, Comprobacion> estado = monitor.estadoActual().stream()
                .collect(Collectors.toMap(Comprobacion::servicio, Function.identity(), (a, b) -> b));
        InformeDeDisponibilidad mes = monitor.informeMensual();
        Map<String, Double> porcentajes = mes.servicios().stream()
                .collect(Collectors.toMap(InformeDeDisponibilidad.LineaDeServicio::servicio,
                        InformeDeDisponibilidad.LineaDeServicio::porcentaje, (a, b) -> b));
        List<TableroTecnico.Disponibilidad> disponibilidad = configuracion.nombres().stream()
                .map(s -> new TableroTecnico.Disponibilidad(s,
                        estado.containsKey(s) && estado.get(s).disponible(), porcentajes.get(s)))
                .toList();
        return TableroTecnico.de(Instant.now(reloj), recolectadas, disponibilidad, latencia.getObjetivoMs(),
                configuracion.umbralPorcentaje());
    }
}
