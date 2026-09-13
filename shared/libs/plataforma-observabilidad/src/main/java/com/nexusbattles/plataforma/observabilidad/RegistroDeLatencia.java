package com.nexusbattles.plataforma.observabilidad;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Guarda las muestras de latencia y calcula los percentiles (HU-REN-001).
 *
 * <p>La ventana es acotada: se conservan las ultimas {@code capacidad}
 * muestras y las mas viejas se descartan. Un servicio con trafico real
 * generaria millones de muestras al dia y guardarlas todas en memoria acabaria
 * tumbando el propio servicio que se pretende medir — que es justo lo que la
 * historia advierte al pedir «recolectar las marcas de tiempo sin afectar el
 * rendimiento de la aplicacion».
 *
 * <p>Es thread-safe por sincronizacion simple: un servidor web atiende
 * peticiones en paralelo y el filtro escribe desde todas ellas. La seccion
 * critica es un {@code add} sobre una cola, no una operacion costosa.
 */
public class RegistroDeLatencia {

    /** Suficiente para un percentil estable sin comprometer memoria. */
    public static final int CAPACIDAD_POR_OMISION = 10_000;

    private final String servicio;
    private final int capacidad;
    private final Deque<MuestraDeLatencia> muestras = new ArrayDeque<>();

    public RegistroDeLatencia(String servicio) {
        this(servicio, CAPACIDAD_POR_OMISION);
    }

    public RegistroDeLatencia(String servicio, int capacidad) {
        if (capacidad <= 0) {
            throw new IllegalArgumentException("la capacidad debe ser positiva, y llego " + capacidad);
        }
        this.servicio = servicio;
        this.capacidad = capacidad;
    }

    public String servicio() {
        return servicio;
    }

    /** Registra una peticion medida, descartando la mas vieja si no cabe. */
    public synchronized void registrar(MuestraDeLatencia muestra) {
        if (muestras.size() >= capacidad) {
            muestras.removeFirst();
        }
        muestras.addLast(muestra);
    }

    public synchronized long cuantasMuestras() {
        return muestras.size();
    }

    /** Copia de las muestras, para exportarlas como evidencia (CA-02). */
    public synchronized List<MuestraDeLatencia> muestras() {
        return List.copyOf(muestras);
    }

    public synchronized void vaciar() {
        muestras.clear();
    }

    /**
     * Informe del estado actual contra el objetivo dado.
     *
     * @param objetivo objetivo y percentil vigentes; el percentil lo decide el
     *     Product Owner (CA-03), por eso entra como parametro y no es constante
     * @param cuantasOperaciones cuantas de las operaciones mas lentas incluir
     */
    public synchronized InformeDeLatencia informe(ObjetivoDeLatencia objetivo, int cuantasOperaciones) {
        if (muestras.isEmpty()) {
            return new InformeDeLatencia(0, objetivo, 0, 0, List.of());
        }

        List<Long> duraciones = muestras.stream().map(MuestraDeLatencia::duracionMs).sorted().toList();
        long maximo = duraciones.get(duraciones.size() - 1);

        return new InformeDeLatencia(
                muestras.size(),
                objetivo,
                percentil(duraciones, objetivo.percentil()),
                maximo,
                operacionesMasLentas(objetivo, cuantasOperaciones));
    }

    /**
     * Las mismas muestras, separadas en lectura y escritura (HU-REN-002).
     *
     * <p>Solo aparecen los tipos que de verdad tienen muestras: un panel con
     * una fila «escritura: 0 muestras, 0 ms» invita a leer un cero como «va
     * rapidisimo» cuando lo que pasa es que nadie ha escrito nada.
     *
     * <p>Se devuelven en el orden del enum —lectura, escritura, otra— para que
     * el informe no cambie de orden entre dos consultas.
     */
    public synchronized List<InformeDeLatencia.ResumenPorTipo> resumenPorTipo(ObjetivoDeLatencia objetivo) {
        Map<TipoDeOperacion, List<Long>> porTipo = new EnumMap<>(TipoDeOperacion.class);
        for (MuestraDeLatencia muestra : muestras) {
            porTipo.computeIfAbsent(muestra.tipo(), t -> new ArrayList<>()).add(muestra.duracionMs());
        }

        List<InformeDeLatencia.ResumenPorTipo> resumen = new ArrayList<>();
        for (TipoDeOperacion tipo : TipoDeOperacion.values()) {
            List<Long> duraciones = porTipo.get(tipo);
            if (duraciones == null || duraciones.isEmpty()) {
                continue;
            }
            List<Long> ordenadas = duraciones.stream().sorted().toList();
            resumen.add(new InformeDeLatencia.ResumenPorTipo(
                    tipo,
                    ordenadas.size(),
                    percentil(ordenadas, objetivo.percentil()),
                    ordenadas.get(ordenadas.size() - 1)));
        }
        return List.copyOf(resumen);
    }

    /**
     * Percentil por rango mas cercano sobre una lista ya ordenada.
     *
     * <p>Se elige este metodo y no una interpolacion porque devuelve siempre un
     * valor realmente medido: en un informe de rendimiento decir «el p95 fue de
     * 412 ms» y que esos 412 ms correspondan a una peticion que de verdad
     * ocurrio es mas defendible ante el cliente que un valor interpolado que
     * nunca se observo.
     */
    static long percentil(List<Long> ordenadas, double percentil) {
        if (ordenadas.isEmpty()) {
            return 0;
        }
        int rango = (int) Math.ceil((percentil / 100d) * ordenadas.size());
        int indice = Math.min(Math.max(rango - 1, 0), ordenadas.size() - 1);
        return ordenadas.get(indice);
    }

    private List<InformeDeLatencia.Operacion> operacionesMasLentas(
            ObjetivoDeLatencia objetivo, int cuantas) {

        Map<String, List<Long>> porOperacion = new LinkedHashMap<>();
        Map<String, String[]> etiquetas = new LinkedHashMap<>();

        for (MuestraDeLatencia muestra : muestras) {
            String clave = muestra.metodo() + " " + muestra.ruta();
            porOperacion.computeIfAbsent(clave, k -> new ArrayList<>()).add(muestra.duracionMs());
            etiquetas.putIfAbsent(clave, new String[] {muestra.metodo(), muestra.ruta()});
        }

        List<InformeDeLatencia.Operacion> operaciones = new ArrayList<>();
        porOperacion.forEach((clave, duraciones) -> {
            List<Long> ordenadas = duraciones.stream().sorted().toList();
            String[] etiqueta = etiquetas.get(clave);
            operaciones.add(new InformeDeLatencia.Operacion(
                    etiqueta[0], etiqueta[1], ordenadas.size(), percentil(ordenadas, objetivo.percentil())));
        });

        operaciones.sort(Comparator.comparingLong(InformeDeLatencia.Operacion::percentilMs).reversed());
        return operaciones.size() <= cuantas ? List.copyOf(operaciones) : List.copyOf(operaciones.subList(0, cuantas));
    }
}
