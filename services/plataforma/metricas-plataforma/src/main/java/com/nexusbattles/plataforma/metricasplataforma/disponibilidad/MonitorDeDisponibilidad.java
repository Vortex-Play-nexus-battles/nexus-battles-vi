package com.nexusbattles.plataforma.metricasplataforma.disponibilidad;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
     * <p>Los servicios se recorren en el orden en que estan declarados en la
     * configuracion, y ninguno puede impedir que se midan los demas: una sonda
     * que falle cuenta como servicio caido, no como ronda perdida.
     *
     * @return lo comprobado en esta ronda
     */
    public List<Comprobacion> comprobarTodos() {
        Instant ahora = reloj.instant();
        List<Comprobacion> resultados = new ArrayList<>();

        for (Map.Entry<String, String> servicio : configuracion.servicios().entrySet()) {
            // Se consulta ANTES de registrar: despues, la comprobacion nueva
            // ya seria el "estado anterior" y la transicion se perderia.
            boolean constabaSano = constabaSano(servicio.getKey());
            Comprobacion comprobacion = comprobar(servicio.getKey(), servicio.getValue(), ahora);
            registro.registrar(comprobacion);
            resultados.add(comprobacion);

            if (!comprobacion.disponible() && constabaSano) {
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

    /**
     * Comprueba un servicio sin dejar que su fallo tumbe la ronda.
     *
     * <p>El adaptador HTTP ya traduce los fallos de red a «caido», pero la
     * garantia no puede depender de que toda implementacion de
     * {@link SondaDeSalud} se acuerde de hacerlo: si una lanzara, el
     * {@code for} se cortaria y los servicios siguientes se quedarian sin
     * medir esa ronda, que es justo lo contrario de CA-01.
     */
    private Comprobacion comprobar(String servicio, String url, Instant instante) {
        try {
            return sonda.comprobar(servicio, url, instante);
        } catch (RuntimeException e) {
            String motivo = e.getMessage() == null || e.getMessage().isBlank()
                    ? e.getClass().getSimpleName()
                    : e.getMessage();
            return Comprobacion.caido(servicio, instante, "la sonda fallo: " + motivo);
        }
    }

    /**
     * True solo si consta una comprobacion anterior y decia que el servicio
     * estaba sano.
     *
     * <p>Sin historial devuelve false, y esa es la parte que importa: la
     * alerta es de <b>transicion</b> sano -> caido. Si al arrancar el monitor
     * un servicio ya estaba caido, no hay transicion que anunciar —lleva caido
     * desde antes— y tratar «sin historial» como «estaba sano» inventaria una
     * caida que no ocurrio en esta ronda. Ese estado inicial si queda
     * registrado y cuenta para el informe; lo que no hace es disparar alerta.
     */
    private boolean constabaSano(String servicio) {
        return registro.estadoActual().stream()
                .filter(comprobacion -> comprobacion.servicio().equals(servicio))
                .findFirst()
                .map(Comprobacion::disponible)
                .orElse(false);
    }
}
