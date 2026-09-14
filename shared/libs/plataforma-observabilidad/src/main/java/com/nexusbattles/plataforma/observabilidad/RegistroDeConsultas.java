package com.nexusbattles.plataforma.observabilidad;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Guarda las consultas medidas y separa las lentas (HU-REN-003).
 *
 * <p>Dos ventanas acotadas y no una: la general sirve para el percentil, y la
 * de lentas para CA-03 —«el sistema la marca en el registro de consultas
 * lentas»—. Si solo hubiera una, una racha de consultas rapidas expulsaria de
 * la ventana justo las lentas, que son las unicas que hay que optimizar.
 *
 * <p>Thread-safe por sincronizacion simple, igual que {@link RegistroDeLatencia}:
 * un servidor ejecuta consultas desde muchos hilos y la seccion critica es un
 * {@code add} sobre una cola.
 */
public class RegistroDeConsultas {

    public static final int CAPACIDAD_POR_OMISION = 10_000;
    public static final int CAPACIDAD_LENTAS_POR_OMISION = 200;

    private final String servicio;
    private final int capacidad;
    private final int capacidadLentas;
    private final long umbralLentaMs;

    private final Deque<MuestraDeConsulta> muestras = new ArrayDeque<>();
    private final Deque<MuestraDeConsulta> lentas = new ArrayDeque<>();
    private long totalLentas;

    public RegistroDeConsultas(String servicio, long umbralLentaMs) {
        this(servicio, umbralLentaMs, CAPACIDAD_POR_OMISION, CAPACIDAD_LENTAS_POR_OMISION);
    }

    public RegistroDeConsultas(String servicio, long umbralLentaMs, int capacidad, int capacidadLentas) {
        if (capacidad <= 0 || capacidadLentas <= 0) {
            throw new IllegalArgumentException(
                    "las capacidades deben ser positivas, y llegaron " + capacidad + " y " + capacidadLentas);
        }
        if (umbralLentaMs <= 0) {
            throw new IllegalArgumentException(
                    "el umbral de consulta lenta debe ser positivo, y llego " + umbralLentaMs);
        }
        this.servicio = servicio;
        this.capacidad = capacidad;
        this.capacidadLentas = capacidadLentas;
        this.umbralLentaMs = umbralLentaMs;
    }

    public String servicio() {
        return servicio;
    }

    public long umbralLentaMs() {
        return umbralLentaMs;
    }

    public synchronized void registrar(MuestraDeConsulta muestra) {
        if (muestras.size() >= capacidad) {
            muestras.removeFirst();
        }
        muestras.addLast(muestra);

        if (muestra.lenta(umbralLentaMs)) {
            // CA-03: la marca es separada y sobrevive aunque la ventana general
            // se llene de consultas rapidas.
            totalLentas++;
            if (lentas.size() >= capacidadLentas) {
                lentas.removeFirst();
            }
            lentas.addLast(muestra);
        }
    }

    public synchronized long cuantasMuestras() {
        return muestras.size();
    }

    /** Cuantas consultas lentas se han visto en total, no solo las retenidas. */
    public synchronized long totalLentas() {
        return totalLentas;
    }

    public synchronized List<MuestraDeConsulta> muestras() {
        return List.copyOf(muestras);
    }

    /** Las consultas lentas retenidas, de la mas reciente a la mas vieja. */
    public synchronized List<MuestraDeConsulta> consultasLentas() {
        List<MuestraDeConsulta> recientesPrimero = new ArrayList<>(lentas);
        java.util.Collections.reverse(recientesPrimero);
        return List.copyOf(recientesPrimero);
    }

    public synchronized void vaciar() {
        muestras.clear();
        lentas.clear();
        totalLentas = 0;
    }

    /**
     * Informe del estado actual contra el objetivo dado.
     *
     * @param objetivo objetivo y percentil vigentes; el percentil lo decide el
     *     Product Owner, por eso entra como parametro y no es constante
     * @param cuantasSentencias cuantas de las sentencias mas lentas incluir
     */
    public synchronized InformeDeConsultas informe(ObjetivoDeLatencia objetivo, int cuantasSentencias) {
        if (muestras.isEmpty()) {
            return new InformeDeConsultas(
                    servicio, 0, objetivo, 0, 0, umbralLentaMs, totalLentas, List.of());
        }

        List<Long> duraciones = muestras.stream().map(MuestraDeConsulta::duracionMs).sorted().toList();

        return new InformeDeConsultas(
                servicio,
                muestras.size(),
                objetivo,
                RegistroDeLatencia.percentil(duraciones, objetivo.percentil()),
                duraciones.get(duraciones.size() - 1),
                umbralLentaMs,
                totalLentas,
                sentenciasMasLentas(objetivo, cuantasSentencias));
    }

    private List<InformeDeConsultas.Sentencia> sentenciasMasLentas(ObjetivoDeLatencia objetivo, int cuantas) {
        Map<String, List<Long>> porSentencia = new LinkedHashMap<>();
        for (MuestraDeConsulta muestra : muestras) {
            porSentencia.computeIfAbsent(muestra.sentencia(), k -> new ArrayList<>()).add(muestra.duracionMs());
        }

        List<InformeDeConsultas.Sentencia> sentencias = new ArrayList<>();
        porSentencia.forEach((sentencia, duraciones) -> {
            List<Long> ordenadas = duraciones.stream().sorted().toList();
            sentencias.add(new InformeDeConsultas.Sentencia(
                    sentencia,
                    ordenadas.size(),
                    RegistroDeLatencia.percentil(ordenadas, objetivo.percentil()),
                    ordenadas.get(ordenadas.size() - 1)));
        });

        sentencias.sort(Comparator.comparingLong(InformeDeConsultas.Sentencia::percentilMs).reversed());
        return sentencias.size() <= cuantas
                ? List.copyOf(sentencias)
                : List.copyOf(sentencias.subList(0, cuantas));
    }
}
