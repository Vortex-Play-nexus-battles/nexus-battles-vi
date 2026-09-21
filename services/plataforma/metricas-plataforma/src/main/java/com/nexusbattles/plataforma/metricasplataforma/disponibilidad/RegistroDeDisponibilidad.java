package com.nexusbattles.plataforma.metricasplataforma.disponibilidad;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registro de disponibilidad de los servicios del bloque (HU-DIS-001).
 *
 * <p>Guarda lo unico que hace falta para el informe: cuando empezo y cuando
 * termino cada interrupcion. No se guarda una fila por comprobacion —serian
 * miles al dia por servicio y el requisito no las pide—, solo los tramos en
 * que algo estuvo caido, mas la ultima comprobacion de cada servicio para
 * poder responder el estado en vivo (CP-01).
 *
 * <p>Las tres exclusiones de DEC-01 se aplican aqui:
 *
 * <ul>
 *   <li>solo se miden los servicios del bloque, que son los que la
 *       configuracion declara;
 *   <li>el tiempo dentro de una ventana de mantenimiento programado no cuenta
 *       como indisponible;
 *   <li>una caida marcada como atribuible a un equipo socio se registra para
 *       el informe pero no descuenta disponibilidad.
 * </ul>
 *
 * <p>Es dominio puro: sin Spring ni reloj propio, para que las reglas del
 * calculo se puedan probar sin levantar nada. Lo que si tiene es un
 * {@link AlmacenDeDisponibilidad} al que escribe cada interrupcion que abre
 * o cierra y cada ventana que programa, y del que se recarga al construirse:
 * sin eso el informe se vaciaba con cada redespliegue (#441), que es justo
 * cuando mas caidas hay que contar. En las pruebas de dominio el almacen es
 * el de memoria y no cambia nada.
 */
public class RegistroDeDisponibilidad {

    private final AlmacenDeDisponibilidad almacen;
    private final List<Interrupcion> interrupciones = new ArrayList<>();
    private final Map<String, Comprobacion> ultimaComprobacion = new LinkedHashMap<>();
    private final Map<String, Interrupcion> abiertas = new LinkedHashMap<>();
    private final List<VentanaDeMantenimiento> ventanas = new ArrayList<>();

    /** Sin persistencia: para las pruebas de dominio. */
    public RegistroDeDisponibilidad() {
        this(new AlmacenEnMemoria());
    }

    /**
     * Con persistencia: recarga lo guardado. Una interrupcion que quedo
     * abierta antes de un reinicio sigue abierta aqui, y la cerrara la
     * primera comprobacion sana de ese servicio.
     */
    public RegistroDeDisponibilidad(AlmacenDeDisponibilidad almacen) {
        this.almacen = almacen;
        for (Interrupcion guardada : almacen.interrupciones()) {
            interrupciones.add(guardada);
            if (guardada.abierta()) {
                abiertas.put(guardada.servicio(), guardada);
            }
        }
        ventanas.addAll(almacen.ventanas());
    }

    /** Declara una ventana de mantenimiento programado (DEC-01). */
    public void programarMantenimiento(VentanaDeMantenimiento ventana) {
        ventanas.add(ventana);
        almacen.guardarVentana(ventana);
    }

    /**
     * Incorpora el resultado de una comprobacion.
     *
     * <p>Abrir y cerrar interrupciones aqui, y no al consultar, es lo que hace
     * que el informe no dependa de cuando se pida.
     */
    public void registrar(Comprobacion comprobacion) {
        ultimaComprobacion.put(comprobacion.servicio(), comprobacion);

        Interrupcion abierta = abiertas.get(comprobacion.servicio());
        if (comprobacion.disponible()) {
            if (abierta != null) {
                abierta.cerrar(comprobacion.instante());
                abiertas.remove(comprobacion.servicio());
                if (abierta.id() != null) {
                    almacen.cerrar(abierta.id(), comprobacion.instante());
                }
            }
            return;
        }

        // Sigue caido: no se abre una interrupcion nueva por cada sondeo.
        if (abierta == null) {
            Interrupcion nueva =
                    new Interrupcion(comprobacion.servicio(), comprobacion.instante(), comprobacion.detalle());
            nueva.identificar(almacen.abrir(nueva));
            interrupciones.add(nueva);
            abiertas.put(comprobacion.servicio(), nueva);
        }
    }

    /** Ultima comprobacion conocida de cada servicio, para el estado en vivo (CP-01). */
    public Collection<Comprobacion> estadoActual() {
        return List.copyOf(ultimaComprobacion.values());
    }

    /** Interrupciones que tocan el periodo pedido, para el informe (CP-02). */
    public List<Interrupcion> interrupcionesEn(Instant desde, Instant hasta) {
        return interrupciones.stream()
                .filter(interrupcion -> !interrupcion.duracionEn(desde, hasta).isZero())
                .toList();
    }

    /**
     * Tiempo que el servicio estuvo indisponible dentro del periodo, ya
     * descontado el mantenimiento programado.
     */
    public Duration indisponibilidadDe(String servicio, Instant desde, Instant hasta) {
        Duration total = Duration.ZERO;
        for (Interrupcion interrupcion : interrupciones) {
            if (!interrupcion.servicio().equals(servicio)) {
                continue;
            }
            Duration bruta = interrupcion.duracionEn(desde, hasta);
            if (bruta.isZero()) {
                continue;
            }
            total = total.plus(bruta).minus(mantenimientoDentroDe(interrupcion, desde, hasta));
        }
        return total.isNegative() ? Duration.ZERO : total;
    }

    /**
     * Parte de la interrupcion que cae dentro de ventanas de mantenimiento.
     *
     * <p>Las ventanas se recorren una a una en vez de fusionarlas: el equipo
     * programa mantenimientos sueltos, no solapados, y fusionar seria resolver
     * un problema que hoy no existe.
     */
    private Duration mantenimientoDentroDe(Interrupcion interrupcion, Instant desde, Instant hasta) {
        Instant arranque = interrupcion.inicio().isBefore(desde) ? desde : interrupcion.inicio();
        Instant cierre =
                interrupcion.fin() == null || interrupcion.fin().isAfter(hasta) ? hasta : interrupcion.fin();

        Duration excluido = Duration.ZERO;
        for (VentanaDeMantenimiento ventana : ventanas) {
            Instant inicioSolape = ventana.inicio().isBefore(arranque) ? arranque : ventana.inicio();
            Instant finSolape = ventana.fin().isAfter(cierre) ? cierre : ventana.fin();
            if (finSolape.isAfter(inicioSolape)) {
                excluido = excluido.plus(Duration.between(inicioSolape, finSolape));
            }
        }
        return excluido;
    }

    /**
     * Informe del periodo para los servicios indicados.
     *
     * @param servicios servicios del bloque; se pasan desde fuera porque la
     *     exclusion de DEC-01 dice que solo estos entran en la cifra
     * @param umbral porcentaje minimo acordado, por ejemplo 99.95
     */
    public InformeDeDisponibilidad informe(
            List<String> servicios, Instant desde, Instant hasta, double umbral) {
        if (!hasta.isAfter(desde)) {
            throw new IllegalArgumentException("el fin del periodo debe ser posterior a su inicio");
        }

        Duration periodo = Duration.between(desde, hasta);
        List<InformeDeDisponibilidad.LineaDeServicio> lineas = new ArrayList<>();

        for (String servicio : servicios) {
            Duration caido = indisponibilidadDe(servicio, desde, hasta);
            Duration disponible = periodo.minus(caido);
            double porcentaje = porcentaje(disponible, periodo);
            lineas.add(new InformeDeDisponibilidad.LineaDeServicio(
                    servicio,
                    disponible,
                    caido,
                    porcentaje,
                    interrupcionesEn(desde, hasta).stream()
                            .filter(interrupcion -> interrupcion.servicio().equals(servicio))
                            .toList()));
        }

        return new InformeDeDisponibilidad(desde, hasta, periodo, umbral, List.copyOf(lineas));
    }

    private static double porcentaje(Duration disponible, Duration periodo) {
        if (periodo.isZero()) {
            return 100d;
        }
        double bruto = (disponible.toMillis() * 100d) / periodo.toMillis();
        // Dos decimales: el informe habla de «99,92 %», no de 99,9166666.
        return Math.round(bruto * 100d) / 100d;
    }
}
