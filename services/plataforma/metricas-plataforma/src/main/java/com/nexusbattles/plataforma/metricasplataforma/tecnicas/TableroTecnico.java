package com.nexusbattles.plataforma.metricasplataforma.tecnicas;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Metricas tecnicas del bloque con las alertas sobre los umbrales que YA
 * estan escritos (HU-MET-004, CA-01): 75 % de procesador (Charter, disparo
 * del autoescalado), 500 ms de latencia (RNF-REN-001, {@code objetivoMs}) y
 * el umbral de disponibilidad de DEC-01. No se inventa ningun umbral mas:
 * la tasa de error se publica sin alerta porque ningun documento la fija.
 */
public record TableroTecnico(Instant generadoEn, List<MetricasDeServicio> servicios, List<Alerta> alertas,
                             List<String> brechas, Umbrales umbrales) {

    /** Charter: «autoescalado al superar 75 % de uso de procesador». Inalterable. */
    public static final double UMBRAL_CPU = 0.75d;

    public record Umbrales(double cpu, long latenciaMs, double disponibilidadPorcentaje) { }

    public record Alerta(String servicio, String metrica, String detalle) { }

    public record Disponibilidad(String servicio, boolean disponible, Double porcentajeDelMes) { }

    public static TableroTecnico de(Instant generadoEn, List<MetricasDeServicio> recolectadas,
                                    List<Disponibilidad> disponibilidad, long latenciaObjetivoMs,
                                    double umbralDisponibilidad) {
        List<Alerta> alertas = new ArrayList<>();
        List<String> brechas = new ArrayList<>();
        for (MetricasDeServicio m : recolectadas) {
            if (!m.recolectado()) {
                brechas.add(m.servicio() + ": " + m.brecha());
                continue;
            }
            if (m.cpu() != null && m.cpu() > UMBRAL_CPU) {
                alertas.add(new Alerta(m.servicio(), "cpu", String.format("uso de procesador %.0f %% por encima del %.0f %%",
                        m.cpu() * 100, UMBRAL_CPU * 100)));
            }
            if (m.maximoMs() != null && m.maximoMs() > latenciaObjetivoMs) {
                alertas.add(new Alerta(m.servicio(), "latencia", String.format("latencia maxima %.0f ms por encima de %d ms",
                        m.maximoMs(), latenciaObjetivoMs)));
            }
        }
        for (Disponibilidad d : disponibilidad) {
            if (!d.disponible()) {
                alertas.add(new Alerta(d.servicio(), "disponibilidad", "el servicio no responde UP"));
            } else if (d.porcentajeDelMes() != null && d.porcentajeDelMes() < umbralDisponibilidad) {
                alertas.add(new Alerta(d.servicio(), "disponibilidad", String.format("%.2f %% en el mes, por debajo del %.2f %%",
                        d.porcentajeDelMes(), umbralDisponibilidad)));
            }
        }
        return new TableroTecnico(generadoEn, List.copyOf(recolectadas), List.copyOf(alertas), List.copyOf(brechas),
                new Umbrales(UMBRAL_CPU, latenciaObjetivoMs, umbralDisponibilidad));
    }

    public boolean conBrechas() {
        return !brechas.isEmpty();
    }

    /** El mismo tablero redactado, para pegarlo en el informe de pruebas (CA-02). */
    public String comoTexto() {
        StringBuilder sb = new StringBuilder();
        sb.append("Metricas tecnicas de la plataforma - ").append(generadoEn).append('\n');
        sb.append(String.format("Umbrales: cpu %.0f %%, latencia %d ms, disponibilidad %.2f %%%n",
                umbrales.cpu() * 100, umbrales.latenciaMs(), umbrales.disponibilidadPorcentaje()));
        for (MetricasDeServicio m : servicios) {
            if (!m.recolectado()) {
                sb.append(String.format("- %s: BRECHA DE OBSERVABILIDAD (%s)%n", m.servicio(), m.brecha()));
                continue;
            }
            sb.append(String.format("- %s: cpu %s, memoria %s MB, peticiones %d, media %s ms, maxima %s ms, errores 5xx %d%n",
                    m.servicio(), porcentaje(m.cpu()), entero(m.memoriaMb()), m.peticiones(), entero(m.promedioMs()),
                    entero(m.maximoMs()), m.errores5xx()));
        }
        sb.append(alertas.isEmpty() ? "Sin alertas.\n" : "Alertas:\n");
        for (Alerta a : alertas) {
            sb.append("  * ").append(a.servicio()).append(" [").append(a.metrica()).append("]: ").append(a.detalle()).append('\n');
        }
        return sb.toString();
    }

    private static String porcentaje(Double fraccion) {
        return fraccion == null ? "n/d" : String.format("%.0f %%", fraccion * 100);
    }

    private static String entero(Double valor) {
        return valor == null ? "n/d" : String.format("%.0f", valor);
    }
}
