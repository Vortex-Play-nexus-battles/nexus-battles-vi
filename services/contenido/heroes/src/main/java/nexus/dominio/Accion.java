package nexus.dominio;

/**
 * Accion especial de un heroe (Tabla 7, Proyecto Integrador II seccion 6.1.2, p. 28).
 * costoPuntos == null representa "Todos los puntos de poder" (Reanimacion del Medico).
 */
public record Accion(String nombre, Integer costoPuntos, String efecto) {

    public boolean cuestaTodoElPoder() {
        return costoPuntos == null;
    }
}
