package nexus.inventario.aplicacion;

/**
 * Hay experiencia que sumar y el servicio de heroes no dijo a que nivel lleva:
 * 503 «Progresion no disponible» y no se aplica nada (1.6.0, B9). El heroe sigue
 * bloqueado hasta el reintento de misiones.
 */
public class ProgresionNoDisponibleException extends RuntimeException {

    public ProgresionNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
