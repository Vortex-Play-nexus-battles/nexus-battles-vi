package nexus.misiones.dominio;

/**
 * Dos escrituras sobre la misma ejecucion a la vez (el jugador cancela
 * mientras el trabajo en segundo plano la termina), o una segunda ejecucion en
 * curso de la misma mision para el mismo jugador. La que llega tarde no se
 * guarda.
 */
public class EjecucionModificadaConcurrentemente extends RuntimeException {

    public EjecucionModificadaConcurrentemente(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
