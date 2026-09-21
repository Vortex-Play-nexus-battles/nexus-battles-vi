package com.nexusbattles.plataforma.metricasplataforma.tecnicas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

/**
 * Lee {@code /actuator/metrics/<nombre>} de cada servicio (regla 3: todos lo
 * exponen). Nadie instrumenta nada nuevo: son las metricas que Micrometer ya
 * registra en los veinte modulos.
 *
 * <ul>
 *   <li>{@code process.cpu.usage} — fraccion 0..1 del procesador que usa la JVM</li>
 *   <li>{@code jvm.memory.used} — bytes</li>
 *   <li>{@code http.server.requests} — COUNT, TOTAL_TIME (s), MAX (s); con
 *       {@code tag=outcome:SERVER_ERROR} el conteo de 5xx</li>
 * </ul>
 */
public class RecolectorDeActuator implements RecolectorDeMetricas {

    private final RestClient http;

    public RecolectorDeActuator(RestClient http) {
        this.http = http;
    }

    static String baseDe(String urlDeSalud) {
        int corte = urlDeSalud.indexOf("/actuator/");
        return corte < 0 ? urlDeSalud.replaceAll("/+$", "") + "/actuator" : urlDeSalud.substring(0, corte) + "/actuator";
    }

    @Override
    public MetricasDeServicio recolectar(String servicio, String urlDeSalud) {
        String base = baseDe(urlDeSalud);
        try {
            Optional<Metrica> peticiones = leer(base + "/metrics/http.server.requests");
            Optional<Metrica> errores = leer(base + "/metrics/http.server.requests?tag=outcome:SERVER_ERROR");
            Optional<Metrica> cpu = leer(base + "/metrics/process.cpu.usage");
            Optional<Metrica> memoria = leer(base + "/metrics/jvm.memory.used");
            long total = peticiones.map(m -> (long) m.valor("COUNT")).orElse(0L);
            double tiempoTotalS = peticiones.map(m -> m.valor("TOTAL_TIME")).orElse(0d);
            Double maximoMs = peticiones.map(m -> m.valor("MAX") * 1000d).orElse(null);
            Double promedioMs = total == 0 ? null : tiempoTotalS * 1000d / total;
            return new MetricasDeServicio(servicio,
                    cpu.map(m -> m.valor("VALUE")).orElse(null),
                    memoria.map(m -> m.valor("VALUE") / (1024d * 1024d)).orElse(null),
                    total, promedioMs, maximoMs,
                    errores.map(m -> (long) m.valor("COUNT")).orElse(0L),
                    null);
        } catch (RuntimeException fallo) {
            return MetricasDeServicio.brecha(servicio, mensajeDe(fallo));
        }
    }

    /** Una metrica que Actuator no conoce responde 404: cuenta como ausente, no como brecha. */
    private Optional<Metrica> leer(String url) {
        try {
            return Optional.ofNullable(http.get().uri(url).retrieve().body(Metrica.class));
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound ausente) {
            return Optional.empty();
        }
    }

    private static String mensajeDe(RuntimeException e) {
        Throwable causa = e;
        while (causa.getCause() != null) {
            causa = causa.getCause();
        }
        String mensaje = causa.getMessage();
        return mensaje == null || mensaje.isBlank() ? causa.getClass().getSimpleName() : mensaje;
    }

    /** Forma de la respuesta de Actuator: {@code {name, measurements:[{statistic, value}], availableTags}}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Metrica(String name, List<Medicion> measurements) {
        double valor(String estadistica) {
            if (measurements == null) {
                return 0d;
            }
            return measurements.stream()
                    .filter(m -> estadistica.equals(m.statistic()))
                    .mapToDouble(Medicion::value)
                    .findFirst()
                    .orElse(0d);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Medicion(String statistic, double value) { }
}
