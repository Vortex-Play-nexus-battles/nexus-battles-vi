package com.nexusbattles.plataforma.resiliencia;

/**
 * Los tres estados de un corta circuitos (HU-DIS-003, SCRUM-1146).
 *
 * <pre>
 *   CERRADO ──(N fallos seguidos)──> ABIERTO
 *   ABIERTO ──(pasa la espera)─────> SEMIABIERTO
 *   SEMIABIERTO ──(prueba bien)────> CERRADO
 *   SEMIABIERTO ──(prueba mal)─────> ABIERTO
 * </pre>
 */
public enum EstadoDelCorta {

    /** Todo normal: las llamadas pasan al servicio de verdad. */
    CERRADO,

    /**
     * El servicio esta caido: las llamadas ni se intentan.
     *
     * <p>Esta es la parte que evita la caida en cascada. Seguir llamando a un
     * servicio que no responde consume un hilo y un tiempo de espera por cada
     * intento; con suficiente trafico, el que se queda sin hilos es <b>el que
     * llama</b>, y entonces la caida de uno se lleva por delante a los demas.
     */
    ABIERTO,

    /** Se deja pasar una llamada de prueba para ver si el servicio volvio. */
    SEMIABIERTO
}
