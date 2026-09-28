package nexus.misiones.dominio;

import java.util.List;

/**
 * Lo que una ejecucion gano, calculado UNA vez al terminar y guardado con ella:
 * los reintentos de la entrega reparten esto, no lo vuelven a sortear.
 *
 * @param productos   objetos del catalogo oficial, que llegan al inventario
 * @param epicas      epicas de Master obtenidas; las que no tienen producto
 *                    quedan en la coleccion del jugador (7.8.12)
 * @param experiencia puntos que el inventario suma al heroe al liberarlo
 * @param sinEntregar lo que el documento da y no se puede entregar, con motivo
 * @param objetivos   cada objetivo con su estado (7.8.8)
 * @param primeraVez  si fue la primera vez que el jugador la completo
 */
public record RecompensasDeEjecucion(
        int creditos,
        List<ObjetoGanado> productos,
        List<EpicaGanada> epicas,
        double experiencia,
        List<SinEntregar> sinEntregar,
        List<ObjetivoEvaluado> objetivos,
        boolean primeraVez) {

    public RecompensasDeEjecucion {
        if (creditos < 0 || experiencia < 0) {
            throw new IllegalArgumentException("Una recompensa no quita nada.");
        }
        productos = List.copyOf(productos);
        epicas = List.copyOf(epicas);
        sinEntregar = List.copyOf(sinEntregar);
        objetivos = List.copyOf(objetivos);
    }

    public boolean tieneEpicaEntregable() {
        return epicas.stream().anyMatch(EpicaGanada::entregable);
    }

    public record ObjetoGanado(String nombre, String productoId, int cantidad) {
    }

    /** {@code productoId} nulo: la epica no esta en el catalogo, solo en la coleccion. */
    public record EpicaGanada(String nombre, String master, String productoId) {

        public boolean entregable() {
            return productoId != null && !productoId.isBlank();
        }
    }

    public record SinEntregar(String nombre, String motivo) {
    }

    public record ObjetivoEvaluado(String texto, boolean cumplido, String bonificacion) {
    }
}
