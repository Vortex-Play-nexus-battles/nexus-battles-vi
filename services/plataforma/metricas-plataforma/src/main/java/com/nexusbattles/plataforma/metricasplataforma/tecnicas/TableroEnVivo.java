package com.nexusbattles.plataforma.metricasplataforma.tecnicas;

import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.Comprobacion;
import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.ConfiguracionDeDisponibilidad;
import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.InformeDeDisponibilidad;
import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.MonitorDeDisponibilidad;
import com.nexusbattles.plataforma.metricasplataforma.sondeo.MotivoDeFallo;
import com.nexusbattles.plataforma.metricasplataforma.sondeo.ResultadoReciente;
import com.nexusbattles.plataforma.metricasplataforma.sondeo.RondaEnParalelo;
import com.nexusbattles.plataforma.observabilidad.PropiedadesDeLatencia;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * El tablero tecnico de HU-MET-004, recolectado en el momento de la peticion
 * (es un tablero, no un historico: el historico de disponibilidad ya lo guarda
 * HU-DIS-001).
 *
 * <h2>Cuanto tarda (RFINAL-08)</h2>
 *
 * Hasta el 4-oct se recolectaba un servicio detras de otro, cada uno con sus
 * cuatro lecturas de Actuator: veintiocho llamadas en serie, 7293 ms medidos
 * en DEV. Ahora los servicios se recolectan a la vez en la
 * {@link RondaEnParalelo}; el que no termina dentro del plazo de la ronda sale
 * como brecha de observabilidad con su motivo (CA-03: senalada, nunca
 * disimulada) y no retrasa al resto. La recoleccion se reutiliza mientras
 * este vigente, y el JSON y el texto para el acta salen de la misma.
 */
public final class TableroEnVivo {

    private final ConfiguracionDeDisponibilidad configuracion;
    private final RecolectorDeMetricas recolector;
    private final MonitorDeDisponibilidad monitor;
    private final PropiedadesDeLatencia latencia;
    private final RondaEnParalelo ronda;
    private final ResultadoReciente<TableroTecnico> reciente;
    private final Clock reloj;

    public TableroEnVivo(ConfiguracionDeDisponibilidad configuracion, RecolectorDeMetricas recolector,
                         MonitorDeDisponibilidad monitor, PropiedadesDeLatencia latencia, RondaEnParalelo ronda,
                         ResultadoReciente<TableroTecnico> reciente, Clock reloj) {
        this.configuracion = configuracion;
        this.recolector = recolector;
        this.monitor = monitor;
        this.latencia = latencia;
        this.ronda = ronda;
        this.reciente = reciente;
        this.reloj = reloj;
    }

    public TableroTecnico actual() {
        ResultadoReciente.Lectura<TableroTecnico> lectura = reciente.obtener(this::construir);
        return lectura.desdeCache() ? lectura.valor().reutilizado() : lectura.valor();
    }

    private TableroTecnico construir() {
        Instant generadoEn = Instant.now(reloj);
        List<Map.Entry<String, String>> servicios = List.copyOf(configuracion.servicios().entrySet());
        List<MetricasDeServicio> recolectadas = ronda.ejecutar(servicios,
                servicio -> recolector.recolectar(servicio.getKey(), servicio.getValue()),
                (servicio, fallo) -> MetricasDeServicio.brecha(servicio.getKey(),
                        MotivoDeFallo.describir(fallo, null, null)),
                servicio -> MetricasDeServicio.brecha(servicio.getKey(),
                        "no respondió en " + ronda.plazo().toMillis() + " ms"));

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
        return TableroTecnico.de(generadoEn, recolectadas, disponibilidad, latencia.getObjetivoMs(),
                configuracion.umbralPorcentaje());
    }
}
