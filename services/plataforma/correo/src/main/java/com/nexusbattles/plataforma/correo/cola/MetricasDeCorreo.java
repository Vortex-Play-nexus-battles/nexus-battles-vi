package com.nexusbattles.plataforma.correo.cola;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Metricas de la cola (regla 3), en Prometheus como
 * {@code correo_envios_total{estado,plantilla}} y {@code correo_pendientes}.
 *
 * <p>El contador sube una vez por cada estado que alcanza un envio: al
 * aceptarlo (PENDIENTE, u OMITIDO si no habia que enviarlo) y tras cada
 * intento. Asi {@code estado="ERROR_REINTENTABLE"} cuenta fallos del
 * proveedor y {@code estado="ENVIADO"} entregas, sin consultar la base.
 *
 * <p>Los pendientes si se leen de la base, pero solo cuando alguien mira la
 * metrica: consultarlos en cada ronda del trabajador seria una consulta cada
 * dos segundos que casi nadie lee. Si la base no responde, el valor es NaN
 * -"no se sabe"- y no un cero que diria que la cola esta vacia.
 */
@Component
public class MetricasDeCorreo {

    static final String ENVIOS = "correo.envios";
    static final String PENDIENTES = "correo.pendientes";

    private final MeterRegistry registro;

    public MetricasDeCorreo(MeterRegistry registro, RepositorioDeEnvios repositorio) {
        this.registro = registro;
        Gauge.builder(PENDIENTES, repositorio, MetricasDeCorreo::pendientes)
                .description("Correos en cola o esperando reintento")
                .register(registro);
    }

    /** Un envio acaba de pasar a {@code estado}. */
    public void registrar(EstadoDeEnvio estado, String plantilla) {
        Counter.builder(ENVIOS)
                .description("Correos que alcanzan cada estado de la cola")
                .tag("estado", estado.name())
                .tag("plantilla", plantilla == null ? "desconocida" : plantilla)
                .register(registro)
                .increment();
    }

    static double pendientes(RepositorioDeEnvios repositorio) {
        try {
            return repositorio.contarPendientes();
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }
}
