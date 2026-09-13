package com.nexusbattles.plataforma.resiliencia;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Que secciones estan limitadas ahora mismo (HU-DIS-003).
 *
 * <p>Es lo que permite responder CP-03 con un dato y no con una opinion: si el
 * registro dice que solo una dependencia esta degradada, el resto de los
 * modulos esta respondiendo con normalidad.
 *
 * <p>Tambien es lo que deja escrito <b>cuando</b> empezo cada degradacion, que
 * es el dato que hace falta para el informe de resiliencia y para saber si la
 * contingencia estuvo activa durante la demostracion.
 */
public class RegistroDeDegradacion {

    private final Map<String, Degradacion> degradadas = new LinkedHashMap<>();

    /**
     * Una dependencia caida y la seccion que se queda limitada por ello.
     *
     * @param dependencia servicio que no responde
     * @param seccion parte de la interfaz que queda limitada
     * @param desde cuando se abrio el circuito
     */
    public record Degradacion(String dependencia, String seccion, Instant desde) {}

    /** Marca una dependencia como caida. Repetirlo no mueve la fecha de inicio. */
    public synchronized void degradada(String dependencia, String seccion, Instant desde) {
        degradadas.putIfAbsent(dependencia, new Degradacion(dependencia, seccion, desde));
    }

    public synchronized void recuperada(String dependencia) {
        degradadas.remove(dependencia);
    }

    public synchronized boolean estaDegradada(String dependencia) {
        return degradadas.containsKey(dependencia);
    }

    /** Las degradaciones activas, en el orden en que aparecieron. */
    public synchronized List<Degradacion> activas() {
        return List.copyOf(new ArrayList<>(degradadas.values()));
    }

    /**
     * True si el servicio esta sirviendo con normalidad.
     *
     * <p>Ojo con leerlo al reves: que haya una seccion limitada <b>no</b>
     * significa que el servicio este caido. Justo lo contrario —sigue en pie y
     * lo esta diciendo—, que es lo que pide CA-01.
     */
    public synchronized boolean sinDegradaciones() {
        return degradadas.isEmpty();
    }

    public synchronized void vaciar() {
        degradadas.clear();
    }
}
