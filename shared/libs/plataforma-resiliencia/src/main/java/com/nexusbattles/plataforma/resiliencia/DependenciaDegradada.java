package com.nexusbattles.plataforma.resiliencia;

/**
 * Una dependencia no esta disponible y la seccion que la usa queda limitada
 * (HU-DIS-003, CA-02).
 *
 * <p>No es un fallo del servicio que la lanza: es informacion sobre <b>que
 * parte</b> de la interfaz no se puede pintar ahora mismo. Por eso lleva
 * {@code seccion}: sin ese dato el frontend solo podria decir «algo fallo», y
 * el criterio pide que el jugador vea con claridad <i>que funcion</i> esta
 * limitada.
 */
public class DependenciaDegradada extends RuntimeException {

    private final String dependencia;
    private final String seccion;

    /**
     * @param dependencia nombre del servicio que no responde, para la bitacora
     * @param seccion que parte de la interfaz queda limitada, para el jugador
     * @param causa el fallo original, o nulo si el corta circuitos estaba abierto
     */
    public DependenciaDegradada(String dependencia, String seccion, Throwable causa) {
        super("La dependencia '" + dependencia + "' no responde; la seccion '" + seccion
                + "' queda limitada.", causa);
        this.dependencia = dependencia;
        this.seccion = seccion;
    }

    public String dependencia() {
        return dependencia;
    }

    public String seccion() {
        return seccion;
    }
}
