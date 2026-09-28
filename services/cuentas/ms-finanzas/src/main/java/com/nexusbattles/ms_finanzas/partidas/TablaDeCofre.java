package com.nexusbattles.ms_finanzas.partidas;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;

/**
 * Qué puede traer un cofre y con qué peso — cofres.yaml 1.1.0 (B7).
 *
 * <p>§7.6 dice que el cofre trae «una recompensa aleatoria» y nada más: ni qué
 * productos ni con qué probabilidad. Por eso la tabla es CONFIGURABLE y su
 * versión viaja con cada cofre ({@code tablaVersion}). La que rige mientras el
 * Product Owner no decida es PROVISIONAL DE DESARROLLO y lo dice en la propia
 * versión ({@code PROVISIONAL-DEV-...}, decisión D-B7-18).
 *
 * <p><b>El sorteo es reproducible.</b> {@link #sortear(long)} es una función
 * pura de la semilla: con la semilla que se guarda en el cofre y la misma
 * tabla se obtiene el mismo premio. La aleatoriedad está en la semilla, que
 * sale de {@code SecureRandom}.
 *
 * @param version  etiqueta de la tabla, la que se guarda con el cofre
 * @param entradas productos del catálogo con su peso relativo
 */
public record TablaDeCofre(String version, List<Entrada> entradas) {

    /** Prefijo que marca una tabla que no es la definitiva del Product Owner. */
    public static final String PREFIJO_PROVISIONAL = "PROVISIONAL-DEV";

    public TablaDeCofre {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("La tabla del cofre necesita versión: viaja con cada cofre.");
        }
        entradas = List.copyOf(Objects.requireNonNull(entradas, "La tabla del cofre necesita entradas."));
        if (entradas.isEmpty()) {
            throw new IllegalArgumentException("Un cofre sin nada que sortear no es un cofre.");
        }
    }

    /**
     * Un producto posible con su peso.
     *
     * @param productoId identificador del producto en el catálogo
     * @param peso       peso relativo, mayor que cero
     */
    public record Entrada(String productoId, int peso) {

        public Entrada {
            if (productoId == null || productoId.isBlank()) {
                throw new IllegalArgumentException("Cada premio del cofre es un producto del catálogo.");
            }
            if (peso < 1) {
                throw new IllegalArgumentException("El peso de " + productoId + " tiene que ser mayor que cero.");
            }
        }
    }

    /**
     * Lee la tabla de su forma de texto: {@code productoId=peso}, separados por
     * {@code ;}, comas o saltos de línea. Las líneas que empiezan por {@code #}
     * son comentarios (la tabla provisional lleva el nombre de cada producto).
     */
    public static TablaDeCofre desde(String version, String texto) {
        Objects.requireNonNull(texto, "Hace falta el texto de la tabla.");
        List<Entrada> entradas = new ArrayList<>();
        for (String linea : texto.split("\\R")) {
            String sinComentario = linea.contains("#") ? linea.substring(0, linea.indexOf('#')) : linea;
            for (String trozo : sinComentario.split("[;,]")) {
                String entrada = trozo.trim();
                if (entrada.isEmpty()) {
                    continue;
                }
                int igual = entrada.indexOf('=');
                if (igual < 1) {
                    throw new IllegalArgumentException("Entrada de la tabla del cofre sin peso: «" + entrada + "».");
                }
                try {
                    entradas.add(new Entrada(entrada.substring(0, igual).trim(),
                            Integer.parseInt(entrada.substring(igual + 1).trim())));
                } catch (NumberFormatException pesoIlegible) {
                    throw new IllegalArgumentException("El peso de «" + entrada + "» no es un entero.",
                            pesoIlegible);
                }
            }
        }
        return new TablaDeCofre(version, entradas);
    }

    /** Si es la tabla provisional de desarrollo y no la del Product Owner. */
    public boolean esProvisional() {
        return version.startsWith(PREFIJO_PROVISIONAL);
    }

    /** Suma de los pesos: el tamaño de la «rueda» del sorteo. */
    public int pesoTotal() {
        return entradas.stream().mapToInt(Entrada::peso).sum();
    }

    /**
     * El premio que corresponde a esta semilla: una tirada uniforme sobre la
     * suma de pesos, y la entrada en cuyo tramo cae. Función pura: misma
     * semilla, mismo premio.
     */
    public Entrada sortear(long semilla) {
        int tirada = new SplittableRandom(semilla).nextInt(pesoTotal());
        int acumulado = 0;
        for (Entrada entrada : entradas) {
            acumulado += entrada.peso();
            if (tirada < acumulado) {
                return entrada;
            }
        }
        // Inalcanzable: la tirada es menor que la suma de los pesos.
        throw new IllegalStateException("La tirada " + tirada + " se salió de la tabla " + version);
    }
}
