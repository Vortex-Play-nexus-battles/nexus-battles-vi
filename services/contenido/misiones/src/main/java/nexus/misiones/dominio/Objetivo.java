package nexus.misiones.dominio;

import java.util.Objects;

/**
 * Un objetivo de la mision (seccion 7.8.3): su texto es el del documento y su
 * {@link TipoDeObjetivo} dice como se comprueba contra la simulacion.
 *
 * @param texto     tal como lo escribe el documento
 * @param principal si es de los principales (sin ellos la mision no se completa)
 * @param tipo      como se evalua
 * @param valor     porcentaje de VIDA_MINIMA o unidades de OBTENER_BOTIN
 * @param botin     nombre del botin de OBTENER_BOTIN
 */
public record Objetivo(String texto, boolean principal, TipoDeObjetivo tipo, Integer valor, String botin) {

    public Objetivo {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("Un objetivo necesita su texto.");
        }
        Objects.requireNonNull(tipo, "Un objetivo necesita saber como se comprueba.");
        if ((tipo == TipoDeObjetivo.VIDA_MINIMA || tipo == TipoDeObjetivo.OBTENER_BOTIN)
                && (valor == null || valor <= 0)) {
            throw new IllegalArgumentException("El objetivo «" + texto + "» necesita un valor positivo.");
        }
        if (tipo == TipoDeObjetivo.OBTENER_BOTIN && (botin == null || botin.isBlank())) {
            throw new IllegalArgumentException("El objetivo «" + texto + "» necesita el nombre del botin.");
        }
    }
}
