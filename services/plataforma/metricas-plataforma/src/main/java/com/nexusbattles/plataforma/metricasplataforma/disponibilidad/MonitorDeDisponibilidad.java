package com.nexusbattles.plataforma.metricasplataforma.disponibilidad;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Comprueba periodicamente la salud de los servicios del bloque y lleva el
 * registro del que sale el informe (HU-DIS-001).
 *
 * <p>Los tres criterios de la historia pasan por aqui:
 *
 * <ul>
 *   <li>CA-01: comprueba <b>todos</b> los servicios declarados, no una muestra;
 *   <li>CA-02: cada resultado va al registro, que acumula el periodo;
 *   <li>CA-03: al cerrar un periodo, si la disponibilidad del bloque queda bajo
 *       el umbral, se dispara la alerta.
 * </ul>
 *
 * <p>El reloj se inyecta para poder probar periodos completos sin esperarlos.
 */
public class MonitorDeDisponibilidad {

    private final ConfiguracionDeDisponibilidad configuracion;
    private final SondaDeSalud sonda;
    private final RegistroDeDisponibilidad registro;
    private final Alertas alertas;
    private final Clock reloj;

    public MonitorDeDisponibilidad(
            ConfiguracionDeDisponibilidad configuracion,
            SondaDeSalud sonda,
            RegistroDeDisponibilidad registro,
            Alertas alertas,
            Clock reloj) {
        this.configuracion = configuracion;
        this.sonda = sonda;
        this.registro = registro;
        this.alertas = alertas;
        this.reloj = reloj;
    }

    /**
     * Una ronda de comprobaciones sobre todos los servicios del bloque.
     *
     * <p>Se alerta solo en el flanco: cuando un servicio que estaba sano deja
     * de responder. Alertar en cada ronda mientras siga caido convertiria la
     * alerta en ruido y nadie la miraria.
     *
     * @return lo comprobado en esta ronda
     */
    public List<Comprobacion> comprobarTodos() {
        Instant ahora = reloj.instant();
        List<Comprobacion> resultados = new java.util.ArrayList<>();

        for (Map.Entry<String, String> servicio : configuracion.servicios().entrySet()) {
            boolean estabaSano = estabaSano(servicio.getKey());
            Comprobacion comprobacion = sonda.comprobar(servicio.getKey(), servicio.getValue(), ahora);
            registro.registrar(comprobacion);
            resultados.add(comprobacion);

            if (!comprobacion.disponible() && estabaSano) {
                alertas.servicioCaido(comprobacion.servicio(), comprobacion.detalle());
            }
        }
        return List.copyOf(resultados);
    }

    /**
     * Informe del periodo. Si la disponibilidad del bloque queda bajo el
     * umbral, dispara la alerta de CP-03 antes de devolverlo.
     */
    public InformeDeDisponibilidad informe(Instant desde, Instant hasta) {
        InformeDeDisponibilidad informe =
                registro.informe(configuracion.nombres(), desde, hasta, configuracion.umbralPorcentaje());

        if (!informe.cumpleElUmbral()) {
            alertas.disponibilidadBajoUmbral(
                    informe.porcentajeDelBloque(), configuracion.umbralPorcentaje());
        }
        return informe;
    }

    /** Informe de los ultimos treinta dias: el periodo que fija DEC-01 («mensual»). */
    public InformeDeDisponibilidad informeMensual() {
        Instant ahora = reloj.instant();
        return informe(ahora.minus(Duration.ofDays(30)), ahora);
    }

    /** Estado en vivo de cada servicio del bloque (CP-01). */
    public List<Comprobacion> estadoActual() {
        return List.copyOf(registro.estadoActual());
    }

    private boolean estabaSano(String servicio) {
        return registro.estadoActual().stream()
                .filter(comprobacion -> comprobacion.servicio().equals(servicio))
                .findFirst()
                .map(Comprobacion::disponible)
                // Primera ronda: sin historial se considera sano, para no
                // alertar por el simple arranque del monitor.
                .orElse(true);
    }
}
